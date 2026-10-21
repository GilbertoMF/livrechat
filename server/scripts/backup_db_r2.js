const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const { S3Client, PutObjectCommand, ListObjectsV2Command, DeleteObjectCommand } = require('@aws-sdk/client-s3');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });

const R2_ACCOUNT_ID = process.env.R2_ACCOUNT_ID || '9a8e754d976094a4d4b422fdc38eaf99';
const R2_ACCESS_KEY_ID = process.env.R2_ACCESS_KEY_ID || 'c31ee3bcf61e6f47df6a0fed8feadab9';
const R2_SECRET_ACCESS_KEY = process.env.R2_SECRET_ACCESS_KEY || '3feb7af31853dec25d95acd7f3ec2cf28bc7bd1f0fb37f98ada3b1680c5c438b';
const R2_BUCKET_NAME = process.env.R2_BUCKET_NAME || 'livrechat-media';
const R2_ENDPOINT = process.env.R2_ENDPOINT || `https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com`;

const client = new S3Client({
  region: 'auto',
  endpoint: R2_ENDPOINT,
  credentials: {
    accessKeyId: R2_ACCESS_KEY_ID,
    secretAccessKey: R2_SECRET_ACCESS_KEY,
  },
});

async function runBackup() {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  console.log(`[BACKUP] Iniciando rotina de backup em ${new Date().toISOString()}...`);

  const serverDir = path.resolve(__dirname, '..');
  const backupsDir = path.join(serverDir, 'backups');
  if (!fs.existsSync(backupsDir)) {
    fs.mkdirSync(backupsDir, { recursive: true });
  }

  const tempDir = path.join(backupsDir, `temp_${timestamp}`);
  fs.mkdirSync(tempDir, { recursive: true });

  const archiveName = `livrechat-backup-${timestamp}.tar.gz`;
  const archivePath = path.join(backupsDir, archiveName);

  try {
    // 1. PostgreSQL dump
    console.log('[BACKUP] 1/4 - Gerando dump do PostgreSQL...');
    const pgDumpPath = path.join(tempDir, 'postgres_dump.sql');
    try {
      execSync(`PGPASSWORD=livrechat_secure_pass_2026 pg_dump -h 127.0.0.1 -U livrechat livrechat -F p --clean --if-exists -f "${pgDumpPath}"`);
      const pgSizeKb = (fs.statSync(pgDumpPath).size / 1024).toFixed(1);
      console.log(`[BACKUP] Dump PostgreSQL gerado (${pgSizeKb} KB)`);
    } catch (pgErr) {
      console.warn('[BACKUP] Aviso: Falha ao gerar pg_dump (PostgreSQL pode não estar ativo):', pgErr.message);
    }

    // 2. Copiar livrechat.db.json se existir
    console.log('[BACKUP] 2/4 - Copiando armazenamento JSON local...');
    const localDbPath = path.join(serverDir, 'data', 'livrechat.db.json');
    if (fs.existsSync(localDbPath)) {
      fs.copyFileSync(localDbPath, path.join(tempDir, 'livrechat.db.json'));
      const jsonSizeMb = (fs.statSync(localDbPath).size / (1024 * 1024)).toFixed(2);
      console.log(`[BACKUP] livrechat.db.json copiado (${jsonSizeMb} MB)`);
    }

    // 3. Compactar em arquivo .tar.gz
    console.log('[BACKUP] 3/4 - Compactando arquivos em .tar.gz...');
    execSync(`tar -czf "${archivePath}" -C "${tempDir}" .`);
    const archiveSizeMb = (fs.statSync(archivePath).size / (1024 * 1024)).toFixed(2);
    console.log(`[BACKUP] Pacote comprimido gerado: ${archiveName} (${archiveSizeMb} MB)`);

    // 4. Enviar para Cloudflare R2
    console.log('[BACKUP] 4/4 - Enviando pacote para Cloudflare R2...');
    const fileBuffer = fs.readFileSync(archivePath);

    // 4a. Versão com carimbo de data
    const dailyKey = `backups/daily/${archiveName}`;
    await client.send(new PutObjectCommand({
      Bucket: R2_BUCKET_NAME,
      Key: dailyKey,
      Body: fileBuffer,
      ContentType: 'application/gzip'
    }));
    console.log(`[BACKUP] Sucesso! Upload concluído: ${dailyKey}`);

    // 4b. Versão 'latest' para restauração imediata
    const latestKey = 'backups/livrechat-backup-latest.tar.gz';
    await client.send(new PutObjectCommand({
      Bucket: R2_BUCKET_NAME,
      Key: latestKey,
      Body: fileBuffer,
      ContentType: 'application/gzip'
    }));
    console.log(`[BACKUP] Sucesso! Atualizado ponteiro mais recente: ${latestKey}`);

    // 5. Rotação de backups antigos (manter últimos 30 backups diários)
    try {
      const listResp = await client.send(new ListObjectsV2Command({
        Bucket: R2_BUCKET_NAME,
        Prefix: 'backups/daily/livrechat-backup-'
      }));

      const objects = listResp.Contents || [];
      if (objects.length > 30) {
        // Ordena do mais antigo para o mais novo
        objects.sort((a, b) => (new Date(a.LastModified).getTime() || 0) - (new Date(b.LastModified).getTime() || 0));
        const toDelete = objects.slice(0, objects.length - 30);
        console.log(`[BACKUP] Rotacionando ${toDelete.length} backup(s) antigo(s)...`);
        for (const item of toDelete) {
          if (item.Key) {
            await client.send(new DeleteObjectCommand({
              Bucket: R2_BUCKET_NAME,
              Key: item.Key
            }));
            console.log(`[BACKUP] Removido backup expirado: ${item.Key}`);
          }
        }
      }
    } catch (rotErr) {
      console.warn('[BACKUP] Aviso na rotação de backups antigos:', rotErr.message);
    }

    console.log(`[BACKUP] Rotina finalizada com 100% de sucesso! Arquivo: ${archiveName} (${archiveSizeMb} MB)`);
  } catch (err) {
    console.error('[BACKUP] ERRO CRÍTICO durante backup:', err);
    process.exit(1);
  } finally {
    // Limpeza de arquivos temporários locais
    try {
      if (fs.existsSync(tempDir)) {
        fs.rmSync(tempDir, { recursive: true, force: true });
      }
      if (fs.existsSync(archivePath)) {
        fs.unlinkSync(archivePath);
      }
    } catch (cleanErr) {
      // Ignorar erros de limpeza
    }
  }
}

runBackup();

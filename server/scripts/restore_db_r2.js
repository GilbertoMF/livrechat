const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const { S3Client, GetObjectCommand } = require('@aws-sdk/client-s3');
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

async function runRestore() {
  const targetKey = process.argv[2] || 'backups/livrechat-backup-latest.tar.gz';
  console.log(`[RESTORE] Baixando backup do R2: ${targetKey}...`);

  const serverDir = path.resolve(__dirname, '..');
  const tempDir = path.join(serverDir, 'backups', 'restore_temp');
  fs.mkdirSync(tempDir, { recursive: true });
  const downloadedArchive = path.join(tempDir, 'backup.tar.gz');

  try {
    const response = await client.send(new GetObjectCommand({
      Bucket: R2_BUCKET_NAME,
      Key: targetKey
    }));

    const streamToBuffer = async (stream) => {
      const chunks = [];
      for await (const chunk of stream) chunks.push(chunk);
      return Buffer.concat(chunks);
    };

    const fileBuffer = await streamToBuffer(response.Body);
    fs.writeFileSync(downloadedArchive, fileBuffer);
    console.log(`[RESTORE] Download concluído (${(fileBuffer.length / (1024 * 1024)).toFixed(2)} MB). Descompactando...`);

    execSync(`tar -xzf "${downloadedArchive}" -C "${tempDir}"`);

    // 1. Restaurar livrechat.db.json
    const restoredJson = path.join(tempDir, 'livrechat.db.json');
    if (fs.existsSync(restoredJson)) {
      const targetJson = path.join(serverDir, 'data', 'livrechat.db.json');
      fs.copyFileSync(restoredJson, targetJson);
      console.log(`[RESTORE] livrechat.db.json restaurado com sucesso!`);
    }

    // 2. Restaurar PostgreSQL dump se disponível
    const restoredSql = path.join(tempDir, 'postgres_dump.sql');
    if (fs.existsSync(restoredSql)) {
      console.log(`[RESTORE] Restaurando banco PostgreSQL a partir do dump...`);
      try {
        execSync(`PGPASSWORD=livrechat_secure_pass_2026 psql -h 127.0.0.1 -U livrechat -d livrechat -f "${restoredSql}"`);
        console.log(`[RESTORE] PostgreSQL restaurado com sucesso!`);
      } catch (sqlErr) {
        console.warn(`[RESTORE] Aviso ao restaurar no PostgreSQL:`, sqlErr.message);
      }
    }

    console.log(`[RESTORE] Restauração completa concluída com sucesso!`);
  } catch (err) {
    console.error(`[RESTORE] Erro ao restaurar:`, err);
    process.exit(1);
  } finally {
    try {
      if (fs.existsSync(tempDir)) {
        fs.rmSync(tempDir, { recursive: true, force: true });
      }
    } catch (_) {}
  }
}

runRestore();

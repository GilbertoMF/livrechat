const fs = require('fs');
const path = require('path');
const { S3Client, PutObjectCommand } = require('@aws-sdk/client-s3');

const R2_ACCOUNT_ID = process.env.R2_ACCOUNT_ID || '9a8e754d976094a4d4b422fdc38eaf99';
const R2_ACCESS_KEY_ID = process.env.R2_ACCESS_KEY_ID || 'c31ee3bcf61e6f47df6a0fed8feadab9';
const R2_SECRET_ACCESS_KEY = process.env.R2_SECRET_ACCESS_KEY || '3feb7af31853dec25d95acd7f3ec2cf28bc7bd1f0fb37f98ada3b1680c5c438b';
const R2_BUCKET_NAME = process.env.R2_BUCKET_NAME || 'livrechat-media';
const R2_ENDPOINT = process.env.R2_ENDPOINT || `https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com`;
const R2_PUBLIC_URL = 'https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev';

const client = new S3Client({
  region: 'auto',
  endpoint: R2_ENDPOINT,
  credentials: {
    accessKeyId: R2_ACCESS_KEY_ID,
    secretAccessKey: R2_SECRET_ACCESS_KEY,
  },
});

async function uploadGame(filePath) {
  if (!fs.existsSync(filePath)) {
    console.error(`Arquivo não encontrado: ${filePath}`);
    process.exit(1);
  }

  const fileName = path.basename(filePath);
  const r2Key = `games/${fileName}`;
  const stats = fs.statSync(filePath);
  const sizeMb = (stats.size / (1024 * 1024)).toFixed(2);

  console.log(`Enviando ${fileName} (${sizeMb} MB) para R2: ${r2Key}...`);
  const fileStream = fs.createReadStream(filePath);
  const command = new PutObjectCommand({
    Bucket: R2_BUCKET_NAME,
    Key: r2Key,
    Body: fileStream,
    ContentType: 'application/octet-stream',
    ContentLength: stats.size,
  });

  const startTime = Date.now();
  await client.send(command);
  const duration = ((Date.now() - startTime) / 1000).toFixed(1);
  console.log(`Sucesso! Enviado em ${duration}s.`);
  console.log(`URL Pública: ${R2_PUBLIC_URL}/${r2Key}`);
}

const targetFile = process.argv[2] || '/home/ubuntu/livrechat/games/dbz_shin_budokai.cso';
uploadGame(targetFile).catch(err => {
  console.error('Erro no upload:', err);
  process.exit(1);
});

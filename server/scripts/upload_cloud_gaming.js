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

async function uploadFile(filePath, r2Key, contentType) {
  if (!fs.existsSync(filePath)) {
    console.error(`Arquivo não encontrado: ${filePath}`);
    return;
  }
  const stats = fs.statSync(filePath);
  console.log(`Enviando ${path.basename(filePath)} (${stats.size} bytes) para ${r2Key}...`);
  const fileStream = fs.createReadStream(filePath);
  const command = new PutObjectCommand({
    Bucket: R2_BUCKET_NAME,
    Key: r2Key,
    Body: fileStream,
    ContentType: contentType,
    ContentLength: stats.size,
  });
  await client.send(command);
  console.log(`✅ Sucesso! URL: ${R2_PUBLIC_URL}/${r2Key}`);
}

async function main() {
  const rootDir = path.resolve(__dirname, '..', '..');
  const pyPath = path.join(rootDir, 'cloud-gaming', 'colab_cloud_gaming.py');
  const nbPath = path.join(rootDir, 'cloud-gaming', 'LivreGames_3D_Cloud.ipynb');

  await uploadFile(pyPath, 'cloud-gaming/colab_cloud_gaming.py', 'text/x-python');
  await uploadFile(nbPath, 'cloud-gaming/LivreGames_3D_Cloud.ipynb', 'application/x-ipynb+json');
  await uploadFile(path.join(rootDir, 'virtualgl.deb'), 'tools/virtualgl.deb', 'application/vnd.debian.binary-package');
}

main().catch(err => {
  console.error('Erro no upload:', err);
  process.exit(1);
});

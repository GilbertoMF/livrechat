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

const releaseDir = path.resolve(__dirname, '../../android/app/build/outputs/apk/release');

const filesToUpload = [
  {
    localFile: path.join(releaseDir, 'app-arm64-v8a-release.apk'),
    r2Key: 'apks/livrechat-v1.1.4-arm64.apk',
  },
  {
    localFile: path.join(releaseDir, 'app-armeabi-v7a-release.apk'),
    r2Key: 'apks/livrechat-v1.1.4-armv7.apk',
  },
  {
    localFile: path.join(releaseDir, 'app-universal-release.apk'),
    r2Key: 'apks/livrechat-v1.1.4-universal.apk',
  },
];

async function uploadFile(item) {
  if (!fs.existsSync(item.localFile)) {
    console.error(`Arquivo não encontrado: ${item.localFile}`);
    return;
  }
  const fileStats = fs.statSync(item.localFile);
  const sizeMb = (fileStats.size / (1024 * 1024)).toFixed(2);
  console.log(`Enviando ${item.r2Key} (${sizeMb} MB)...`);

  const fileStream = fs.createReadStream(item.localFile);
  const command = new PutObjectCommand({
    Bucket: R2_BUCKET_NAME,
    Key: item.r2Key,
    Body: fileStream,
    ContentType: 'application/vnd.android.package-archive',
    ContentLength: fileStats.size,
  });

  await client.send(command);
  console.log(` Sucesso: ${R2_PUBLIC_URL}/${item.r2Key}`);
}

async function main() {
  console.log('Iniciando upload de APKs v1.1.4 para Cloudflare R2...');
  for (const item of filesToUpload) {
    await uploadFile(item);
  }
  console.log('\nTodos os APKs v1.1.4 foram publicados com sucesso no Cloudflare R2 CDN!');
}

main().catch(err => {
  console.error('Erro no upload para R2:', err);
  process.exit(1);
});

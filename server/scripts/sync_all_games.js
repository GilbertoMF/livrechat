const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
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

const GAMES = [
  {
    fileName: 'bomberman.cso',
    sourceUrl: 'https://archive.org/download/psp-cso-collection/Bomberman%20%28USA%29.cso',
  },
  {
    fileName: 'need_for_speed_most_wanted.cso',
    sourceUrl: 'https://archive.org/download/psp-cso-collection/Need%20for%20Speed%20-%20Most%20Wanted%205-1-0%20%28USA%29.cso',
  },
  {
    fileName: 'tekken_dark_resurrection.cso',
    sourceUrl: 'https://archive.org/download/psp-cso-collection/Tekken%20-%20Dark%20Resurrection%20%28USA%29.cso',
  },
  {
    fileName: 'naruto_shippuden_unh3.cso',
    sourceUrl: 'https://archive.org/download/psp-cso-collection/Naruto%20Shippuden%20-%20Ultimate%20Ninja%20Heroes%203%20%28USA%29.cso',
  },
  {
    fileName: 'gta_vice_city_stories.cso',
    sourceUrl: 'https://archive.org/download/psp-cso-collection/Grand%20Theft%20Auto%20-%20Vice%20City%20Stories%20%28USA%29.cso',
  },
];

const gamesDir = '/home/ubuntu/livrechat/games';

async function uploadToR2(filePath, fileName) {
  const r2Key = `games/${fileName}`;
  const stats = fs.statSync(filePath);
  const sizeMb = (stats.size / (1024 * 1024)).toFixed(2);
  console.log(`[R2] Enviando ${fileName} (${sizeMb} MB)...`);
  const fileStream = fs.createReadStream(filePath);
  const command = new PutObjectCommand({
    Bucket: R2_BUCKET_NAME,
    Key: r2Key,
    Body: fileStream,
    ContentType: 'application/octet-stream',
    ContentLength: stats.size,
  });
  await client.send(command);
  console.log(`[R2] Pronto: ${R2_PUBLIC_URL}/${r2Key}`);
}

async function run() {
  if (!fs.existsSync(gamesDir)) {
    fs.mkdirSync(gamesDir, { recursive: true });
  }

  for (const game of GAMES) {
    const localPath = path.join(gamesDir, game.fileName);
    if (!fs.existsSync(localPath) || fs.statSync(localPath).size < 10 * 1024 * 1024) {
      console.log(`\n[Download] Baixando ${game.fileName} via aria2 16 streams...`);
      try {
        execSync(`aria2c -s 16 -x 16 -j 16 -k 1M "${game.sourceUrl}" -d "${gamesDir}" -o "${game.fileName}"`, { stdio: 'inherit' });
      } catch (err) {
        console.error(`Erro ao baixar ${game.fileName}:`, err.message);
        continue;
      }
    } else {
      console.log(`\n[Local] ${game.fileName} já existe.`);
    }

    try {
      await uploadToR2(localPath, game.fileName);
    } catch (err) {
      console.error(`Erro no upload para R2 de ${game.fileName}:`, err.message);
    }
  }

  console.log('\nTodos os jogos foram sincronizados com o Cloudflare R2!');
}

run();

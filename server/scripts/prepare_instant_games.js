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
    id: 'super_bomberman_2',
    name: 'Super Bomberman 2',
    archiveName: 'Super Bomberman 2.7z',
    targetSmcRegex: /Super Bomberman 2 \(U\) \[!\].smc/i,
    cleanFileName: 'super_bomberman_2.smc',
  },
  {
    id: 'super_mario_kart',
    name: 'Super Mario Kart',
    archiveName: 'Super Mario Kart.7z',
    targetSmcRegex: /Super Mario Kart \(U\) \[!\].smc/i,
    cleanFileName: 'super_mario_kart.smc',
  },
  {
    id: 'mortal_kombat_3',
    name: 'Mortal Kombat 3',
    archiveName: 'Mortal Kombat 3.7z',
    targetSmcRegex: /Mortal Kombat 3 \(U\) \[!\].smc/i,
    cleanFileName: 'mortal_kombat_3.smc',
  },
  {
    id: 'super_street_fighter_2',
    name: 'Super Street Fighter II',
    archiveName: 'Super Street Fighter II - The New Challengers.7z',
    targetSmcRegex: /Super Street Fighter II - The New Challengers \(U\) \[!\].smc/i,
    cleanFileName: 'super_street_fighter_2.smc',
  },
  {
    id: 'top_gear_2',
    name: 'Top Gear 2',
    archiveName: 'Top Gear 2.7z',
    targetSmcRegex: /Top Gear 2 \(U\) \[!\].smc/i,
    cleanFileName: 'top_gear_2.smc',
  },
  {
    id: 'super_mario_world',
    name: 'Super Mario World',
    archiveName: 'Super Mario World.7z',
    targetSmcRegex: /Super Mario World \(U\)/i,
    cleanFileName: 'super_mario_world.smc',
  },
  {
    id: 'donkey_kong_country',
    name: 'Donkey Kong Country',
    archiveName: 'Donkey Kong Country.7z',
    targetSmcRegex: /Donkey Kong Country \(U\)/i,
    cleanFileName: 'donkey_kong_country.smc',
  },
  {
    id: 'mega_man_x',
    name: 'Mega Man X',
    archiveName: 'Mega Man X.7z',
    targetSmcRegex: /Mega Man X \(U\)/i,
    cleanFileName: 'mega_man_x.smc',
  },
  {
    id: 'zelda_alttp',
    name: 'The Legend of Zelda: A Link to the Past',
    archiveName: 'Legend of Zelda, The - A Link to the Past.7z',
    targetSmcRegex: /Link to the Past/i,
    cleanFileName: 'zelda_alttp.smc',
  },
  {
    id: 'super_metroid',
    name: 'Super Metroid',
    archiveName: 'Super Metroid.7z',
    targetSmcRegex: /Super Metroid \(JU\) \[!\].smc/i,
    cleanFileName: 'super_metroid.smc',
  },
  {
    id: 'top_gear',
    name: 'Top Gear',
    archiveName: 'Top Gear.7z',
    targetSmcRegex: /Top Gear \(U\) \[!\].smc/i,
    cleanFileName: 'top_gear.smc',
  },
  {
    id: 'chrono_trigger',
    name: 'Chrono Trigger',
    archiveName: 'Chrono Trigger.7z',
    targetSmcRegex: /Chrono Trigger \(U\) \[!\].smc/i,
    cleanFileName: 'chrono_trigger.smc',
  },
  {
    id: 'aladdin',
    name: "Disney's Aladdin",
    archiveName: 'Aladdin.7z',
    targetSmcRegex: /Aladdin \(U\)/i,
    cleanFileName: 'aladdin.smc',
  },
];

const WORK_DIR = '/home/ubuntu/livrechat/instant-games';

async function uploadToR2(filePath, r2Key) {
  const stats = fs.statSync(filePath);
  const sizeMb = (stats.size / (1024 * 1024)).toFixed(2);
  console.log(`[R2] Enviando ${path.basename(filePath)} (${sizeMb} MB) -> ${r2Key}...`);
  const fileStream = fs.createReadStream(filePath);
  const command = new PutObjectCommand({
    Bucket: R2_BUCKET_NAME,
    Key: r2Key,
    Body: fileStream,
    ContentType: 'application/octet-stream',
    ContentLength: stats.size,
  });
  await client.send(command);
  console.log(`[R2] OK: ${R2_PUBLIC_URL}/${r2Key}`);
}

async function run() {
  if (!fs.existsSync(WORK_DIR)) {
    fs.mkdirSync(WORK_DIR, { recursive: true });
  }

  for (const game of GAMES) {
    console.log(`\n=== Processando: ${game.name} ===`);
    const archivePath = path.join(WORK_DIR, game.archiveName);
    const finalSmcPath = path.join(WORK_DIR, game.cleanFileName);

    if (!fs.existsSync(finalSmcPath)) {
      if (!fs.existsSync(archivePath)) {
        const encodedUrl = `https://archive.org/download/gameplay-snes/${encodeURIComponent(game.archiveName)}`;
        console.log(`Baixando de: ${encodedUrl}...`);
        execSync(`curl -L -s -o "${archivePath}" "${encodedUrl}"`);
      }

      console.log(`Extraindo ${game.archiveName}...`);
      const extractDir = path.join(WORK_DIR, `temp_${game.id}`);
      fs.mkdirSync(extractDir, { recursive: true });
      execSync(`7z x -y "${archivePath}" -o"${extractDir}"`);

      // Procurar o arquivo .smc correto
      const extractedFiles = fs.readdirSync(extractDir);
      let foundFile = extractedFiles.find(f => game.targetSmcRegex.test(f));
      if (!foundFile) {
        // Fallback: qualquer .smc com (U) ou o primeiro .smc
        foundFile = extractedFiles.find(f => f.includes('(U)') && f.endsWith('.smc')) || extractedFiles.find(f => f.endsWith('.smc'));
      }

      if (!foundFile) {
        console.error(`Erro: Nenhum arquivo .smc encontrado para ${game.name}! Arquivos:`, extractedFiles);
        continue;
      }

      const srcSmc = path.join(extractDir, foundFile);
      fs.copyFileSync(srcSmc, finalSmcPath);
      console.log(`ROM selecionada: ${foundFile} -> ${game.cleanFileName}`);

      // Limpar pasta temporaria
      fs.rmSync(extractDir, { recursive: true, force: true });
    } else {
      console.log(`ROM já existe localmente: ${finalSmcPath}`);
    }

    // Upload para R2
    const r2Key = `instant-games/${game.cleanFileName}`;
    await uploadToR2(finalSmcPath, r2Key);
  }

  console.log('\n Todos os 5 jogos instantâneos foram preparados e sincronizados no Cloudflare R2 com sucesso!');
}

run().catch(console.error);

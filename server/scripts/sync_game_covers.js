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

const COVERS = [
  // PSP Games (7)
  { id: 'dbz_sb', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/1/12/Dragon_Ball_Z_Shin_Budokai_cover.jpg' },
  { id: 'gta_vcs', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/3/3e/GTA_Vice_City_Stories_PSP_boxart.jpg' },
  { id: 'naruto_unh3', ext: 'jpg', url: 'https://images.launchbox-app.com/3470cf1d-89e7-4f64-a499-38db60e3dc96.jpg' },
  { id: 'tekken_dr', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/d/d0/PSP-TekkenDarkRessurectionUSversion-FrontCover.jpg' },
  { id: 'nfs_mw', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/8/8e/Need_for_Speed_Most_Wanted_Box_Art.jpg' },
  { id: 'bomberman_psp', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/9/99/Bomberman_PSP.jpg' },
  { id: 'pes_2014', ext: 'png', url: 'https://upload.wikimedia.org/wikipedia/en/2/2b/Pes2014eucover.png' },

  // SNES Games (13)
  { id: 'super_bomberman_2', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/4/41/SNES_Super_Bomberman_2_cover_art.jpg' },
  { id: 'super_mario_kart', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/3/38/Supermariokart_box.JPG' },
  { id: 'mortal_kombat_3', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/9/9e/Mortal_Kombat_3_cover.JPG' },
  { id: 'super_street_fighter_2', ext: 'png', url: 'https://upload.wikimedia.org/wikipedia/en/7/7c/SSF2_US_flyer.png' },
  { id: 'top_gear_2', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/d/dd/Top_Gear_2_cover.jpg' },
  { id: 'super_mario_world', ext: 'png', url: 'https://upload.wikimedia.org/wikipedia/en/3/32/Super_Mario_World_Coverart.png' },
  { id: 'donkey_kong_country', ext: 'png', url: 'https://upload.wikimedia.org/wikipedia/en/1/1a/Donkey_Kong_Country_SNES_cover.png' },
  { id: 'mega_man_x', ext: 'png', url: 'https://upload.wikimedia.org/wikipedia/en/f/f1/Mega_Man_X_Coverart.png' },
  { id: 'zelda_alttp', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/2/21/The_Legend_of_Zelda_A_Link_to_the_Past_SNES_Game_Cover.jpg' },
  { id: 'super_metroid', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/e/e4/Smetroidbox.jpg' },
  { id: 'top_gear', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/7/7a/SNES_Top_Gear_cover_art.jpg' },
  { id: 'chrono_trigger', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/a/a7/Chrono_Trigger.jpg' },
  { id: 'aladdin', ext: 'jpg', url: 'https://upload.wikimedia.org/wikipedia/en/f/fd/Disney%27s_Aladdin_%28SNES%29_cover_art.jpg' },
];

const LOCAL_DIR = path.resolve(__dirname, '../public/covers');

async function run() {
  if (!fs.existsSync(LOCAL_DIR)) {
    fs.mkdirSync(LOCAL_DIR, { recursive: true });
  }

  console.log(`Baixando e enviando ${COVERS.length} capas de jogos para Cloudflare R2...`);

  for (const item of COVERS) {
    const filename = `${item.id}.${item.ext}`;
    const localPath = path.join(LOCAL_DIR, filename);

    console.log(`\n[Cover] ${item.id} -> ${filename}`);
    try {
      // Download with Wikipedia-compliant User-Agent
      const cmd = `curl -s -L -A "LivreChatApp/1.1 (https://github.com/GilbertoMF/livrechat; contact@livrechat.org)" "${item.url}" -o "${localPath}"`;
      execSync(cmd);

      const stats = fs.statSync(localPath);
      if (stats.size < 1000) {
        console.warn(`Aviso: Arquivo baixado muito pequeno (${stats.size} bytes)!`);
      } else {
        console.log(`Baixado com sucesso (${(stats.size / 1024).toFixed(1)} KB)`);
      }

      // Upload to R2
      const r2Key = `game-covers/${filename}`;
      const fileStream = fs.createReadStream(localPath);
      const contentType = item.ext === 'png' ? 'image/png' : 'image/jpeg';
      const command = new PutObjectCommand({
        Bucket: R2_BUCKET_NAME,
        Key: r2Key,
        Body: fileStream,
        ContentType: contentType,
        ContentLength: stats.size,
      });
      await client.send(command);
      console.log(`[R2 OK] ${R2_PUBLIC_URL}/${r2Key}`);
    } catch (err) {
      console.error(`Erro ao processar ${item.id}:`, err.message);
    }
  }

  console.log('\n Todas as capas foram sincronizadas no Cloudflare R2!');
}

run().catch(console.error);

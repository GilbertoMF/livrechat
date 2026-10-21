import http from 'http';
import path from 'path';
import express from 'express';
import cors from 'cors';
import { CONFIG } from './config/env';
import { db } from './db/database';
import { authenticateToken, rateLimiter } from './middleware/auth';
import { AuthController } from './modules/auth/auth.controller';
import { UsersController } from './modules/users/users.controller';
import { MessagesController } from './modules/messages/messages.controller';
import { CallsController } from './modules/calls/calls.controller';
import { StatusController } from './modules/status/status.controller';
import { MediaController, uploadMiddleware } from './modules/media/media.controller';
import { LivreTubeController } from './modules/livretube/livretube.controller';
import { gamesRouter } from './modules/games/games.controller';
import { netplayManager, netplayRouter } from './modules/games/netplay.server';
import { WebSocketManager } from './websocket/ws.server';
import { fcmService } from './services/fcm.service';

const app = express();
const server = http.createServer(app);

// Middlewares
app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Static assets & Public Download Portal
const publicDir = path.join(__dirname, '../public');
app.use(express.static(publicDir));

// Download Portal routes
app.get(['/', '/download', '/baixar', '/app'], (req, res) => {
  res.sendFile(path.join(publicDir, 'index.html'));
});

// Latest APK redirect
app.get(['/apks/livrechat-latest.apk', '/download/latest'], (req, res) => {
  res.redirect('https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/apks/livrechat-v1.1.28-universal.apk');
});

// Dedicated Architecture Download Routes (3 versions available)
app.get(['/download/arm64', '/apks/arm64'], (req, res) => {
  res.redirect('https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/apks/livrechat-v1.1.28-arm64.apk');
});
app.get(['/download/armv7', '/apks/armv7'], (req, res) => {
  res.redirect('https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/apks/livrechat-v1.1.28-armv7.apk');
});
app.get(['/download/universal', '/apks/universal'], (req, res) => {
  res.redirect('https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/apks/livrechat-v1.1.28-universal.apk');
});

// Games static hosting (CSO / ISO files)
const gamesStaticDir = process.env.GAMES_DIR || '/home/ubuntu/livrechat/games';
app.use('/games', express.static(gamesStaticDir));

// Health check
app.get('/health', async (req, res) => {
  let registeredFcmDevices = 0;
  try {
    registeredFcmDevices = await db.getFcmTokenCount();
  } catch (e) {}

  res.status(200).json({
    status: 'online',
    appName: 'LivreChat Server',
    version: '1.1.28',
    fcmInitialized: fcmService.isAvailable(),
    registeredFcmDevices,
    timestamp: new Date().toISOString()
  });
});

const CDN_APKS_BASE = 'https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/apks';

// App Versions API
app.get('/api/versions/check', (req, res) => {
  const clientVersionCode = parseInt((req.query.versionCode as string) || (req.headers['x-app-version-code'] as string) || '0', 10);
  const LATEST_CODE = 48;
  const MIN_SUPPORTED = 13;
  const isUpdateAvailable = clientVersionCode < LATEST_CODE;
  const isMandatory = clientVersionCode < MIN_SUPPORTED;

  res.status(200).json({
    clientVersionCode,
    latestVersion: '1.1.38',
    latestVersionCode: LATEST_CODE,
    minSupportedVersionCode: MIN_SUPPORTED,
    isUpdateAvailable,
    isMandatory,
    title: isMandatory ? 'Atualização Obrigatória' : 'Atualização Disponível',
    message: isMandatory
      ? 'Versões incompatíveis não podem se comunicar por segurança. Atualize para a versão mais recente.'
      : 'Nova versão 1.1.38: Central de Usuários & Estatísticas da Comunidade em tempo real (online, offline, contas criadas, ativos) no app e painel web!',
    apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.38-arm64.apk`,
    apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.38-armv7.apk`,
    apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.38-universal.apk`,
    downloadPortalUrl: '/download'
  });
});

app.get('/api/versions', (req, res) => {
  res.status(200).json({
    latest: {
      version: '1.1.38',
      versionCode: 48,
      minSupportedVersionCode: 13,
      releaseDate: '2026-10-21',
      mandatory: false,
      title: 'Atualização Disponível',
      message: 'Versão 1.1.38: Nova Central de Usuários e Métricas da Comunidade em tempo real (online, offline, ativos, total de contas criadas), disponível no app e através de painel web responsivo.',
      apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.38-arm64.apk`,
      apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.38-armv7.apk`,
      apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.38-universal.apk`,
      downloadPortalUrl: '/download',
      sizeMbArm64: '25.7 MB',
      sizeMbUniversal: '32.2 MB'
    },
    versions: [
      {
        version: '1.1.38',
        versionCode: 48,
        releaseDate: '2026-10-21',
        isLatest: true,
        changelog: '1. Central de Usuários & Métricas da Comunidade: visualize total de contas criadas, usuários online em tempo real, usuários offline e ativos nas últimas 24h; 2. Filtros interativos (Todos, Online, Ativos) com busca instantânea por nome ou @username; 3. Painel Web público e responsivo acessível pelo navegador em /central e /stats; 4. Acesso em 1 toque na barra superior do LivreChat ou nas Configurações.',
        apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.38-arm64.apk`,
        apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.38-armv7.apk`,
        apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.38-universal.apk`
      },
      {
        version: '1.1.37',
        versionCode: 47,
        releaseDate: '2026-10-20',
        isLatest: false,
        changelog: '1. Busca integrada no topo: ícone de pesquisa colocado ao lado do botão (?) de ajuda, liberando mais de 150dp de espaço na tela; 2. Muito mais espaço para navegar no catálogo de jogos sem poluição visual ou aperto com teclado; 3. Banners compactos e remoção de pílulas redundantes.',
        apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.37-arm64.apk`,
        apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.37-armv7.apk`,
        apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.37-universal.apk`
      },
      {
        version: '1.1.36',
        versionCode: 46,
        releaseDate: '2026-10-20',
        isLatest: false,
        changelog: '1. Correção no layout do LivreGames: o primeiro card de jogo agora aparece completo e centralizado sem ficar encolhido ou escondido atrás da barra de título; 2. Barra de pesquisa e seletor de consoles (SNES/PSP/Todos) e gêneros integrados de forma limpa e fluida.',
        apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.36-arm64.apk`,
        apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.36-armv7.apk`,
        apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.36-universal.apk`
      },
      {
        version: '1.1.35',
        versionCode: 45,
        releaseDate: '2026-10-20',
        isLatest: false,
        changelog: '1. Tela de jogo desobstruída: topo flutuante nos cantos laterais transparentes sem cobrir o HUD; 2. Botões Select e Start movidos do centro da tela para as laterais sob o direcional e ABXY; 3. Tela de Sala Multiplayer adaptada para modo paisagem em 2 colunas sem cortes; 4. Ocultação automática da barra superior ao abrir modais.',
        apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.35-arm64.apk`,
        apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.35-armv7.apk`,
        apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.35-universal.apk`
      },
      {
        version: '1.1.32',
        versionCode: 42,
        releaseDate: '2026-10-20',
        isLatest: false,
        changelog: '1. Novos jogos instantâneos de SNES (Super Mario World, Donkey Kong Country, Mega Man X, Zelda: A Link to the Past, Super Metroid, Top Gear, Chrono Trigger, Aladdin); 2. Assistir Juntos (Watch Together) estilo Facebook Messenger com player de vídeo integrado sincronizado ao vivo no topo do chat; 3. Opção de girar para tela horizontal enquanto joga sem recarregar o emulador.',
        apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.32-arm64.apk`,
        apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.32-armv7.apk`,
        apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.32-universal.apk`
      },
      {
        version: '1.1.31',
        versionCode: 41,
        releaseDate: '2026-10-20',
        isLatest: false,
        changelog: '1. Correção visual na tela de chat: a barra do LivreParty e os balões de áudio não se sobrepõem mais; 2. Nova aba de Jogos (LivreGames): jogue clássicos instantâneos e PSP com download direto e servidor Ad-Hoc multiplayer.',
        apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.31-arm64.apk`,
        apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.31-armv7.apk`,
        apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.31-universal.apk`
      },
      {
        version: '1.1.30',
        versionCode: 40,
        releaseDate: '2026-10-20',
        isLatest: false,
        changelog: '6 Superpoderes: Canais de Rádio Walkie-Talkie PX, Radar Tático e Bússola P2P, IA On-Device local, LivreParty sincronizada, Mensagens Dead Drop com autodestruição, e Backup Shamir 2/3.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '25.7 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.30-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Galaxy A10 / Básicos)', size: '22.3 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.30-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '32.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.30-universal.apk` }
        ]
      },
      {
        version: '1.1.29',
        versionCode: 39,
        releaseDate: '2026-10-19',
        isLatest: false,
        changelog: 'Superpoderes da Rede Offline Mesh: 1. Walkie-Talkie Instantâneo PTT (Push-to-Talk) com bips e viva-voz em tempo real; 2. Envio de fotos leves e notas de voz comprimidas via múltiplos saltos na malha; 3. Ponte de Internet Híbrida (compartilhamento automático de internet com nós sem sinal); 4. Mapa Topológico Interativo da Malha (Radar orbital visual com nós, conexões e partículas animadas).',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.29-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Galaxy A10 / Básicos)', size: '21.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.29-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.6 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.29-universal.apk` }
        ]
      },
      {
        version: '1.1.28',
        versionCode: 38,
        releaseDate: '2026-10-19',
        isLatest: false,
        changelog: 'Identificação visual e detalhada de nós intermediários na Rede Offline Mesh (exibe qual aparelho repassou e total de saltos); Roteamento multi-hop transparente entre aparelhos vizinhos.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.28-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Galaxy A10 / Básicos)', size: '21.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.28-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.6 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.28-universal.apk` }
        ]
      },
      {
        version: '1.1.27',
        versionCode: 37,
        releaseDate: '2026-10-19',
        isLatest: false,
        changelog: 'Deduplicação de aparelhos no Radar Próximos da Rede Offline; Transmissão e persistência nativa do conteúdo e autor de mensagens respondidas (Quote/Reply); Salto animado até a mensagem respondida.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.27-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Galaxy A10 / Básicos)', size: '21.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.27-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.6 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.27-universal.apk` }
        ]
      },
      {
        version: '1.1.26',
        versionCode: 36,
        releaseDate: '2026-10-19',
        isLatest: false,
        changelog: 'Persistência permanente de fotos e vídeos da câmera (armazenamento protegido que não é apagado pelo Android); Envio seguro de mídias mesmo se o upload R2 falhar (conversão automática para Base64 sem envio de caminhos locais); Caixa de saída inteligente no envio de arquivos offline; Aviso visual explicativo para fotos antigas não enviadas à nuvem.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.26-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Galaxy A10 / Básicos)', size: '21.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.26-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.6 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.26-universal.apk` }
        ]
      },
      {
        version: '1.1.25',
        versionCode: 35,
        releaseDate: '2026-10-19',
        isLatest: false,
        changelog: 'Download e abertura de arquivos e documentos recebidos (PDF, DOC, ZIP, APK, etc.) direto do servidor/nuvem; Balão de resposta agora exibe o texto exato e o autor da mensagem original (com salto animado até a mensagem ao clicar); Suporte aprimorado para reprodução e miniaturas de vídeos remotos.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.25-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Galaxy A10 / Básicos)', size: '21.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.25-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.6 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.25-universal.apk` }
        ]
      },
      {
        version: '1.1.24',
        versionCode: 34,
        releaseDate: '2026-10-19',
        isLatest: false,
        changelog: 'Protocolo Gossip P2P Mesh (descoberta automática de aparelhos remotos através de nós intermediários A -> B -> C), novo botão (+) para iniciar conversa com qualquer aparelho offline na malha e roteamento multi-hop com entrega confirmada (✓✓).',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.24-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Galaxy A10 / Básicos)', size: '21.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.24-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.6 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.24-universal.apk` }
        ]
      },
      {
        version: '1.1.23',
        versionCode: 33,
        releaseDate: '2026-10-19',
        isLatest: false,
        changelog: 'Desempate determinístico anti-colisão na conexão P2P da Rede Offline, auto-reconexão contínua, detecção de hardware (GPS/Bluetooth) e entrega instantânea de payloads.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.23-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '21.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.23-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.6 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.23-universal.apk` }
        ]
      },
      {
        version: '1.1.21',
        versionCode: 31,
        releaseDate: '2026-10-19',
        isLatest: false,
        changelog: 'Nova Aba de Rede Offline P2P Mesh (funciona 100% sem internet via BLE e Wi-Fi Direct com roteamento Store-and-Forward); LivreTube com mini-player flutuante e rotação de tela horizontal corrigida.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.21-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '21.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.21-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.6 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.21-universal.apk` }
        ]
      },
      {
        version: '1.1.20',
        versionCode: 30,
        releaseDate: '2026-10-19',
        isLatest: false,
        changelog: 'LivreGames Instant Cloud Play (sem download) a 60 FPS com WebAssembly, gamepad touch e multiplayer 1-toque.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.20-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.20-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.20-universal.apk` }
        ]
      },
      {
        version: '1.1.19',
        versionCode: 29,
        releaseDate: '2026-10-18',
        isLatest: false,
        changelog: 'LivreGames PSP com catálogo sob demanda e servidor Ad-Hoc oficial.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.19-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.19-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.19-universal.apk` }
        ]
      },
      {
        version: '1.1.18',
        versionCode: 28,
        releaseDate: '2026-10-18',
        isLatest: false,
        changelog: 'Versão 1.1.18: Chegou o LivreTube! Nova 4ª aba dedicada para assistir a vídeos do YouTube sem login, sem anúncios comerciais e com privacidade absoluta; Busca rápida por vídeos e canais; Pílulas de categorias (Em Alta, Música, Podcasts, Games, Notícias); Player moderno integrado com controles e proteção Shield.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.18-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.18-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.18-universal.apk` }
        ]
      },
      {
        version: '1.1.17',
        versionCode: 27,
        releaseDate: '2026-10-18',
        isLatest: false,
        changelog: 'Versão 1.1.17: Relatório Completo de UI/UX implementado; Burner IDs (IDs descartáveis de 8 dígitos válidos por 24h); Recuperação Social Descentralizada em 3 fragmentos com Quorum 2/3; PIN de Pânico/Coação; Modo Blackout Total OLED puro (#000000); Autolimpeza de Armazenamento Inteligente (7, 30, 90 dias); Modais padronizados com hierarquia de botões; Lista de contatos limpa com menu ⋮ e gesto de toque longo.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.17-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.17-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.17-universal.apk` }
        ]
      },
      {
        version: '1.1.16',
        versionCode: 26,
        releaseDate: '2026-10-17',
        isLatest: false,
        changelog: 'Versão 1.1.16: Correção do carregamento infinito em fotos recebidas com sanitização e auto-recuperação de cache; Menu de ações por toque longo em fotos, vídeos, áudios, documentos e arquivos P2P; Nova tela de chamadas moderna com animação de pulso, criptografia E2E ponta a ponta, botão minimizar e barra de chamada em andamento; Correção de orientação na criação de figurinhas.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.16-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.16-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.16-universal.apk` }
        ]
      },
      {
        version: '1.1.15',
        versionCode: 25,
        releaseDate: '2026-10-17',
        isLatest: false,
        changelog: 'Mega atualização 1.1.15: PIN de Coação (perfil falso sob ameaça), Modo Camuflagem (abre calculadora funcional), Modulador de Tom de Voz (Grave, Agudo, Robô), Queimar Conversa dos dois lados, Mensagem Envelopada com Senha, Modo Anti-Xeretador (Peek to view) e Transferência Direta P2P sem limite.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.15-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.15-armv7.apk` },
          { type: 'universal', name: 'APK Universal Completo', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.15-universal.apk` }
        ]
      },
      {
        version: '1.1.14',
        versionCode: 24,
        releaseDate: '2026-10-17',
        isLatest: false,
        changelog: 'Remoção do ícone de atalho de recado de vídeo (bolinha) da barra de digitação da conversa, mantendo a interface limpa estilo WhatsApp (anexos de vídeo continuam no clipe).',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.14-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.14-armv7.apk` },
          { type: 'universal', name: 'APK Universal (Todos os Aparelhos)', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.14-universal.apk` }
        ]
      },
      {
        version: '1.1.13',
        versionCode: 23,
        releaseDate: '2026-10-17',
        isLatest: false,
        changelog: 'Silenciar notificações de conversas estilo WhatsApp (8 horas, 1 semana, Sempre), correção completa na criação de figurinhas (recorte quadrado 512x512 PNG salvo na galeria), toque em chamada perdida abre a aba de chamadas sem ligar de volta e supressão de notificações no topo quando dentro do chat ativo.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.13-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.13-armv7.apk` },
          { type: 'universal', name: 'APK Universal (Todos os Aparelhos)', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.13-universal.apk` }
        ]
      },
      {
        version: '1.1.12',
        versionCode: 22,
        releaseDate: '2026-10-17',
        isLatest: false,
        changelog: 'Atendimento instantâneo de chamadas: ao tocar em Atender na notificação com o app minimizado, a chamada conecta direto sem precisar confirmar de novo na tela.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.12-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.12-armv7.apk` },
          { type: 'universal', name: 'APK Universal (Todos os Aparelhos)', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.12-universal.apk` }
        ]
      },
      {
        version: '1.1.11',
        versionCode: 21,
        releaseDate: '2026-10-17',
        isLatest: false,
        changelog: 'Sincronização instantânea de chamada tocando em segundo plano: o chamador vê "Chamando..." assim que o aparelho de destino começa a tocar via push FCM.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.11-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.11-armv7.apk` },
          { type: 'universal', name: 'APK Universal (Todos os Aparelhos)', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.11-universal.apk` }
        ]
      },
      {
        version: '1.1.8',
        versionCode: 18,
        releaseDate: '2026-10-17',
        isLatest: false,
        changelog: 'Correção definitiva na reprodução de mensagens de áudio/voz recebidas e detecção aprimorada para celulares 32-bit (Moto e6i).',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.8-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.8-armv7.apk` },
          { type: 'universal', name: 'APK Universal (Todos os Aparelhos)', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.8-universal.apk` }
        ]
      },
      {
        version: '1.1.7',
        versionCode: 17,
        releaseDate: '2026-10-17',
        isLatest: false,
        changelog: 'Migração definitiva para o Servidor Dedicado 24/7 de Alta Performance (Oracle Cloud Always Free) com zero limites de franquia, conexão instantânea (~20ms) e banco de dados PostgreSQL integrado.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.7-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.7-armv7.apk` },
          { type: 'universal', name: 'APK Universal (Todos os Aparelhos)', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.7-universal.apk` }
        ]
      },
      {
        version: '1.1.6',
        versionCode: 16,
        releaseDate: '2026-10-16',
        isLatest: false,
        changelog: 'Atualização dentro do próprio app com download em streaming, barra de progresso em tempo real e instalador integrado. Correção definitiva do bug do visto por último dos contatos offline.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '24.2 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.6-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '20.9 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.6-armv7.apk` },
          { type: 'universal', name: 'APK Universal (Todos os Aparelhos)', size: '30.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.6-universal.apk` }
        ]
      },
      {
        version: '1.1.4',
        versionCode: 14,
        releaseDate: '2026-10-16',
        isLatest: false,
        changelog: 'Transição 3D Cube no Flash (Stories estilo Instagram), navegação contínua entre contatos, sombras e perspectiva 3D realistas.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '25.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.4-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '22.0 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.4-armv7.apk` },
          { type: 'universal', name: 'APK Universal (Todos os Aparelhos)', size: '32.1 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.4-universal.apk` }
        ]
      },
      {
        version: '1.1.3',
        versionCode: 13,
        releaseDate: '2026-10-16',
        isLatest: false,
        changelog: 'Correção definitiva na rotação de tela (não desativa mais o travamento vertical do sistema) e download direto de APKs via CDN Cloudflare sem bloqueio no Chrome de celular.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '25.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.3-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '22.0 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.3-armv7.apk` },
          { type: 'universal', name: 'APK Universal (Todos os Aparelhos)', size: '32.1 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.3-universal.apk` }
        ]
      },
      {
        version: '1.1.2',
        versionCode: 12,
        releaseDate: '2026-10-16',
        isLatest: false,
        changelog: 'Banner de atualização obrigatória, trava de segurança entre versões diferentes e compatibilidade garantida de protocolo de mensagens e chamadas.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '25.4 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.2-arm64.apk` },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '22.0 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.2-armv7.apk` },
          { type: 'universal', name: 'APK Universal (Todos os Aparelhos)', size: '32.1 MB', url: `${CDN_APKS_BASE}/livrechat-v1.1.2-universal.apk` }
        ]
      },
      {
        version: '1.1.1',
        versionCode: 11,
        releaseDate: '2026-10-16',
        isLatest: true,
        changelog: 'Removida notificação persistente "Serviço ativo" (economia de bateria), status Online/Offline sincronizado com abertura/fechamento do app (estilo WhatsApp) e correção na exibição de nomes salvos de contatos em notificações e no perfil.',
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '25.4 MB', url: '/apks/livrechat-v1.1.1-arm64.apk' },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '22.0 MB', url: '/apks/livrechat-v1.1.1-armv7.apk' },
          { type: 'universal', name: 'APK Universal (Todos os Aparelhos)', size: '32.1 MB', url: '/apks/livrechat-v1.1.1-universal.apk' }
        ]
      },
      {
        version: '1.1.0',
        versionCode: 10,
        releaseDate: '2026-10-16',
        isLatest: false,
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64 (POCO / Modernos)', size: '25.4 MB', url: '/apks/livrechat-v1.1.0-arm64.apk' },
          { type: 'armv7', name: 'APK 32-bit ARMv7 (Moto e6i / Básicos)', size: '22.0 MB', url: '/apks/livrechat-v1.1.0-armv7.apk' },
          { type: 'universal', name: 'APK Universal (Todos os Aparelhos)', size: '32.1 MB', url: '/apks/livrechat-v1.1.0-universal.apk' }
        ]
      },
      {
        version: '1.0.9',
        versionCode: 9,
        releaseDate: '2026-10-16',
        isLatest: false,
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64', size: '25.4 MB', url: '/apks/livrechat-v1.0.9-arm64.apk' },
          { type: 'armv7', name: 'APK 32-bit ARMv7', size: '22.0 MB', url: '/apks/livrechat-v1.0.9-armv7.apk' },
          { type: 'universal', name: 'APK Universal', size: '32.1 MB', url: '/apks/livrechat-v1.0.9-universal.apk' }
        ]
      },
      {
        version: '1.0.8',
        versionCode: 8,
        releaseDate: '2026-10-16',
        isLatest: false,
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64', size: '25.4 MB', url: '/apks/livrechat-v1.0.8-arm64.apk' },
          { type: 'armv7', name: 'APK 32-bit ARMv7', size: '22.0 MB', url: '/apks/livrechat-v1.0.8-armv7.apk' },
          { type: 'universal', name: 'APK Universal', size: '32.1 MB', url: '/apks/livrechat-v1.0.8-universal.apk' }
        ]
      },
      {
        version: '1.0.7',
        versionCode: 7,
        releaseDate: '2026-10-16',
        isLatest: false,
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64', size: '24.9 MB', url: '/apks/livrechat-v1.0.7-arm64.apk' },
          { type: 'armv7', name: 'APK 32-bit ARMv7', size: '21.5 MB', url: '/apks/livrechat-v1.0.7-armv7.apk' },
          { type: 'universal', name: 'APK Universal', size: '31.4 MB', url: '/apks/livrechat-v1.0.7-universal.apk' }
        ]
      },
      {
        version: '1.0.6',
        versionCode: 6,
        releaseDate: '2026-10-16',
        isLatest: false,
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64', size: '24.9 MB', url: '/apks/livrechat-v1.0.6-arm64.apk' },
          { type: 'armv7', name: 'APK 32-bit ARMv7', size: '21.5 MB', url: '/apks/livrechat-v1.0.6-armv7.apk' },
          { type: 'universal', name: 'APK Universal', size: '31.4 MB', url: '/apks/livrechat-v1.0.6-universal.apk' }
        ]
      },
      {
        version: '1.0.5',
        versionCode: 5,
        releaseDate: '2026-10-16',
        isLatest: false,
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64', size: '24.9 MB', url: '/apks/livrechat-v1.0.5-arm64.apk' },
          { type: 'armv7', name: 'APK 32-bit ARMv7', size: '21.5 MB', url: '/apks/livrechat-v1.0.5-armv7.apk' },
          { type: 'universal', name: 'APK Universal', size: '31.4 MB', url: '/apks/livrechat-v1.0.5-universal.apk' }
        ]
      },
      {
        version: '1.0.4',
        versionCode: 4,
        releaseDate: '2026-10-16',
        isLatest: false,
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64', size: '24.9 MB', url: '/apks/livrechat-v1.0.4-arm64.apk' },
          { type: 'armv7', name: 'APK 32-bit ARMv7', size: '21.5 MB', url: '/apks/livrechat-v1.0.4-armv7.apk' },
          { type: 'universal', name: 'APK Universal', size: '31.4 MB', url: '/apks/livrechat-v1.0.4-universal.apk' }
        ]
      },
      {
        version: '1.0.3',
        versionCode: 3,
        releaseDate: '2026-10-16',
        isLatest: false,
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64', size: '24.9 MB', url: '/apks/livrechat-v1.0.3-arm64.apk' },
          { type: 'armv7', name: 'APK 32-bit ARMv7', size: '21.5 MB', url: '/apks/livrechat-v1.0.3-armv7.apk' },
          { type: 'universal', name: 'APK Universal', size: '31.4 MB', url: '/apks/livrechat-v1.0.3-universal.apk' }
        ]
      },
      {
        version: '1.0.2',
        versionCode: 3,
        releaseDate: '2026-10-16',
        isLatest: false,
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64', size: '24.9 MB', url: '/apks/livrechat-v1.0.2-arm64.apk' },
          { type: 'universal', name: 'APK Universal', size: '31.4 MB', url: '/apks/livrechat-v1.0.2-universal.apk' }
        ]
      },
      {
        version: '1.0.1',
        versionCode: 2,
        releaseDate: '2026-10-15',
        isLatest: false,
        downloads: [
          { type: 'arm64', name: 'APK Leve ARM64', size: '23.8 MB', url: '/apks/livrechat-v1.0.1-arm64.apk' },
          { type: 'universal', name: 'APK Universal', size: '30.0 MB', url: '/apks/livrechat-v1.0.1-universal.apk' }
        ]
      },
      {
        version: '1.0.0',
        versionCode: 1,
        releaseDate: '2026-10-10',
        isLatest: false,
        downloads: [
          { type: 'universal', name: 'APK Universal v1.0.0', size: '78.4 MB', url: '/apks/livrechat-v1.0.0-universal.apk' }
        ]
      }
    ]
  });
});

// ==========================================
// AUTH ROUTES (No phone number required)
// ==========================================
// Rate limited to prevent brute force (15 attempts per 5 minutes)
const authLimiter = rateLimiter(15, 5 * 60 * 1000);

app.post('/api/auth/register', authLimiter, AuthController.register);
app.post('/api/auth/login', authLimiter, AuthController.login);
app.post('/api/auth/recover', authLimiter, AuthController.recover);
app.post('/api/auth/virtual-number/request', authLimiter, AuthController.requestVirtualNumberCode);
app.post('/api/auth/virtual-number/verify', authLimiter, AuthController.verifyVirtualNumberCode);

// Protected Auth & Session routes
app.get('/api/auth/sessions', authenticateToken, AuthController.getSessions);
app.delete('/api/auth/sessions/:sessionId', authenticateToken, AuthController.revokeSession);
app.post('/api/auth/sessions/revoke-others', authenticateToken, AuthController.revokeOtherSessions);
app.delete('/api/auth/account', authenticateToken, AuthController.deleteAccount);

// ==========================================
// USERS & CONTACTS ROUTES
// ==========================================
app.get('/api/users/profile', authenticateToken, UsersController.getProfile);
app.get('/api/users/profile/:userId', authenticateToken, UsersController.getProfile);
app.put('/api/users/profile', authenticateToken, UsersController.updateProfile);
app.get('/api/users/search', authenticateToken, UsersController.searchUsers);

// Contacts
app.get('/api/users/contacts', authenticateToken, UsersController.getContacts);
app.post('/api/users/contacts', authenticateToken, UsersController.addContact);
app.post('/api/users/sync-contacts', authenticateToken, UsersController.syncContacts);
app.put('/api/users/contacts/:contactUserId', authenticateToken, UsersController.updateContact);
app.delete('/api/users/contacts/:contactUserId', authenticateToken, UsersController.removeContact);

// Blocking & Reports
app.get('/api/users/blocked', authenticateToken, UsersController.getBlockedUsers);
app.post('/api/users/block', authenticateToken, UsersController.blockUser);
app.delete('/api/users/unblock/:blockedUserId', authenticateToken, UsersController.unblockUser);
app.post('/api/users/report', authenticateToken, UsersController.reportUser);

// Preferences
app.get('/api/users/preferences', authenticateToken, UsersController.getPreferences);
app.put('/api/users/preferences', authenticateToken, UsersController.updatePreferences);

// FCM Push Tokens
app.post('/api/users/fcm-token', authenticateToken, UsersController.registerFcmToken);
app.delete('/api/users/fcm-token', authenticateToken, UsersController.removeFcmToken);

// Burner IDs (Identificadores Descartáveis)
app.post('/api/users/burner-id', authenticateToken, UsersController.generateBurnerId);
app.get('/api/users/burner-id', authenticateToken, UsersController.getBurnerId);
app.delete('/api/users/burner-id', authenticateToken, UsersController.destroyBurnerId);

// ==========================================
// MESSAGES & CONVERSATIONS ROUTES
// ==========================================
app.get('/api/messages/conversations', authenticateToken, MessagesController.getConversations);
app.post('/api/messages/conversations/direct', authenticateToken, MessagesController.getOrCreateConversation);
app.post('/api/messages/groups', authenticateToken, MessagesController.createGroup);
app.put('/api/messages/groups/:conversationId', authenticateToken, MessagesController.updateGroup);
app.put('/api/messages/groups/:conversationId/admins/:userId', authenticateToken, MessagesController.setGroupMemberAdmin);
app.put('/api/messages/groups/:conversationId/permissions', authenticateToken, MessagesController.updateGroupPermissions);
app.get('/api/messages/conversations/:conversationId/members', authenticateToken, MessagesController.getConversationMembers);
app.post('/api/messages/conversations/:conversationId/members', authenticateToken, MessagesController.addConversationMembers);
app.delete('/api/messages/conversations/:conversationId/members/:memberId', authenticateToken, MessagesController.removeConversationMember);
app.get('/api/messages/:conversationId', authenticateToken, MessagesController.getMessages);
app.post('/api/messages', authenticateToken, MessagesController.sendMessage);
app.put('/api/messages/:messageId', authenticateToken, MessagesController.editMessage);
app.delete('/api/messages/:messageId', authenticateToken, MessagesController.deleteMessage);
app.post('/api/messages/:messageId/delivered', authenticateToken, MessagesController.markAsDelivered);
app.post('/api/messages/:messageId/read', authenticateToken, MessagesController.markAsRead);
app.post('/api/messages/conversations/:conversationId/read', authenticateToken, MessagesController.markConversationAsRead);
app.delete('/api/messages/conversations/:conversationId/clear', authenticateToken, MessagesController.clearConversation);
app.delete('/api/messages/conversations/:conversationId', authenticateToken, MessagesController.deleteConversation);

// ==========================================
// CALLS ROUTES
// ==========================================
app.get('/api/calls/ice-servers', authenticateToken, CallsController.getIceServers);
app.get('/api/calls/history', authenticateToken, CallsController.getCallHistory);
app.post('/api/calls/record', authenticateToken, CallsController.recordCall);
app.delete('/api/calls/history', authenticateToken, CallsController.clearCallHistory);
app.post('/api/calls/ringing', authenticateToken, CallsController.notifyRinging);
app.post('/api/calls/answer', authenticateToken, CallsController.answerCall);
app.post('/api/calls/reject', authenticateToken, CallsController.rejectCall);

// ==========================================
// STATUS / STORIES ROUTES
// ==========================================
app.get('/api/status', authenticateToken, StatusController.getStatuses);
app.post('/api/status', authenticateToken, StatusController.createStatus);
app.delete('/api/status/:statusId', authenticateToken, StatusController.deleteStatus);
app.post('/api/status/:statusId/view', authenticateToken, StatusController.viewStatus);
app.post('/api/status/:statusId/react', authenticateToken, StatusController.reactStatus);

// ==========================================
// MEDIA & STORAGE ROUTES (Cloudflare R2)
// ==========================================
app.post('/api/media/upload', authenticateToken, uploadMiddleware.single('file'), MediaController.uploadMedia);
app.post('/api/media/presign', authenticateToken, MediaController.getPresignedUrl);

// ==========================================
// LIVRETUBE (Privacy Video & YouTube Frontend)
// ==========================================
app.get('/api/livretube/trending', LivreTubeController.getTrending);
app.get('/api/livretube/search', LivreTubeController.search);
app.get('/api/livretube/video/:id', LivreTubeController.getVideoDetails);

// ==========================================
// LIVREGAMES (PSP Retro Catalog & Multiplayer Server)
// ==========================================
app.use('/api/games', gamesRouter);
app.use('/api/games/netplay', netplayRouter);
app.use('/list', netplayRouter); // Compatibility with EmulatorJS default /list endpoint

// Attach WebSocket Manager & Netplay Socket.IO
export const wsManager = new WebSocketManager(server);
netplayManager.init(server);

// ==========================================
// COMMUNITY & SERVER STATS (Central de Usuários)
// ==========================================
app.get('/api/community/stats', async (req, res) => {
  try {
    const onlineUserIds = wsManager.getOnlineUserIds();
    const stats = await db.getCommunityStats(onlineUserIds);
    res.status(200).json(stats);
  } catch (err: any) {
    console.error('Error fetching community stats:', err);
    res.status(500).json({ error: 'Failed to fetch community stats', details: err?.message });
  }
});

app.get(['/central', '/stats', '/painel'], async (req, res) => {
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.send(`<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>LivreChat — Central da Comunidade & Usuários</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Plus Jakarta Sans', sans-serif; }
    body { background-color: #0B0E14; color: #F3F4F6; min-height: 100vh; padding: 24px 16px; }
    .container { max-width: 1080px; margin: 0 auto; }
    .header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 24px; padding-bottom: 20px; border-bottom: 1px solid #1F2937; }
    .logo-area { display: flex; align-items: center; gap: 14px; }
    .logo-badge { width: 44px; height: 44px; border-radius: 12px; background: linear-gradient(135deg, #8B5CF6, #6D28D9); display: flex; align-items: center; justify-content: center; font-size: 22px; box-shadow: 0 4px 14px rgba(139, 92, 246, 0.4); }
    .title h1 { font-size: 24px; font-weight: 800; color: #FFFFFF; display: flex; align-items: center; gap: 8px; }
    .title p { font-size: 13px; color: #9CA3AF; margin-top: 2px; }
    .live-status { display: flex; align-items: center; gap: 8px; background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.3); padding: 6px 14px; border-radius: 999px; font-size: 13px; font-weight: 600; color: #34D399; }
    .pulse-dot { width: 8px; height: 8px; border-radius: 50%; background: #10B981; animation: pulse 2s infinite; }
    @keyframes pulse { 0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); } 70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); } 100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); } }

    /* Grid de Métricas Principais */
    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .kpi-card { background: #151A23; border: 1px solid #232B3B; border-radius: 16px; padding: 20px; transition: transform 0.2s, border-color 0.2s; position: relative; overflow: hidden; }
    .kpi-card:hover { transform: translateY(-2px); border-color: #374151; }
    .kpi-icon { font-size: 24px; margin-bottom: 12px; }
    .kpi-value { font-size: 36px; font-weight: 800; line-height: 1; margin-bottom: 6px; }
    .kpi-label { font-size: 13px; font-weight: 600; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.5px; }
    .kpi-sub { font-size: 12px; color: #6B7280; margin-top: 4px; }
    .kpi-online { border-color: rgba(16, 185, 129, 0.3); }
    .kpi-online .kpi-value { color: #34D399; }
    .kpi-total .kpi-value { color: #A78BFA; }
    .kpi-offline .kpi-value { color: #9CA3AF; }
    .kpi-active .kpi-value { color: #60A5FA; }

    /* Mini Barra de Estatísticas */
    .mini-stats { display: flex; flex-wrap: wrap; gap: 12px; background: #151A23; border: 1px solid #232B3B; border-radius: 14px; padding: 14px 18px; margin-bottom: 24px; align-items: center; justify-content: space-between; font-size: 13px; color: #9CA3AF; }
    .mini-item { display: flex; align-items: center; gap: 6px; }
    .mini-item strong { color: #FFFFFF; }

    /* Lista de Usuários */
    .section-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
    .section-header h2 { font-size: 18px; font-weight: 700; color: #FFFFFF; }
    .search-box { background: #151A23; border: 1px solid #232B3B; border-radius: 12px; padding: 8px 14px; color: #FFF; font-size: 14px; outline: none; width: 100%; max-width: 320px; transition: border-color 0.2s; }
    .search-box:focus { border-color: #8B5CF6; }

    .users-table { width: 100%; border-collapse: separate; border-spacing: 0; background: #151A23; border: 1px solid #232B3B; border-radius: 16px; overflow: hidden; }
    .users-table th { background: #1A212E; padding: 12px 16px; text-align: left; font-size: 12px; font-weight: 700; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.5px; border-bottom: 1px solid #232B3B; }
    .users-table td { padding: 14px 16px; border-bottom: 1px solid #1F2937; font-size: 14px; vertical-align: middle; }
    .users-table tr:last-child td { border-bottom: none; }
    .users-table tr:hover { background: #1A212E; }

    .user-info { display: flex; align-items: center; gap: 12px; }
    .avatar { width: 38px; height: 38px; border-radius: 10px; background: #374151; display: flex; align-items: center; justify-content: center; font-weight: 700; color: #FFF; font-size: 15px; overflow: hidden; }
    .avatar img { width: 100%; height: 100%; object-fit: cover; }
    .user-name { font-weight: 700; color: #FFFFFF; }
    .user-handle { font-size: 12px; color: #9CA3AF; }

    .badge-online { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: 999px; background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.3); font-size: 11px; font-weight: 700; color: #34D399; }
    .badge-offline { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: 999px; background: rgba(156, 163, 175, 0.1); border: 1px solid rgba(156, 163, 175, 0.2); font-size: 11px; font-weight: 600; color: #9CA3AF; }

    .btn-refresh { background: #8B5CF6; color: #FFF; border: none; padding: 8px 16px; border-radius: 10px; font-weight: 600; font-size: 13px; cursor: pointer; transition: background 0.2s; display: flex; align-items: center; gap: 6px; }
    .btn-refresh:hover { background: #7C3AED; }

    @media (max-width: 640px) {
      .users-table th:nth-child(3), .users-table td:nth-child(3) { display: none; }
      .search-box { max-width: 100%; }
    }
  </style>
</head>
<body>
  <div class="container">
    <header class="header">
      <div class="logo-area">
        <div class="logo-badge">💬</div>
        <div class="title">
          <h1>LivreChat <span>Central</span></h1>
          <p>Monitoramento e estatísticas em tempo real da rede</p>
        </div>
      </div>
      <div style="display: flex; align-items: center; gap: 12px;">
        <div class="live-status">
          <div class="pulse-dot"></div>
          <span>Ao Vivo</span>
        </div>
        <button class="btn-refresh" onclick="fetchStats()">🔄 Atualizar</button>
      </div>
    </header>

    <div class="kpi-grid">
      <div class="kpi-card kpi-total">
        <div class="kpi-icon">👥</div>
        <div class="kpi-value" id="totalAccounts">--</div>
        <div class="kpi-label">Contas Criadas</div>
        <div class="kpi-sub">Total de usuários cadastrados</div>
      </div>
      <div class="kpi-card kpi-online">
        <div class="kpi-icon">🟢</div>
        <div class="kpi-value" id="onlineUsers">--</div>
        <div class="kpi-label">Online Agora</div>
        <div class="kpi-sub">Conectados via WebSocket</div>
      </div>
      <div class="kpi-card kpi-offline">
        <div class="kpi-icon">⚪</div>
        <div class="kpi-value" id="offlineUsers">--</div>
        <div class="kpi-label">Offline</div>
        <div class="kpi-sub">Sem conexão ativa</div>
      </div>
      <div class="kpi-card kpi-active">
        <div class="kpi-icon">⚡</div>
        <div class="kpi-value" id="activeToday">--</div>
        <div class="kpi-label">Usuários Ativos (Hoje)</div>
        <div class="kpi-sub">Atividade nas últimas 24h</div>
      </div>
    </div>

    <div class="mini-stats">
      <div class="mini-item">💬 Total Mensagens: <strong id="totalMessages">--</strong></div>
      <div class="mini-item">👥 Conversas Criadas: <strong id="totalConversations">--</strong></div>
      <div class="mini-item">📅 Ativos na Semana: <strong id="activeThisWeek">--</strong></div>
      <div class="mini-item">⏱️ Uptime Servidor: <strong id="uptime">--</strong></div>
      <div class="mini-item">🕒 Atualizado: <strong id="lastSync">--</strong></div>
    </div>

    <div class="section-header">
      <h2>Lista de Membros da Comunidade</h2>
      <input type="text" id="searchInput" class="search-box" placeholder="Pesquisar por nome ou @username..." oninput="filterUsers()">
    </div>

    <div style="overflow-x: auto;">
      <table class="users-table">
        <thead>
          <tr>
            <th>Usuário</th>
            <th>Status</th>
            <th>Visto por Último</th>
            <th>Cadastrado em</th>
          </tr>
        </thead>
        <tbody id="usersTableBody">
          <tr><td colspan="4" style="text-align: center; color: #9CA3AF; padding: 30px;">Carregando usuários...</td></tr>
        </tbody>
      </table>
    </div>
  </div>

  <script>
    let allUsers = [];

    function formatTimeAgo(isoString) {
      if (!isoString) return 'Nunca';
      const diffMs = Date.now() - new Date(isoString).getTime();
      const diffSec = Math.floor(diffMs / 1000);
      if (diffSec < 60) return 'Agora mesmo';
      const diffMin = Math.floor(diffSec / 60);
      if (diffMin < 60) return diffMin + 'm atrás';
      const diffHr = Math.floor(diffMin / 60);
      if (diffHr < 24) return diffHr + 'h atrás';
      const diffDays = Math.floor(diffHr / 24);
      return diffDays + 'd atrás';
    }

    function formatDate(isoString) {
      if (!isoString) return '--';
      try {
        const d = new Date(isoString);
        return d.toLocaleDateString('pt-BR');
      } catch (e) { return isoString; }
    }

    function formatUptime(seconds) {
      if (!seconds) return '0m';
      const d = Math.floor(seconds / 86400);
      const h = Math.floor((seconds % 86400) / 3600);
      const m = Math.floor((seconds % 3600) / 60);
      if (d > 0) return d + 'd ' + h + 'h';
      if (h > 0) return h + 'h ' + m + 'm';
      return m + 'm';
    }

    async function fetchStats() {
      try {
        const res = await fetch('/api/community/stats');
        const data = await res.json();

        document.getElementById('totalAccounts').textContent = data.totalAccounts ?? 0;
        document.getElementById('onlineUsers').textContent = data.onlineUsers ?? 0;
        document.getElementById('offlineUsers').textContent = data.offlineUsers ?? 0;
        document.getElementById('activeToday').textContent = data.activeToday ?? 0;
        document.getElementById('totalMessages').textContent = data.totalMessages ?? 0;
        document.getElementById('totalConversations').textContent = data.totalConversations ?? 0;
        document.getElementById('activeThisWeek').textContent = data.activeThisWeek ?? 0;
        document.getElementById('uptime').textContent = formatUptime(data.uptimeSeconds);
        document.getElementById('lastSync').textContent = new Date().toLocaleTimeString('pt-BR');

        allUsers = data.users || [];
        filterUsers();
      } catch (err) {
        console.error('Erro ao carregar estatísticas:', err);
      }
    }

    function filterUsers() {
      const q = (document.getElementById('searchInput').value || '').toLowerCase().trim();
      const filtered = allUsers.filter(u => {
        return !q || (u.displayName && u.displayName.toLowerCase().includes(q)) ||
               (u.username && u.username.toLowerCase().includes(q));
      });

      const tbody = document.getElementById('usersTableBody');
      if (filtered.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align: center; color: #9CA3AF; padding: 24px;">Nenhum usuário encontrado.</td></tr>';
        return;
      }

      tbody.innerHTML = filtered.map(u => {
        const initial = (u.displayName || u.username || '?').charAt(0).toUpperCase();
        const avatarHtml = u.avatarUrl
          ? '<img src="' + u.avatarUrl + '" alt="' + u.username + '">'
          : initial;

        const statusHtml = u.isOnline
          ? '<span class="badge-online"><span class="pulse-dot"></span> Online</span>'
          : '<span class="badge-offline">⚪ Offline</span>';

        return '<tr>' +
          '<td>' +
            '<div class="user-info">' +
              '<div class="avatar">' + avatarHtml + '</div>' +
              '<div>' +
                '<div class="user-name">' + (u.displayName || u.username) + '</div>' +
                '<div class="user-handle">@' + u.username + '</div>' +
              '</div>' +
            '</div>' +
          '</td>' +
          '<td>' + statusHtml + '</td>' +
          '<td style="color: #9CA3AF;">' + (u.isOnline ? '<span style="color: #34D399; font-weight: 600;">Agora</span>' : formatTimeAgo(u.lastActive)) + '</td>' +
          '<td style="color: #9CA3AF;">' + formatDate(u.createdAt) + '</td>' +
        '</tr>';
      }).join('');
    }

    fetchStats();
    setInterval(fetchStats, 5000);
  </script>
</body>
</html>`);
});

// Start server
async function startServer() {
  await db.init();
  server.listen(CONFIG.PORT, '0.0.0.0', () => {
    console.log(`====================================================`);
    console.log(` LivreChat Server iniciado na porta ${CONFIG.PORT}`);
    console.log(` HTTP API: http://localhost:${CONFIG.PORT}`);
    console.log(` WebSocket: ws://localhost:${CONFIG.PORT}/ws`);
    console.log(` Netplay Socket.IO: http://localhost:${CONFIG.PORT}/socket.io`);
    console.log(` Modo: ${CONFIG.NODE_ENV}`);
    console.log(`====================================================`);
  });
}

startServer();

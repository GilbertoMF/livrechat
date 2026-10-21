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
  res.redirect('https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/apks/livrechat-v1.1.40-universal.apk');
});

// Dedicated Architecture Download Routes (3 versions available)
app.get(['/download/arm64', '/apks/arm64'], (req, res) => {
  res.redirect('https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/apks/livrechat-v1.1.40-arm64.apk');
});
app.get(['/download/armv7', '/apks/armv7'], (req, res) => {
  res.redirect('https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/apks/livrechat-v1.1.40-armv7.apk');
});
app.get(['/download/universal', '/apks/universal'], (req, res) => {
  res.redirect('https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/apks/livrechat-v1.1.40-universal.apk');
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
  const LATEST_CODE = 50;
  const MIN_SUPPORTED = 13;
  const isUpdateAvailable = clientVersionCode < LATEST_CODE;
  const isMandatory = clientVersionCode < MIN_SUPPORTED;

  res.status(200).json({
    clientVersionCode,
    latestVersion: '1.1.40',
    latestVersionCode: LATEST_CODE,
    minSupportedVersionCode: MIN_SUPPORTED,
    isUpdateAvailable,
    isMandatory,
    title: isMandatory ? 'Atualização Obrigatória' : 'Atualização Disponível',
    message: isMandatory
      ? 'Versões incompatíveis não podem se comunicar por segurança. Atualize para a versão mais recente.'
      : 'Nova versão 1.1.40: Sistema completo de denúncia estilo WhatsApp, envio das últimas mensagens para segurança e moderação na Central Web.',
    apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.40-arm64.apk`,
    apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.40-armv7.apk`,
    apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.40-universal.apk`,
    downloadPortalUrl: '/download'
  });
});

app.get('/api/versions', (req, res) => {
  res.status(200).json({
    latest: {
      version: '1.1.40',
      versionCode: 50,
      minSupportedVersionCode: 13,
      releaseDate: '2026-10-21',
      mandatory: false,
      title: 'Atualização Disponível',
      message: 'Versão 1.1.40: Sistema de denúncia igual ao WhatsApp, moderação de mensagens e usuários na Central Web.',
      apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.40-arm64.apk`,
      apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.40-armv7.apk`,
      apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.40-universal.apk`,
      downloadPortalUrl: '/download',
      sizeMbArm64: '25.8 MB',
      sizeMbUniversal: '32.3 MB'
    },
    versions: [
      {
        version: '1.1.40',
        versionCode: 50,
        releaseDate: '2026-10-21',
        isLatest: true,
        changelog: '1. Denúncia completa igual ao WhatsApp: denuncie mensagens ou contatos enviando o snapshot das últimas 5 mensagens da conversa para moderação; 2. Opção de bloquear contato e limpar conversa ao denunciar; 3. Moderação em tempo real na Central Web com bloqueio/suspensão de usuários.',
        apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.40-arm64.apk`,
        apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.40-armv7.apk`,
        apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.40-universal.apk`
      },
      {
        version: '1.1.39',
        versionCode: 49,
        releaseDate: '2026-10-21',
        isLatest: false,
        changelog: '1. Otimizações de desempenho e consumo de memória; 2. Ajustes na privacidade da rede e estabilização de conexões P2P.',
        apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.39-arm64.apk`,
        apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.39-armv7.apk`,
        apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.39-universal.apk`
      },
      {
        version: '1.1.38',
        versionCode: 48,
        releaseDate: '2026-10-21',
        isLatest: false,
        changelog: 'Melhorias internas no sistema de métricas do servidor.',
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
// REPORTS & MODERATION ROUTES (WhatsApp Style)
// ==========================================
// User submission from Android App
app.post('/api/reports', authenticateToken, UsersController.reportUser);

// ==========================================
// COMMUNITY & SERVER STATS (Central de Usuários e Moderação - Privada Web Admin)
// ==========================================
const ADMIN_PIN = process.env.ADMIN_PIN || 'admin2026';

app.get('/api/community/stats', async (req, res) => {
  const pin = (req.headers['x-admin-pin'] as string) || (req.query.key as string) || (req.query.pin as string);
  if (pin !== ADMIN_PIN) {
    return res.status(401).json({ error: 'Acesso restrito ao Administrador', code: 'UNAUTHORIZED' });
  }

  try {
    const onlineUserIds = wsManager.getOnlineUserIds();
    const stats = await db.getCommunityStats(onlineUserIds);
    res.status(200).json(stats);
  } catch (err: any) {
    console.error('Error fetching community stats:', err);
    res.status(500).json({ error: 'Failed to fetch community stats', details: err?.message });
  }
});

// Admin Reports List
app.get('/api/reports', async (req, res) => {
  const pin = (req.headers['x-admin-pin'] as string) || (req.query.key as string) || (req.query.pin as string);
  if (pin !== ADMIN_PIN) {
    return res.status(401).json({ error: 'Acesso restrito ao Administrador', code: 'UNAUTHORIZED' });
  }

  try {
    const limit = parseInt(req.query.limit as string) || 100;
    const offset = parseInt(req.query.offset as string) || 0;
    const reports = await db.getReports(limit, offset);
    res.status(200).json({ reports });
  } catch (err: any) {
    console.error('Error fetching reports:', err);
    res.status(500).json({ error: 'Failed to fetch reports', details: err?.message });
  }
});

// Admin Update Report (Status, Notes, Action Taken)
app.patch('/api/reports/:id', async (req, res) => {
  const pin = (req.headers['x-admin-pin'] as string) || (req.query.key as string) || (req.query.pin as string);
  if (pin !== ADMIN_PIN) {
    return res.status(401).json({ error: 'Acesso restrito ao Administrador', code: 'UNAUTHORIZED' });
  }

  try {
    const { id } = req.params;
    const { status, action_taken, admin_notes } = req.body;
    await db.updateReport(id, {
      status,
      action_taken,
      admin_notes
    });
    res.status(200).json({ success: true, message: 'Denúncia atualizada com sucesso.' });
  } catch (err: any) {
    console.error('Error updating report:', err);
    res.status(500).json({ error: 'Failed to update report', details: err?.message });
  }
});

// Admin Ban / Unban User
app.post('/api/admin/users/:id/ban', async (req, res) => {
  const pin = (req.headers['x-admin-pin'] as string) || (req.query.key as string) || (req.query.pin as string);
  if (pin !== ADMIN_PIN) {
    return res.status(401).json({ error: 'Acesso restrito ao Administrador', code: 'UNAUTHORIZED' });
  }

  try {
    const { id } = req.params;
    const { banned, reason } = req.body;
    const isBanned = !!banned;
    await db.setUserBanned(id, isBanned);

    if (isBanned) {
      wsManager.disconnectUser(id, reason || 'Sua conta foi suspensa por violar as diretrizes da comunidade.');
    }

    res.status(200).json({ success: true, banned: isBanned });
  } catch (err: any) {
    console.error('Error toggling user ban status:', err);
    res.status(500).json({ error: 'Failed to update user ban status', details: err?.message });
  }
});

app.get(['/central', '/stats', '/painel', '/admin'], async (req, res) => {
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.send(`<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>LivreChat — Central de Administração & Moderação</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Plus Jakarta Sans', sans-serif; }
    body { background-color: #0B0E14; color: #F3F4F6; min-height: 100vh; padding: 24px 16px; }
    .container { max-width: 1140px; margin: 0 auto; }
    .header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 24px; padding-bottom: 20px; border-bottom: 1px solid #1F2937; }
    .logo-area { display: flex; align-items: center; gap: 14px; }
    .logo-badge { width: 44px; height: 44px; border-radius: 12px; background: linear-gradient(135deg, #8B5CF6, #6D28D9); display: flex; align-items: center; justify-content: center; font-size: 22px; box-shadow: 0 4px 14px rgba(139, 92, 246, 0.4); }
    .title h1 { font-size: 24px; font-weight: 800; color: #FFFFFF; display: flex; align-items: center; gap: 8px; }
    .title p { font-size: 13px; color: #9CA3AF; margin-top: 2px; }
    .live-status { display: flex; align-items: center; gap: 8px; background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.3); padding: 6px 14px; border-radius: 999px; font-size: 13px; font-weight: 600; color: #34D399; }
    .pulse-dot { width: 8px; height: 8px; border-radius: 50%; background: #10B981; animation: pulse 2s infinite; }
    @keyframes pulse { 0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); } 70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); } 100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); } }

    /* Grid de Métricas Principais */
    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px; margin-bottom: 24px; }
    .kpi-card { background: #151A23; border: 1px solid #232B3B; border-radius: 16px; padding: 18px; transition: transform 0.2s, border-color 0.2s; position: relative; overflow: hidden; cursor: pointer; }
    .kpi-card:hover { transform: translateY(-2px); border-color: #374151; }
    .kpi-icon { font-size: 24px; margin-bottom: 10px; }
    .kpi-value { font-size: 34px; font-weight: 800; line-height: 1; margin-bottom: 6px; }
    .kpi-label { font-size: 12px; font-weight: 600; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.5px; }
    .kpi-sub { font-size: 12px; color: #6B7280; margin-top: 4px; }
    .kpi-online { border-color: rgba(16, 185, 129, 0.3); }
    .kpi-online .kpi-value { color: #34D399; }
    .kpi-total .kpi-value { color: #A78BFA; }
    .kpi-offline .kpi-value { color: #9CA3AF; }
    .kpi-active .kpi-value { color: #60A5FA; }
    .kpi-reports { border-color: rgba(239, 68, 68, 0.3); }
    .kpi-reports .kpi-value { color: #F87171; }
    .kpi-reports.has-pending { background: rgba(239, 68, 68, 0.08); border-color: rgba(239, 68, 68, 0.5); }

    /* Mini Barra de Estatísticas */
    .mini-stats { display: flex; flex-wrap: wrap; gap: 12px; background: #151A23; border: 1px solid #232B3B; border-radius: 14px; padding: 14px 18px; margin-bottom: 24px; align-items: center; justify-content: space-between; font-size: 13px; color: #9CA3AF; }
    .mini-item { display: flex; align-items: center; gap: 6px; }
    .mini-item strong { color: #FFFFFF; }

    /* Tabs de Navegação */
    .tabs-bar { display: flex; gap: 8px; margin-bottom: 20px; border-bottom: 1px solid #232B3B; padding-bottom: 8px; }
    .tab-btn { background: transparent; border: none; padding: 10px 18px; border-radius: 10px; color: #9CA3AF; font-size: 14px; font-weight: 700; cursor: pointer; transition: all 0.2s; display: flex; align-items: center; gap: 8px; }
    .tab-btn:hover { color: #FFFFFF; background: #151A23; }
    .tab-btn.active { color: #FFFFFF; background: #8B5CF6; box-shadow: 0 4px 14px rgba(139, 92, 246, 0.3); }
    .tab-badge { background: #EF4444; color: #FFF; font-size: 11px; font-weight: 800; padding: 2px 7px; border-radius: 999px; }
    .tab-badge.hidden { display: none; }

    /* Barra de Ações & Filtros */
    .section-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
    .section-header h2 { font-size: 18px; font-weight: 700; color: #FFFFFF; }
    .filter-pills { display: flex; gap: 6px; flex-wrap: wrap; }
    .filter-pill { background: #151A23; border: 1px solid #232B3B; color: #9CA3AF; padding: 6px 12px; border-radius: 8px; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s; }
    .filter-pill.active { background: #374151; color: #FFFFFF; border-color: #4B5563; }
    .search-box { background: #151A23; border: 1px solid #232B3B; border-radius: 12px; padding: 8px 14px; color: #FFF; font-size: 14px; outline: none; width: 100%; max-width: 300px; transition: border-color 0.2s; }
    .search-box:focus { border-color: #8B5CF6; }

    /* Tabelas */
    .data-table { width: 100%; border-collapse: separate; border-spacing: 0; background: #151A23; border: 1px solid #232B3B; border-radius: 16px; overflow: hidden; }
    .data-table th { background: #1A212E; padding: 12px 14px; text-align: left; font-size: 12px; font-weight: 700; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.5px; border-bottom: 1px solid #232B3B; }
    .data-table td { padding: 12px 14px; border-bottom: 1px solid #1F2937; font-size: 13px; vertical-align: middle; }
    .data-table tr:last-child td { border-bottom: none; }
    .data-table tr:hover { background: #1A212E; }

    .user-info { display: flex; align-items: center; gap: 10px; }
    .avatar { width: 36px; height: 36px; border-radius: 10px; background: #374151; display: flex; align-items: center; justify-content: center; font-weight: 700; color: #FFF; font-size: 14px; overflow: hidden; flex-shrink: 0; }
    .avatar img { width: 100%; height: 100%; object-fit: cover; }
    .user-name { font-weight: 700; color: #FFFFFF; line-height: 1.2; }
    .user-handle { font-size: 11px; color: #9CA3AF; margin-top: 2px; }

    /* Badges */
    .badge-online { display: inline-flex; align-items: center; gap: 5px; padding: 3px 8px; border-radius: 999px; background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.3); font-size: 11px; font-weight: 700; color: #34D399; }
    .badge-offline { display: inline-flex; align-items: center; gap: 5px; padding: 3px 8px; border-radius: 999px; background: rgba(156, 163, 175, 0.1); border: 1px solid rgba(156, 163, 175, 0.2); font-size: 11px; font-weight: 600; color: #9CA3AF; }
    .badge-banned { display: inline-flex; align-items: center; gap: 4px; padding: 3px 8px; border-radius: 999px; background: rgba(239, 68, 68, 0.2); border: 1px solid rgba(239, 68, 68, 0.4); font-size: 11px; font-weight: 700; color: #F87171; }
    .badge-active-user { display: inline-flex; align-items: center; gap: 4px; padding: 3px 8px; border-radius: 999px; background: rgba(59, 130, 246, 0.15); border: 1px solid rgba(59, 130, 246, 0.3); font-size: 11px; font-weight: 600; color: #93C5FD; }

    .badge-reason { display: inline-block; padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 700; background: #232B3B; color: #E5E7EB; border: 1px solid #374151; }
    .reason-spam { background: rgba(245, 158, 11, 0.15); border-color: rgba(245, 158, 11, 0.3); color: #FBBF24; }
    .reason-fraud { background: rgba(239, 68, 68, 0.15); border-color: rgba(239, 68, 68, 0.3); color: #F87171; }
    .reason-harassment { background: rgba(236, 72, 153, 0.15); border-color: rgba(236, 72, 153, 0.3); color: #F472B6; }

    .badge-status { display: inline-block; padding: 3px 8px; border-radius: 999px; font-size: 11px; font-weight: 700; }
    .status-pending { background: rgba(245, 158, 11, 0.15); border: 1px solid rgba(245, 158, 11, 0.3); color: #FBBF24; }
    .status-resolved { background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.3); color: #34D399; }
    .status-dismissed { background: rgba(156, 163, 175, 0.15); border: 1px solid rgba(156, 163, 175, 0.2); color: #9CA3AF; }

    /* Botões */
    .btn-refresh { background: #8B5CF6; color: #FFF; border: none; padding: 8px 16px; border-radius: 10px; font-weight: 600; font-size: 13px; cursor: pointer; transition: background 0.2s; display: flex; align-items: center; gap: 6px; }
    .btn-refresh:hover { background: #7C3AED; }
    .btn-logout { background: #232B3B; color: #9CA3AF; border: 1px solid #374151; padding: 8px 14px; border-radius: 10px; font-weight: 600; font-size: 13px; cursor: pointer; transition: all 0.2s; }
    .btn-logout:hover { background: #374151; color: #FFF; }

    .btn-action { border: none; padding: 6px 10px; border-radius: 8px; font-size: 12px; font-weight: 700; cursor: pointer; transition: all 0.2s; display: inline-flex; align-items: center; gap: 4px; }
    .btn-ban { background: rgba(239, 68, 68, 0.15); color: #F87171; border: 1px solid rgba(239, 68, 68, 0.3); }
    .btn-ban:hover { background: #EF4444; color: #FFF; }
    .btn-unban { background: rgba(16, 185, 129, 0.15); color: #34D399; border: 1px solid rgba(16, 185, 129, 0.3); }
    .btn-unban:hover { background: #10B981; color: #FFF; }
    .btn-view-msgs { background: rgba(139, 92, 246, 0.15); color: #C4B5FD; border: 1px solid rgba(139, 92, 246, 0.3); }
    .btn-view-msgs:hover { background: #8B5CF6; color: #FFF; }
    .btn-resolve { background: rgba(16, 185, 129, 0.12); color: #34D399; border: 1px solid rgba(16, 185, 129, 0.25); }
    .btn-resolve:hover { background: #10B981; color: #FFF; }
    .btn-dismiss { background: #232B3B; color: #9CA3AF; border: 1px solid #374151; }
    .btn-dismiss:hover { background: #374151; color: #FFF; }

    /* Modal de Inspeção de Mensagens (Estilo WhatsApp) */
    .modal-overlay { position: fixed; inset: 0; background: rgba(11, 14, 20, 0.88); backdrop-filter: blur(8px); display: none; align-items: center; justify-content: center; z-index: 10000; padding: 16px; }
    .modal-overlay.active { display: flex; }
    .modal-card { background: #151A23; border: 1px solid #374151; border-radius: 20px; width: 100%; max-width: 580px; max-height: 90vh; display: flex; flex-direction: column; overflow: hidden; box-shadow: 0 20px 50px rgba(0,0,0,0.7); }
    .modal-header { padding: 18px 20px; border-bottom: 1px solid #232B3B; display: flex; justify-content: space-between; align-items: center; }
    .modal-header h3 { font-size: 16px; font-weight: 800; color: #FFF; display: flex; align-items: center; gap: 8px; }
    .modal-close { background: transparent; border: none; color: #9CA3AF; font-size: 20px; cursor: pointer; padding: 4px; }
    .modal-close:hover { color: #FFF; }
    .modal-subtitle { font-size: 12px; color: #9CA3AF; padding: 10px 20px; background: #10141D; border-bottom: 1px solid #232B3B; }
    .modal-chat-body { padding: 20px; overflow-y: auto; flex: 1; background: #0B141A; display: flex; flex-direction: column; gap: 12px; }
    .chat-bubble { max-width: 82%; padding: 10px 14px; border-radius: 12px; font-size: 13px; line-height: 1.45; position: relative; box-shadow: 0 1px 3px rgba(0,0,0,0.3); word-break: break-word; }
    .bubble-outgoing { align-self: flex-end; background: #005C4B; color: #E9EDEF; border-top-right-radius: 2px; }
    .bubble-incoming { align-self: flex-start; background: #202C33; color: #E9EDEF; border-top-left-radius: 2px; }
    .bubble-sender { font-size: 11px; font-weight: 700; margin-bottom: 3px; }
    .bubble-outgoing .bubble-sender { color: #53BDEB; }
    .bubble-incoming .bubble-sender { color: #F59E0B; }
    .bubble-time { font-size: 10px; color: rgba(255, 255, 255, 0.6); margin-top: 4px; text-align: right; }
    .modal-footer { padding: 14px 20px; border-top: 1px solid #232B3B; display: flex; justify-content: space-between; align-items: center; gap: 10px; background: #151A23; }

    /* Auth Gate Modal */
    #loginGate { position: fixed; inset: 0; background: rgba(11, 14, 20, 0.96); backdrop-filter: blur(10px); display: flex; align-items: center; justify-content: center; z-index: 10001; padding: 20px; }
    .login-card { background: #151A23; border: 1px solid #374151; border-radius: 20px; padding: 36px 28px; width: 100%; max-width: 400px; text-align: center; box-shadow: 0 16px 40px rgba(0,0,0,0.6); }
    .pin-input { width: 100%; padding: 12px 16px; border-radius: 12px; background: #0B0E14; border: 1px solid #232B3B; color: #fff; font-size: 15px; margin-top: 18px; outline: none; text-align: center; letter-spacing: 2px; }
    .pin-input:focus { border-color: #8B5CF6; }
    .btn-login { width: 100%; margin-top: 14px; padding: 12px; border-radius: 12px; background: #8B5CF6; color: #fff; border: none; font-weight: 700; font-size: 14px; cursor: pointer; transition: background 0.2s; }
    .btn-login:hover { background: #7C3AED; }

    /* Toast Notification */
    #toast { position: fixed; bottom: 24px; right: 24px; background: #1E293B; border: 1px solid #334155; color: #FFF; padding: 12px 20px; border-radius: 12px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); font-size: 13px; font-weight: 600; display: none; z-index: 10002; }

    @media (max-width: 768px) {
      .data-table th:nth-child(4), .data-table td:nth-child(4),
      .data-table th:nth-child(5), .data-table td:nth-child(5) { display: none; }
      .search-box { max-width: 100%; }
    }
  </style>
</head>
<body>
  <!-- Modal de Autenticação Admin -->
  <div id="loginGate" style="display: none;">
    <div class="login-card">
      <div style="font-size: 42px; margin-bottom: 12px;">🛡️</div>
      <h2 style="color: #FFFFFF; font-size: 20px; font-weight: 800; margin-bottom: 6px;">Central do Administrador</h2>
      <p style="color: #9CA3AF; font-size: 13px; line-height: 1.5;">Painel privado do LivreChat. Digite sua senha de Administrador para prosseguir.</p>
      <input type="password" id="pinInput" class="pin-input" placeholder="Senha de Admin" onkeydown="if(event.key==='Enter') loginWithPin()">
      <div id="loginError" style="color: #EF4444; font-size: 12px; margin-top: 10px; display: none; font-weight: 600;">Senha incorreta. Tente novamente!</div>
      <button class="btn-login" onclick="loginWithPin()">Acessar Central</button>
    </div>
  </div>

  <!-- Toast Notification -->
  <div id="toast"></div>

  <!-- Modal de Inspeção de Mensagens (WhatsApp Style) -->
  <div id="messageModal" class="modal-overlay">
    <div class="modal-card">
      <div class="modal-header">
        <h3>💬 Snapshot da Conversa Denunciada</h3>
        <button class="modal-close" onclick="closeMessageModal()">✕</button>
      </div>
      <div class="modal-subtitle" id="modalSubtitle">
        Últimas 5 mensagens da conversa enviadas no momento da denúncia
      </div>
      <div class="modal-chat-body" id="modalChatBody">
        <!-- Balões inseridos dinamicamente -->
      </div>
      <div class="modal-footer" id="modalFooter">
        <button class="btn-action btn-dismiss" onclick="closeMessageModal()">Fechar</button>
        <div style="display: flex; gap: 8px;" id="modalActionButtons">
          <!-- Botões rápidos de moderação -->
        </div>
      </div>
    </div>
  </div>

  <div class="container">
    <header class="header">
      <div class="logo-area">
        <div class="logo-badge">💬</div>
        <div class="title">
          <h1>LivreChat <span>Central</span></h1>
          <p>Administração & Moderação em Tempo Real (WhatsApp Style Reporting)</p>
        </div>
      </div>
      <div style="display: flex; align-items: center; gap: 10px;">
        <div class="live-status">
          <div class="pulse-dot"></div>
          <span>Ao Vivo</span>
        </div>
        <button class="btn-refresh" onclick="fetchAll()">🔄 Atualizar</button>
        <button class="btn-logout" onclick="logoutAdmin()">🔒 Bloquear</button>
      </div>
    </header>

    <!-- Grid de Métricas Principais -->
    <div class="kpi-grid">
      <div class="kpi-card kpi-total" onclick="switchTab('users')">
        <div class="kpi-icon">👥</div>
        <div class="kpi-value" id="totalAccounts">--</div>
        <div class="kpi-label">Contas Criadas</div>
        <div class="kpi-sub">Total cadastrados</div>
      </div>
      <div class="kpi-card kpi-online" onclick="switchTab('users')">
        <div class="kpi-icon">🟢</div>
        <div class="kpi-value" id="onlineUsers">--</div>
        <div class="kpi-label">Online Agora</div>
        <div class="kpi-sub">Conectados via WebSocket</div>
      </div>
      <div class="kpi-card kpi-offline" onclick="switchTab('users')">
        <div class="kpi-icon">⚪</div>
        <div class="kpi-value" id="offlineUsers">--</div>
        <div class="kpi-label">Offline</div>
        <div class="kpi-sub">Sem conexão ativa</div>
      </div>
      <div class="kpi-card kpi-active" onclick="switchTab('users')">
        <div class="kpi-icon">⚡</div>
        <div class="kpi-value" id="activeToday">--</div>
        <div class="kpi-label">Ativos Hoje</div>
        <div class="kpi-sub">Últimas 24 horas</div>
      </div>
      <div class="kpi-card kpi-reports" id="kpiReportsCard" onclick="switchTab('reports')">
        <div class="kpi-icon">🚨</div>
        <div class="kpi-value" id="pendingReportsCount">0</div>
        <div class="kpi-label">Denúncias Pendentes</div>
        <div class="kpi-sub" id="totalReportsSub">Total: 0 recebidas</div>
      </div>
    </div>

    <!-- Mini Estatísticas -->
    <div class="mini-stats">
      <div class="mini-item">💬 Mensagens: <strong id="totalMessages">--</strong></div>
      <div class="mini-item">👥 Conversas: <strong id="totalConversations">--</strong></div>
      <div class="mini-item">📅 Ativos na Semana: <strong id="activeThisWeek">--</strong></div>
      <div class="mini-item">⏱️ Uptime Servidor: <strong id="uptime">--</strong></div>
      <div class="mini-item">🕒 Sincronizado: <strong id="lastSync">--</strong></div>
    </div>

    <!-- Navegação por Abas -->
    <div class="tabs-bar">
      <button class="tab-btn active" id="tabBtnUsers" onclick="switchTab('users')">
        👥 Usuários Cadastrados (<span id="tabUsersCount">0</span>)
      </button>
      <button class="tab-btn" id="tabBtnReports" onclick="switchTab('reports')">
        🚨 Denúncias & Moderação
        <span class="tab-badge hidden" id="tabPendingBadge">0</span>
      </button>
    </div>

    <!-- ABA 1: USUÁRIOS CADASTRADOS -->
    <div id="tabContentUsers">
      <div class="section-header">
        <h2>Membros da Comunidade</h2>
        <input type="text" id="searchInput" class="search-box" placeholder="Pesquisar nome ou @username..." oninput="filterUsers()">
      </div>

      <div style="overflow-x: auto;">
        <table class="data-table">
          <thead>
            <tr>
              <th>Usuário</th>
              <th>Conexão</th>
              <th>Status Conta</th>
              <th>Visto por Último</th>
              <th>Cadastrado em</th>
              <th style="text-align: right;">Ação</th>
            </tr>
          </thead>
          <tbody id="usersTableBody">
            <tr><td colspan="6" style="text-align: center; color: #9CA3AF; padding: 30px;">Carregando usuários...</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ABA 2: DENÚNCIAS & MODERAÇÃO -->
    <div id="tabContentReports" style="display: none;">
      <div class="section-header">
        <div>
          <h2>Fila de Moderação de Denúncias</h2>
          <p style="font-size: 12px; color: #9CA3AF; margin-top: 2px;">Denúncias enviadas por usuários pelo chat com as últimas 5 mensagens da conversa</p>
        </div>
        <div class="filter-pills">
          <button class="filter-pill active" id="filterAll" onclick="setReportFilter('all')">Todas</button>
          <button class="filter-pill" id="filterPending" onclick="setReportFilter('pending')">🟡 Pendentes</button>
          <button class="filter-pill" id="filterResolved" onclick="setReportFilter('resolved')">🟢 Resolvidas</button>
          <button class="filter-pill" id="filterDismissed" onclick="setReportFilter('dismissed')">⚪ Descartadas</button>
        </div>
      </div>

      <div style="overflow-x: auto;">
        <table class="data-table">
          <thead>
            <tr>
              <th>Data</th>
              <th>Denunciante</th>
              <th>Denunciado</th>
              <th>Motivo & Detalhes</th>
              <th>Snapshot Conversa</th>
              <th>Status</th>
              <th style="text-align: right;">Ações de Moderação</th>
            </tr>
          </thead>
          <tbody id="reportsTableBody">
            <tr><td colspan="7" style="text-align: center; color: #9CA3AF; padding: 30px;">Carregando denúncias...</td></tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <script>
    let allUsers = [];
    let allReports = [];
    let currentTab = 'users';
    let currentReportFilter = 'all';

    const urlParams = new URLSearchParams(window.location.search);
    const keyFromUrl = urlParams.get('key') || urlParams.get('pin');
    if (keyFromUrl) {
      localStorage.setItem('admin_pin', keyFromUrl);
    }

    function getAdminPin() {
      return localStorage.getItem('admin_pin') || '';
    }

    function showLogin() {
      document.getElementById('loginGate').style.display = 'flex';
      setTimeout(() => document.getElementById('pinInput').focus(), 100);
    }

    function hideLogin() {
      document.getElementById('loginGate').style.display = 'none';
      document.getElementById('loginError').style.display = 'none';
    }

    function loginWithPin() {
      const pin = document.getElementById('pinInput').value.trim();
      if (!pin) return;
      localStorage.setItem('admin_pin', pin);
      fetchAll();
    }

    function logoutAdmin() {
      localStorage.removeItem('admin_pin');
      document.getElementById('pinInput').value = '';
      showLogin();
    }

    function showToast(msg) {
      const t = document.getElementById('toast');
      t.textContent = msg;
      t.style.display = 'block';
      setTimeout(() => { t.style.display = 'none'; }, 3500);
    }

    function switchTab(tab) {
      currentTab = tab;
      const btnUsers = document.getElementById('tabBtnUsers');
      const btnReports = document.getElementById('tabBtnReports');
      const contentUsers = document.getElementById('tabContentUsers');
      const contentReports = document.getElementById('tabContentReports');

      if (tab === 'users') {
        btnUsers.classList.add('active');
        btnReports.classList.remove('active');
        contentUsers.style.display = 'block';
        contentReports.style.display = 'none';
      } else {
        btnUsers.classList.remove('active');
        btnReports.classList.add('active');
        contentUsers.style.display = 'none';
        contentReports.style.display = 'block';
      }
    }

    function setReportFilter(filter) {
      currentReportFilter = filter;
      ['all', 'pending', 'resolved', 'dismissed'].forEach(f => {
        const btn = document.getElementById('filter' + f.charAt(0).toUpperCase() + f.slice(1));
        if (btn) btn.classList.toggle('active', f === filter);
      });
      renderReports();
    }

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
        return d.toLocaleDateString('pt-BR') + ' ' + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
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

    async function fetchAll() {
      await Promise.all([fetchStats(), fetchReports()]);
    }

    async function fetchStats() {
      const pin = getAdminPin();
      if (!pin) { showLogin(); return; }

      try {
        const res = await fetch('/api/community/stats', {
          headers: { 'x-admin-pin': pin }
        });

        if (res.status === 401) {
          localStorage.removeItem('admin_pin');
          document.getElementById('loginError').style.display = 'block';
          showLogin();
          return;
        }

        hideLogin();
        const data = await res.json();

        document.getElementById('totalAccounts').textContent = data.totalAccounts ?? 0;
        document.getElementById('tabUsersCount').textContent = data.totalAccounts ?? 0;
        document.getElementById('onlineUsers').textContent = data.onlineUsers ?? 0;
        document.getElementById('offlineUsers').textContent = data.offlineUsers ?? 0;
        document.getElementById('activeToday').textContent = data.activeToday ?? 0;
        document.getElementById('totalMessages').textContent = data.totalMessages ?? 0;
        document.getElementById('totalConversations').textContent = data.totalConversations ?? 0;
        document.getElementById('activeThisWeek').textContent = data.activeThisWeek ?? 0;
        document.getElementById('uptime').textContent = formatUptime(data.uptimeSeconds);
        document.getElementById('lastSync').textContent = new Date().toLocaleTimeString('pt-BR');

        const pending = data.pendingReports ?? 0;
        const totalR = data.totalReports ?? 0;
        document.getElementById('pendingReportsCount').textContent = pending;
        document.getElementById('totalReportsSub').textContent = 'Total: ' + totalR + ' recebidas';

        const kpiCard = document.getElementById('kpiReportsCard');
        const badge = document.getElementById('tabPendingBadge');
        if (pending > 0) {
          kpiCard.classList.add('has-pending');
          badge.textContent = pending;
          badge.classList.remove('hidden');
        } else {
          kpiCard.classList.remove('has-pending');
          badge.classList.add('hidden');
        }

        allUsers = data.users || [];
        filterUsers();
      } catch (err) {
        console.error('Erro ao carregar estatísticas:', err);
      }
    }

    async function fetchReports() {
      const pin = getAdminPin();
      if (!pin) return;

      try {
        const res = await fetch('/api/reports', {
          headers: { 'x-admin-pin': pin }
        });
        if (res.ok) {
          const data = await res.json();
          allReports = data.reports || [];
          renderReports();
        }
      } catch (err) {
        console.error('Erro ao carregar denúncias:', err);
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
        tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: #9CA3AF; padding: 24px;">Nenhum usuário encontrado.</td></tr>';
        return;
      }

      tbody.innerHTML = filtered.map(u => {
        const initial = (u.displayName || u.username || '?').charAt(0).toUpperCase();
        const avatarHtml = u.avatarUrl
          ? '<img src="' + u.avatarUrl + '" alt="' + u.username + '">'
          : initial;

        const connectionHtml = u.isOnline
          ? '<span class="badge-online"><span class="pulse-dot"></span> Online</span>'
          : '<span class="badge-offline">⚪ Offline</span>';

        const statusContaHtml = u.isBanned
          ? '<span class="badge-banned">⛔ Banido</span>'
          : '<span class="badge-active-user">Ativa</span>';

        const actionBtn = u.isBanned
          ? '<button class="btn-action btn-unban" onclick="toggleBan(\\'' + u.id + '\\', false, \\'' + (u.displayName || u.username) + '\\')">✅ Desbanir</button>'
          : '<button class="btn-action btn-ban" onclick="toggleBan(\\'' + u.id + '\\', true, \\'' + (u.displayName || u.username) + '\\')">🚫 Banir</button>';

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
          '<td>' + connectionHtml + '</td>' +
          '<td>' + statusContaHtml + '</td>' +
          '<td style="color: #9CA3AF;">' + (u.isOnline ? '<span style="color: #34D399; font-weight: 600;">Agora</span>' : formatTimeAgo(u.lastActive)) + '</td>' +
          '<td style="color: #9CA3AF;">' + formatDate(u.createdAt) + '</td>' +
          '<td style="text-align: right;">' + actionBtn + '</td>' +
        '</tr>';
      }).join('');
    }

    function renderReports() {
      const filtered = allReports.filter(r => {
        if (currentReportFilter === 'all') return true;
        return (r.status || 'pending') === currentReportFilter;
      });

      const tbody = document.getElementById('reportsTableBody');
      if (filtered.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #9CA3AF; padding: 28px;">Nenhuma denúncia nesta categoria.</td></tr>';
        return;
      }

      tbody.innerHTML = filtered.map(r => {
        const reporterInitial = (r.reporter_display_name || r.reporter_username || '?').charAt(0).toUpperCase();
        const reportedInitial = (r.reported_display_name || r.reported_username || '?').charAt(0).toUpperCase();

        const msgs = Array.isArray(r.recent_messages) ? r.recent_messages : (typeof r.recent_messages === 'string' ? JSON.parse(r.recent_messages || '[]') : []);
        const msgCount = msgs.length;

        let reasonClass = 'badge-reason';
        const reasonLower = (r.reason || '').toLowerCase();
        if (reasonLower.includes('spam')) reasonClass += ' reason-spam';
        else if (reasonLower.includes('golpe') || reasonLower.includes('fraude')) reasonClass += ' reason-fraud';
        else if (reasonLower.includes('assédio') || reasonLower.includes('ofensa') || reasonLower.includes('violento')) reasonClass += ' reason-harassment';

        let statusBadge = '<span class="badge-status status-pending">🟡 Pendente</span>';
        if (r.status === 'resolved') statusBadge = '<span class="badge-status status-resolved">🟢 Resolvida</span>';
        else if (r.status === 'dismissed') statusBadge = '<span class="badge-status status-dismissed">⚪ Descartada</span>';

        const reportedStatusBadge = r.reported_is_banned
          ? ' <span class="badge-banned" style="font-size: 10px; padding: 1px 5px;">Banido</span>'
          : '';

        const actionBanBtn = r.reported_is_banned
          ? '<button class="btn-action btn-unban" onclick="toggleBan(\\'' + r.reported_user_id + '\\', false, \\'' + (r.reported_display_name || r.reported_username) + '\\')">✅ Desbanir</button>'
          : '<button class="btn-action btn-ban" onclick="toggleBan(\\'' + r.reported_user_id + '\\', true, \\'' + (r.reported_display_name || r.reported_username) + '\\')">🚫 Banir</button>';

        const resolveBtn = r.status !== 'resolved'
          ? '<button class="btn-action btn-resolve" onclick="updateReportStatus(\\'' + r.id + '\\', \\'resolved\\', \\'moderated\\')">✔️ Resolver</button>'
          : '';

        const dismissBtn = r.status !== 'dismissed'
          ? '<button class="btn-action btn-dismiss" onclick="updateReportStatus(\\'' + r.id + '\\', \\'dismissed\\', \\'dismissed\\')">✖️ Descartar</button>'
          : '';

        return '<tr>' +
          '<td style="color: #9CA3AF; font-size: 12px; white-space: nowrap;">' + formatDate(r.created_at) + '</td>' +
          '<td>' +
            '<div class="user-info">' +
              '<div class="avatar">' + (r.reporter_avatar_url ? '<img src="' + r.reporter_avatar_url + '">' : reporterInitial) + '</div>' +
              '<div>' +
                '<div class="user-name">' + (r.reporter_display_name || r.reporter_username || 'Desconhecido') + '</div>' +
                '<div class="user-handle">@' + (r.reporter_username || '-') + '</div>' +
              '</div>' +
            '</div>' +
          '</td>' +
          '<td>' +
            '<div class="user-info">' +
              '<div class="avatar" style="border: 1px solid rgba(239, 68, 68, 0.4);">' + (r.reported_avatar_url ? '<img src="' + r.reported_avatar_url + '">' : reportedInitial) + '</div>' +
              '<div>' +
                '<div class="user-name">' + (r.reported_display_name || r.reported_username || 'Desconhecido') + reportedStatusBadge + '</div>' +
                '<div class="user-handle">@' + (r.reported_username || '-') + '</div>' +
              '</div>' +
            '</div>' +
          '</td>' +
          '<td>' +
            '<span class="' + reasonClass + '">' + (r.reason || 'Denúncia') + '</span>' +
            (r.details ? '<div style="font-size: 12px; color: #D1D5DB; margin-top: 4px; max-width: 240px;">' + escapeHtml(r.details) + '</div>' : '') +
          '</td>' +
          '<td>' +
            (msgCount > 0
              ? '<button class="btn-action btn-view-msgs" onclick="openMessageModal(\\'' + r.id + '\\')">💬 Ver ' + msgCount + ' Mensagens</button>'
              : '<span style="color: #6B7280; font-size: 12px;">Sem mensagens</span>') +
          '</td>' +
          '<td>' + statusBadge + '</td>' +
          '<td style="text-align: right;">' +
            '<div style="display: flex; gap: 6px; justify-content: flex-end; flex-wrap: wrap;">' +
              actionBanBtn +
              resolveBtn +
              dismissBtn +
            '</div>' +
          '</td>' +
        '</tr>';
      }).join('');
    }

    function escapeHtml(text) {
      if (!text) return '';
      return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function openMessageModal(reportId) {
      const report = allReports.find(r => r.id === reportId);
      if (!report) return;

      const msgs = Array.isArray(report.recent_messages) ? report.recent_messages : (typeof report.recent_messages === 'string' ? JSON.parse(report.recent_messages || '[]') : []);
      const modal = document.getElementById('messageModal');
      const chatBody = document.getElementById('modalChatBody');
      const subtitle = document.getElementById('modalSubtitle');
      const actionButtons = document.getElementById('modalActionButtons');

      subtitle.innerHTML = '<strong>' + (report.reported_display_name || report.reported_username) + '</strong> foi denunciado por <em>' + (report.reason || 'Violação') + '</em>';

      if (msgs.length === 0) {
        chatBody.innerHTML = '<div style="text-align: center; color: #9CA3AF; padding: 40px;">Nenhuma mensagem registrada no snapshot desta denúncia.</div>';
      } else {
        chatBody.innerHTML = msgs.map(m => {
          const isOutgoing = m.isOutgoing === true || m.senderId === report.reporter_id;
          const bubbleClass = isOutgoing ? 'chat-bubble bubble-outgoing' : 'chat-bubble bubble-incoming';
          const senderLabel = isOutgoing
            ? (report.reporter_display_name || report.reporter_username || 'Denunciante')
            : (report.reported_display_name || report.reported_username || 'Denunciado');

          let timeText = '';
          if (m.timestamp) {
            try {
              const d = new Date(m.timestamp);
              timeText = d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
            } catch (e) { timeText = ''; }
          }

          let content = escapeHtml(m.content || '');
          if (m.messageType && m.messageType !== 'TEXT') {
            content = '<span style="font-weight: 600; opacity: 0.9;">[' + m.messageType + ']</span> ' + content;
          }

          return '<div class="' + bubbleClass + '">' +
            '<div class="bubble-sender">' + senderLabel + '</div>' +
            '<div>' + (content || '<em style="opacity: 0.7;">Mensagem de mídia ou vazia</em>') + '</div>' +
            '<div class="bubble-time">' + timeText + '</div>' +
          '</div>';
        }).join('');
      }

      const isBanned = report.reported_is_banned;
      const banBtn = isBanned
        ? '<button class="btn-action btn-unban" onclick="toggleBan(\\'' + report.reported_user_id + '\\', false, \\'' + (report.reported_display_name || report.reported_username) + '\\'); closeMessageModal();">✅ Desbanir</button>'
        : '<button class="btn-action btn-ban" onclick="toggleBan(\\'' + report.reported_user_id + '\\', true, \\'' + (report.reported_display_name || report.reported_username) + '\\'); closeMessageModal();">🚫 Banir Usuário</button>';

      actionButtons.innerHTML = banBtn +
        '<button class="btn-action btn-resolve" onclick="updateReportStatus(\\'' + report.id + '\\', \\'resolved\\', \\'moderated\\'); closeMessageModal();">✔️ Marcar Resolvido</button>';

      modal.classList.add('active');
    }

    function closeMessageModal() {
      document.getElementById('messageModal').classList.remove('active');
    }

    async function toggleBan(userId, ban, userName) {
      const pin = getAdminPin();
      if (!pin) return;

      const confirmMsg = ban
        ? 'Tem certeza que deseja BANIR ' + (userName || 'este usuário') + '? O usuário será desconectado e impedido de usar o app.'
        : 'Deseja revogar o banimento de ' + (userName || 'este usuário') + '?';

      if (!confirm(confirmMsg)) return;

      try {
        const res = await fetch('/api/admin/users/' + userId + '/ban', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'x-admin-pin': pin
          },
          body: JSON.stringify({ banned: ban, reason: ban ? 'Violação dos Termos de Uso.' : undefined })
        });

        if (res.ok) {
          showToast(ban ? '🚫 Usuário banido com sucesso!' : '✅ Usuário desbanido com sucesso!');
          fetchAll();
        } else {
          showToast('Erro ao atualizar status do usuário.');
        }
      } catch (e) {
        showToast('Erro na requisição.');
      }
    }

    async function updateReportStatus(reportId, status, actionTaken) {
      const pin = getAdminPin();
      if (!pin) return;

      try {
        const res = await fetch('/api/reports/' + reportId, {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json',
            'x-admin-pin': pin
          },
          body: JSON.stringify({ status, action_taken: actionTaken })
        });

        if (res.ok) {
          showToast('Denúncia atualizada com sucesso!');
          fetchAll();
        } else {
          showToast('Erro ao atualizar denúncia.');
        }
      } catch (e) {
        showToast('Erro na requisição.');
      }
    }

    // Inicialização
    fetchAll();
    setInterval(() => {
      if (getAdminPin()) fetchAll();
    }, 5000);
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

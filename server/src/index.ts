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
  res.redirect('https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/apks/livrechat-v1.1.14-universal.apk');
});

// Health check
app.get('/health', async (req, res) => {
  let registeredFcmDevices = 0;
  try {
    registeredFcmDevices = await db.getFcmTokenCount();
  } catch (e) {}

  res.status(200).json({
    status: 'online',
    appName: 'LivreChat Server',
    version: '1.1.15',
    fcmInitialized: fcmService.isAvailable(),
    registeredFcmDevices,
    timestamp: new Date().toISOString()
  });
});

const CDN_APKS_BASE = 'https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/apks';

// App Versions API
app.get('/api/versions/check', (req, res) => {
  const clientVersionCode = parseInt((req.query.versionCode as string) || (req.headers['x-app-version-code'] as string) || '0', 10);
  const LATEST_CODE = 26;
  const MIN_SUPPORTED = 13;
  const isUpdateAvailable = clientVersionCode < LATEST_CODE;
  const isMandatory = clientVersionCode < MIN_SUPPORTED;

  res.status(200).json({
    clientVersionCode,
    latestVersion: '1.1.16',
    latestVersionCode: LATEST_CODE,
    minSupportedVersionCode: MIN_SUPPORTED,
    isUpdateAvailable,
    isMandatory,
    title: isMandatory ? 'Atualização Obrigatória' : 'Atualização Disponível',
    message: isMandatory
      ? 'Versões incompatíveis não podem se comunicar por segurança. Atualize para a versão mais recente.'
      : 'Nova versão 1.1.16: Correção definitiva de carregamento de fotos recebidas, toque longo em fotos/mídias com menu completo, tela de chamadas moderna com minimizar e criação de figurinhas corrigida!',
    apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.16-arm64.apk`,
    apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.16-armv7.apk`,
    apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.16-universal.apk`,
    downloadPortalUrl: '/download'
  });
});

app.get('/api/versions', (req, res) => {
  res.status(200).json({
    latest: {
      version: '1.1.16',
      versionCode: 26,
      minSupportedVersionCode: 13,
      releaseDate: '2026-10-17',
      mandatory: false,
      title: 'Atualização Disponível',
      message: 'Atualização 1.1.16: Correção de fotos recebidas, toque longo em mídias, tela de chamada moderna e melhorias no criador de figurinhas.',
      apkArm64Url: `${CDN_APKS_BASE}/livrechat-v1.1.16-arm64.apk`,
      apkArmv7Url: `${CDN_APKS_BASE}/livrechat-v1.1.16-armv7.apk`,
      apkUniversalUrl: `${CDN_APKS_BASE}/livrechat-v1.1.16-universal.apk`,
      downloadPortalUrl: '/download',
      sizeMbArm64: '24.2 MB',
      sizeMbUniversal: '30.4 MB'
    },
    versions: [
      {
        version: '1.1.16',
        versionCode: 26,
        releaseDate: '2026-10-17',
        isLatest: true,
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

// Attach WebSocket Manager
export const wsManager = new WebSocketManager(server);

// Start server
async function startServer() {
  await db.init();
  server.listen(CONFIG.PORT, '0.0.0.0', () => {
    console.log(`====================================================`);
    console.log(` LivreChat Server iniciado na porta ${CONFIG.PORT}`);
    console.log(` HTTP API: http://localhost:${CONFIG.PORT}`);
    console.log(` WebSocket: ws://localhost:${CONFIG.PORT}/ws`);
    console.log(` Modo: ${CONFIG.NODE_ENV}`);
    console.log(`====================================================`);
  });
}

startServer();

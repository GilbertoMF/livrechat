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
  res.redirect('/apks/livrechat-v1.1.0-arm64.apk');
});

// Health check
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'online',
    appName: 'LivreChat Server',
    version: '1.1.0',
    timestamp: new Date().toISOString()
  });
});

// App Versions API
app.get('/api/versions', (req, res) => {
  res.status(200).json({
    latest: {
      version: '1.1.0',
      versionCode: 10,
      releaseDate: '2026-10-16',
      apkArm64Url: '/apks/livrechat-v1.1.0-arm64.apk',
      apkUniversalUrl: '/apks/livrechat-v1.1.0-universal.apk',
      sizeMbArm64: '25.4 MB',
      sizeMbUniversal: '32.1 MB'
    },
    versions: [
      {
        version: '1.1.0',
        versionCode: 10,
        releaseDate: '2026-10-16',
        isLatest: true,
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

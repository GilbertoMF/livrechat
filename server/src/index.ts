import http from 'http';
import express from 'express';
import cors from 'cors';
import { CONFIG } from './config/env';
import { db } from './db/database';
import { authenticateToken, rateLimiter } from './middleware/auth';
import { AuthController } from './modules/auth/auth.controller';
import { UsersController } from './modules/users/users.controller';
import { MessagesController } from './modules/messages/messages.controller';
import { CallsController } from './modules/calls/calls.controller';
import { WebSocketManager } from './websocket/ws.server';

const app = express();
const server = http.createServer(app);

// Middlewares
app.use(cors());
app.use(express.json({ limit: '10mb' }));

// Health check
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'online',
    appName: 'LivreChat Server',
    version: '1.0.0',
    timestamp: new Date().toISOString()
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
app.delete('/api/users/contacts/:contactUserId', authenticateToken, UsersController.removeContact);

// Blocking & Reports
app.get('/api/users/blocked', authenticateToken, UsersController.getBlockedUsers);
app.post('/api/users/block', authenticateToken, UsersController.blockUser);
app.delete('/api/users/unblock/:blockedUserId', authenticateToken, UsersController.unblockUser);
app.post('/api/users/report', authenticateToken, UsersController.reportUser);

// Preferences
app.get('/api/users/preferences', authenticateToken, UsersController.getPreferences);
app.put('/api/users/preferences', authenticateToken, UsersController.updatePreferences);

// ==========================================
// MESSAGES & CONVERSATIONS ROUTES
// ==========================================
app.get('/api/messages/conversations', authenticateToken, MessagesController.getConversations);
app.post('/api/messages/conversations/direct', authenticateToken, MessagesController.getOrCreateConversation);
app.get('/api/messages/:conversationId', authenticateToken, MessagesController.getMessages);
app.post('/api/messages', authenticateToken, MessagesController.sendMessage);
app.put('/api/messages/:messageId', authenticateToken, MessagesController.editMessage);
app.delete('/api/messages/:messageId', authenticateToken, MessagesController.deleteMessage);
app.post('/api/messages/:messageId/read', authenticateToken, MessagesController.markAsRead);

// ==========================================
// CALLS ROUTES
// ==========================================
app.get('/api/calls/ice-servers', authenticateToken, CallsController.getIceServers);
app.get('/api/calls/history', authenticateToken, CallsController.getCallHistory);
app.post('/api/calls/record', authenticateToken, CallsController.recordCall);

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

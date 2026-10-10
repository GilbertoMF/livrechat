import { Server as HttpServer } from 'http';
import WebSocket, { WebSocketServer } from 'ws';
import url from 'url';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { CONFIG } from '../config/env';
import { db, Message } from '../db/database';

export type AuthenticatedSocket = WebSocket & {
  userId?: string;
  username?: string;
  isAlive?: boolean;
};

export class WebSocketManager {
  private wss: WebSocketServer;
  // Map userId to active sockets
  private userSockets: Map<string, Set<AuthenticatedSocket>> = new Map();

  constructor(server: HttpServer) {
    this.wss = new WebSocketServer({ server, path: '/ws' });
    this.setup();
  }

  private setup() {
    this.wss.on('connection', (ws: AuthenticatedSocket, req) => {
      // 1. Authenticate via token query param or authorization header
      const parsedUrl = url.parse(req.url || '', true);
      const token = (parsedUrl.query.token as string) || req.headers['sec-websocket-protocol'];

      if (!token) {
        ws.close(4001, 'Token de autenticação obrigatório.');
        return;
      }

      try {
        const decoded = jwt.verify(token, CONFIG.JWT_SECRET) as { userId: string; username: string };
        ws.userId = decoded.userId;
        ws.username = decoded.username;
        ws.isAlive = true;

        if (!this.userSockets.has(ws.userId)) {
          this.userSockets.set(ws.userId, new Set());
        }
        this.userSockets.get(ws.userId)!.add(ws);

        console.log(`[WS] Usuário conectado: ${ws.username} (${ws.userId})`);

        // Send connection ack
        ws.send(JSON.stringify({
          type: 'connection:ack',
          userId: ws.userId,
          timestamp: new Date().toISOString()
        }));

        // Handle Heartbeat
        ws.on('pong', () => {
          ws.isAlive = true;
        });

        // Handle Messages
        ws.on('message', async (raw: any) => {
          try {
            const data = JSON.parse(raw.toString());
            await this.handleIncomingEvent(ws, data);
          } catch (err) {
            console.error('[WS] Erro ao processar mensagem do socket:', err);
            ws.send(JSON.stringify({ type: 'error', message: 'Formato de payload inválido.' }));
          }
        });

        // Handle Disconnect
        ws.on('close', () => {
          if (ws.userId && this.userSockets.has(ws.userId)) {
            const set = this.userSockets.get(ws.userId)!;
            set.delete(ws);
            if (set.size === 0) {
              this.userSockets.delete(ws.userId);
            }
          }
          console.log(`[WS] Usuário desconectado: ${ws.username}`);
        });

      } catch (err) {
        ws.close(4003, 'Token inválido.');
      }
    });

    // Heartbeat Interval to clean dead sockets
    setInterval(() => {
      this.wss.clients.forEach((client: any) => {
        const authWs = client as AuthenticatedSocket;
        if (authWs.isAlive === false) {
          return authWs.terminate();
        }
        authWs.isAlive = false;
        authWs.ping();
      });
    }, 30000);
  }

  private async handleIncomingEvent(senderWs: AuthenticatedSocket, event: any) {
    const senderId = senderWs.userId!;

    switch (event.type) {
      // ==========================================
      // CHAT MESSAGES
      // ==========================================
      case 'chat:send': {
        const { conversationId, content, iv, messageType, replyToId, clientMessageId } = event;
        const msgId = clientMessageId || uuidv4();
        const now = new Date().toISOString();

        const messageRecord: Message = {
          id: msgId,
          conversation_id: conversationId,
          sender_id: senderId,
          content,
          iv,
          message_type: messageType || 'text',
          reply_to_id: replyToId,
          is_deleted: false,
          is_edited: false,
          created_at: now,
          updated_at: now
        };

        // Persist message
        await db.createMessage(messageRecord);

        // Acknowledge sender with "sent" status
        senderWs.send(JSON.stringify({
          type: 'chat:ack',
          messageId: msgId,
          clientMessageId,
          status: 'sent',
          timestamp: now
        }));

        // Broadcast to conversation participants
        const members = await db.getConversationMembers(conversationId);
        for (const memberId of members) {
          if (memberId !== senderId) {
            const delivered = this.sendToUser(memberId, {
              type: 'chat:message',
              message: messageRecord
            });

            // If recipient is currently online, mark delivered
            if (delivered) {
              await db.addReceipt({
                id: uuidv4(),
                message_id: msgId,
                user_id: memberId,
                status: 'delivered',
                timestamp: new Date().toISOString()
              });

              // Notify sender of delivery
              senderWs.send(JSON.stringify({
                type: 'chat:receipt',
                messageId: msgId,
                status: 'delivered',
                userId: memberId
              }));
            }
          }
        }
        break;
      }

      case 'chat:receipt': {
        const { messageId, status } = event; // 'delivered' or 'read'
        await db.addReceipt({
          id: uuidv4(),
          message_id: messageId,
          user_id: senderId,
          status,
          timestamp: new Date().toISOString()
        });

        // Forward receipt to original sender
        const msg = (await db.getMessages('', 1)).find(m => m.id === messageId) || null;
        // Broadcast to all connected sockets
        this.broadcast({
          type: 'chat:receipt',
          messageId,
          status,
          userId: senderId
        });
        break;
      }

      case 'chat:typing': {
        const { conversationId, isTyping } = event;
        const members = await db.getConversationMembers(conversationId);
        for (const memberId of members) {
          if (memberId !== senderId) {
            this.sendToUser(memberId, {
              type: 'chat:typing',
              conversationId,
              userId: senderId,
              username: senderWs.username,
              isTyping: !!isTyping
            });
          }
        }
        break;
      }

      // ==========================================
      // WEBRTC CALL SIGNALING
      // ==========================================
      case 'call:offer': {
        const { calleeId, sdp } = event;
        const callerProfile = await db.getProfile(senderId);

        // Check if callee blocked caller
        const blocked = await db.isBlocked(senderId, calleeId);
        if (blocked) {
          senderWs.send(JSON.stringify({
            type: 'call:busy',
            reason: 'Usuário indisponível.'
          }));
          return;
        }

        const online = this.sendToUser(calleeId, {
          type: 'call:incoming',
          callerId: senderId,
          callerUsername: senderWs.username,
          callerDisplayName: callerProfile?.display_name || senderWs.username,
          callerAvatarUrl: callerProfile?.avatar_url || null,
          sdp
        });

        if (!online) {
          senderWs.send(JSON.stringify({
            type: 'call:offline',
            message: 'O contato está offline no momento.'
          }));
        }
        break;
      }

      case 'call:answer': {
        const { callerId, sdp } = event;
        this.sendToUser(callerId, {
          type: 'call:answered',
          calleeId: senderId,
          sdp
        });
        break;
      }

      case 'call:ice_candidate': {
        const { targetUserId, candidate } = event;
        this.sendToUser(targetUserId, {
          type: 'call:ice_candidate',
          fromUserId: senderId,
          candidate
        });
        break;
      }

      case 'call:ringing': {
        const { callerId } = event;
        this.sendToUser(callerId, {
          type: 'call:ringing',
          fromUserId: senderId
        });
        break;
      }

      case 'call:reject': {
        const { callerId, reason } = event;
        this.sendToUser(callerId, {
          type: 'call:rejected',
          fromUserId: senderId,
          reason: reason || 'Chamada recusada.'
        });

        // Record missed/rejected call
        await db.recordCall({
          id: uuidv4(),
          caller_id: callerId,
          callee_id: senderId,
          status: 'rejected',
          started_at: new Date().toISOString(),
          ended_at: new Date().toISOString(),
          duration_seconds: 0
        });
        break;
      }

      case 'call:hangup': {
        const { targetUserId, durationSeconds } = event;
        this.sendToUser(targetUserId, {
          type: 'call:ended',
          fromUserId: senderId
        });

        if (targetUserId) {
          await db.recordCall({
            id: uuidv4(),
            caller_id: senderId,
            callee_id: targetUserId,
            status: 'completed',
            started_at: new Date(Date.now() - (durationSeconds || 0) * 1000).toISOString(),
            ended_at: new Date().toISOString(),
            duration_seconds: durationSeconds || 0
          });
        }
        break;
      }

      default:
        console.warn(`[WS] Tipo de evento desconhecido: ${event.type}`);
    }
  }

  public sendToUser(userId: string, payload: any): boolean {
    const sockets = this.userSockets.get(userId);
    if (!sockets || sockets.size === 0) {
      return false;
    }
    const message = JSON.stringify(payload);
    for (const ws of sockets) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(message);
      }
    }
    return true;
  }

  public broadcast(payload: any) {
    const message = JSON.stringify(payload);
    this.wss.clients.forEach((client: any) => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(message);
      }
    });
  }
}

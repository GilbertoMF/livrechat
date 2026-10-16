import { Server as HttpServer } from 'http';
import WebSocket, { WebSocketServer } from 'ws';
import url from 'url';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { CONFIG } from '../config/env';
import { db, Message } from '../db/database';
import { fcmService } from '../services/fcm.service';

export const APP_VERSION_RULES = {
  LATEST_VERSION_NAME: '1.1.5',
  LATEST_VERSION_CODE: 15,
  MIN_SUPPORTED_VERSION_CODE: 12,
  DOWNLOAD_PORTAL_URL: '/download'
};

export type AuthenticatedSocket = WebSocket & {
  userId?: string;
  username?: string;
  isAlive?: boolean;
  versionCode?: number;
  versionName?: string;
};

export class WebSocketManager {
  private static instance: WebSocketManager | null = null;
  public static getInstance(): WebSocketManager | null {
    return WebSocketManager.instance;
  }

  private wss: WebSocketServer;
  // Map userId to active sockets
  private userSockets: Map<string, Set<AuthenticatedSocket>> = new Map();

  constructor(server: HttpServer) {
    WebSocketManager.instance = this;
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

        const clientVersionCode = parseInt((parsedUrl.query.versionCode as string) || (req.headers['x-app-version-code'] as string) || '0', 10);
        const clientVersionName = (parsedUrl.query.version as string) || (req.headers['x-app-version'] as string) || '';
        ws.versionCode = clientVersionCode;
        ws.versionName = clientVersionName;

        if (ws.userId && clientVersionCode > 0) {
          db.setUserVersionCode(ws.userId, clientVersionCode);
        }

        if (!this.userSockets.has(ws.userId)) {
          this.userSockets.set(ws.userId, new Set());
        }
        const isFirstConnection = this.userSockets.get(ws.userId)!.size === 0;
        this.userSockets.get(ws.userId)!.add(ws);

        if (isFirstConnection && ws.userId) {
          this.broadcastUserStatus(ws.userId, 'online').catch(() => {});
        }

        console.log(`[WS] Usuário conectado: ${ws.username} (${ws.userId}) [v${clientVersionCode || 1}]`);
        if (ws.userId) {
          db.ensureUserExists(ws.userId, ws.username || 'usuario').catch(() => {});
        }

        // Send connection ack with version compatibility status
        const isOutdated = clientVersionCode > 0 && clientVersionCode < APP_VERSION_RULES.MIN_SUPPORTED_VERSION_CODE;
        ws.send(JSON.stringify({
          type: 'connection:ack',
          userId: ws.userId,
          versionStatus: {
            isUpdateAvailable: clientVersionCode < APP_VERSION_RULES.LATEST_VERSION_CODE,
            isMandatory: isOutdated,
            latestVersion: APP_VERSION_RULES.LATEST_VERSION_NAME,
            latestVersionCode: APP_VERSION_RULES.LATEST_VERSION_CODE,
            minSupportedVersionCode: APP_VERSION_RULES.MIN_SUPPORTED_VERSION_CODE,
            downloadUrl: APP_VERSION_RULES.DOWNLOAD_PORTAL_URL
          },
          timestamp: new Date().toISOString()
        }));

        if (isOutdated) {
          ws.send(JSON.stringify({
            type: 'system:version_status',
            message: 'Versão do aplicativo desatualizada. Versões diferentes não podem se comunicar por segurança. Atualize para a versão mais recente.',
            isMandatory: true,
            latestVersion: APP_VERSION_RULES.LATEST_VERSION_NAME,
            latestVersionCode: APP_VERSION_RULES.LATEST_VERSION_CODE,
            downloadUrl: APP_VERSION_RULES.DOWNLOAD_PORTAL_URL
          }));
        }

        // Deliver pending offline messages immediately
        (async () => {
          try {
            const pending = await db.getPendingMessagesForUser(ws.userId!);
            for (const msg of pending) {
              if (ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({
                  type: 'chat:message',
                  message: msg
                }));
                // Record delivered receipt
                await db.addReceipt({
                  id: uuidv4(),
                  message_id: msg.id,
                  user_id: ws.userId!,
                  status: 'delivered',
                  timestamp: new Date().toISOString()
                });
                // Notify sender that message was delivered
                this.sendToUser(msg.sender_id, {
                  type: 'chat:receipt',
                  messageId: msg.id,
                  status: 'delivered',
                  userId: ws.userId
                });
              }
            }
          } catch (err) {
            console.error('[WS] Erro ao entregar mensagens pendentes:', err);
          }
        })();

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
              this.broadcastUserStatus(ws.userId, 'offline').catch(() => {});
            }
          }
          console.log(`[WS] Usuário desconectado: ${ws.username}`);
        });

      } catch (err) {
        ws.close(4003, 'Token inválido.');
      }
    });

    // Heartbeat Interval to clean dead sockets quickly (10s)
    setInterval(() => {
      this.wss.clients.forEach((client: any) => {
        const authWs = client as AuthenticatedSocket;
        if (authWs.isAlive === false) {
          return authWs.terminate();
        }
        authWs.isAlive = false;
        authWs.ping();
      });
    }, 10000);
  }

  private async handleIncomingEvent(senderWs: AuthenticatedSocket, event: any) {
    const senderId = senderWs.userId!;

    switch (event.type) {
      // ==========================================
      // CHAT MESSAGES
      // ==========================================
      case 'chat:send': {
        const { conversationId, content, iv, messageType, replyToId, clientMessageId, targetUserId } = event;
        const msgId = clientMessageId || uuidv4();
        const now = new Date().toISOString();

        if (conversationId) {
          const conv = await db.getConversationById(conversationId);
          if (conv?.is_group && conv?.admin_only_message) {
            const isSenderAdmin = await db.isUserGroupAdmin(conversationId, senderId);
            if (!isSenderAdmin) {
              senderWs.send(JSON.stringify({
                type: 'chat:error',
                conversationId,
                clientMessageId,
                message: 'Apenas os administradores podem enviar mensagens neste grupo.'
              }));
              return;
            }
          }
        }

        let members = await db.getConversationMembers(conversationId);
        if (targetUserId && !members.includes(targetUserId)) {
          members.push(targetUserId);
        }
        members = Array.from(new Set(members));

        // Check if direct 1-on-1 communication is blocked in either direction
        let isDirectBlocked = false;
        const otherMember = targetUserId || (members.length <= 2 ? members.find(m => m !== senderId) : null);
        if (otherMember) {
          isDirectBlocked = await db.isBlocked(senderId, otherMember);
        }

        if (isDirectBlocked) {
          // Contact is blocked!
          // Give sender 1 gray tick ack (like WhatsApp: appears sent to sender, but never delivered to recipient and never saved in conversation)
          senderWs.send(JSON.stringify({
            type: 'chat:ack',
            messageId: msgId,
            clientMessageId,
            status: 'sent',
            timestamp: now
          }));
          break;
        }

        // Security & Version Compatibility Rule:
        // 1. Sender must be on a supported version (>= MIN_SUPPORTED_VERSION_CODE)
        if (senderWs.versionCode && senderWs.versionCode < APP_VERSION_RULES.MIN_SUPPORTED_VERSION_CODE) {
          senderWs.send(JSON.stringify({
            type: 'chat:error',
            clientMessageId,
            code: 'SENDER_VERSION_OUTDATED',
            message: 'Seu aplicativo está em uma versão antiga e desatualizada. Versões diferentes não podem se comunicar. Atualize o LivreChat no banner da tela principal para continuar enviando mensagens.'
          }));
          break;
        }

        // 2. Recipients cannot be on incompatible / outdated versions
        let hasIncompatibleRecipient = false;
        let incompatibleRecipientName = '';
        for (const memberId of members) {
          if (memberId !== senderId) {
            let recVersion = 0;
            const recSockets = this.userSockets.get(memberId);
            if (recSockets && recSockets.size > 0) {
              for (const s of recSockets) {
                if (s.versionCode) {
                  recVersion = s.versionCode;
                  break;
                }
              }
            } else {
              recVersion = await db.getUserVersionCode(memberId);
            }

            if (recVersion > 0 && recVersion < APP_VERSION_RULES.MIN_SUPPORTED_VERSION_CODE) {
              hasIncompatibleRecipient = true;
              const recProfile = await db.getProfile(memberId);
              incompatibleRecipientName = recProfile?.display_name || 'O contato';
              break;
            }
          }
        }

        if (hasIncompatibleRecipient) {
          senderWs.send(JSON.stringify({
            type: 'chat:error',
            clientMessageId,
            code: 'RECIPIENT_VERSION_INCOMPATIBLE',
            message: `${incompatibleRecipientName} está usando uma versão desatualizada do LivreChat. Versões diferentes não podem se comunicar por segurança. Peça para o contato atualizar o app.`
          }));
          break;
        }

        // 1. Instant acknowledgment to sender (0ms latency - 1 tick)
        senderWs.send(JSON.stringify({
          type: 'chat:ack',
          messageId: msgId,
          clientMessageId,
          status: 'sent',
          timestamp: now
        }));

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

        // 2. Immediately forward to online recipients without waiting for DB
        const forwardToRecipients = async () => {

          const senderProfile = await db.getProfile(senderId);
          const senderUser = await db.findUserById(senderId);
          const payloadWithSender = {
            ...messageRecord,
            sender_name: senderProfile?.display_name || senderUser?.username || '',
            sender_username: senderUser?.username || '',
            sender_avatar_url: senderProfile?.avatar_url || null
          };

          for (const memberId of members) {
            if (memberId !== senderId) {
              const blocked = await db.isBlocked(senderId, memberId);
              if (blocked) {
                continue;
              }

              const delivered = this.sendToUser(memberId, {
                type: 'chat:message',
                message: payloadWithSender
              });

              // Send FCM High Priority Push (wakes up receiver's Android phone even if killed)
              fcmService.sendNewMessagePush(memberId, {
                id: msgId,
                conversationId,
                senderId,
                senderName: senderProfile?.display_name || senderUser?.username || 'LivreChat',
                senderAvatar: senderProfile?.avatar_url || '',
                content,
                type: messageType || 'text',
                createdAt: now
              }).catch(() => {});

              // If recipient is currently online, mark delivered (2 ticks)
              if (delivered) {
                senderWs.send(JSON.stringify({
                  type: 'chat:receipt',
                  messageId: msgId,
                  status: 'delivered',
                  userId: memberId
                }));

                db.addReceipt({
                  id: uuidv4(),
                  message_id: msgId,
                  user_id: memberId,
                  status: 'delivered',
                  timestamp: now
                }).catch(() => {});
              }
            }
          }
        };

        forwardToRecipients().catch(err => console.error('[WS] Erro ao repassar mensagem:', err));

        // 3. Persist to database in background
        (async () => {
          if (targetUserId) {
            const blocked = await db.isBlocked(senderId, targetUserId);
            if (blocked) return;
          }
          await db.ensureUserExists(senderId, senderWs.username || 'usuario');
          await db.ensureConversationMember(conversationId, senderId);
          if (targetUserId) {
            await db.ensureConversationMember(conversationId, targetUserId);
          }
          await db.createMessage(messageRecord);
        })().catch(err => console.error('[WS] Erro ao persistir mensagem no banco:', err));
        break;
      }

      case 'chat:receipt': {
        const { messageId, status } = event; // 'delivered' or 'read'
        let finalStatus = status;

        if (status === 'read') {
          const prefs = await db.getPreferences(senderId);
          if (prefs && prefs.read_receipts_enabled === false) {
            finalStatus = 'delivered';
          }
        }

        await db.addReceipt({
          id: uuidv4(),
          message_id: messageId,
          user_id: senderId,
          status: finalStatus,
          timestamp: new Date().toISOString()
        });

        // Broadcast to all connected sockets
        this.broadcast({
          type: 'chat:receipt',
          messageId,
          status: finalStatus,
          userId: senderId
        });
        break;
      }

      case 'chat:typing': {
        const { conversationId, isTyping, targetUserId } = event;
        let members = await db.getConversationMembers(conversationId);
        if (targetUserId && !members.includes(targetUserId)) {
          members.push(targetUserId);
        }
        const profile = await db.getProfile(senderId);
        const displayName = profile?.display_name || senderWs.username || '';
        for (const memberId of members) {
          if (memberId !== senderId) {
            const blocked = await db.isBlocked(senderId, memberId);
            if (blocked) continue;
            this.sendToUser(memberId, {
              type: 'chat:typing',
              conversationId,
              userId: senderId,
              username: senderWs.username,
              displayName,
              isTyping: !!isTyping
            });
          }
        }
        break;
      }

      case 'chat:recording_audio':
      case 'chat:recording': {
        const { conversationId, isRecording, targetUserId } = event;
        let members = await db.getConversationMembers(conversationId);
        if (targetUserId && !members.includes(targetUserId)) {
          members.push(targetUserId);
        }
        const profile = await db.getProfile(senderId);
        const displayName = profile?.display_name || senderWs.username || '';
        for (const memberId of members) {
          if (memberId !== senderId) {
            const blocked = await db.isBlocked(senderId, memberId);
            if (blocked) continue;
            this.sendToUser(memberId, {
              type: 'chat:recording_audio',
              conversationId,
              userId: senderId,
              username: senderWs.username,
              displayName,
              isRecording: !!isRecording
            });
          }
        }
        break;
      }

      case 'chat:delete': {
        const { messageId, conversationId, targetUserId } = event;
        await db.deleteMessage(messageId, senderId);
        
        let members = await db.getConversationMembers(conversationId);
        if (targetUserId && !members.includes(targetUserId)) {
          members.push(targetUserId);
        }
        for (const memberId of members) {
          this.sendToUser(memberId, {
            type: 'chat:delete',
            messageId,
            conversationId,
            deleteForEveryone: true
          });
        }
        this.broadcast({
          type: 'chat:delete',
          messageId,
          conversationId,
          deleteForEveryone: true
        });
        break;
      }

      case 'chat:reaction': {
        const { messageId, conversationId, emoji, targetUserId } = event;
        const reactionsStr = await db.addOrToggleReaction(messageId, senderId, emoji);
        let members = await db.getConversationMembers(conversationId);
        if (targetUserId && !members.includes(targetUserId)) {
          members.push(targetUserId);
        }
        for (const memberId of members) {
          this.sendToUser(memberId, {
            type: 'chat:reaction',
            messageId,
            conversationId,
            userId: senderId,
            emoji,
            reactions: reactionsStr
          });
        }
        break;
      }

      case 'status:reaction': {
        const { statusId, authorId, emoji } = event;
        const profile = await db.getProfile(senderId);
        const user = await db.findUserById(senderId);
        const reaction = {
          user_id: senderId,
          username: user?.username || senderWs.username || 'usuario',
          display_name: profile?.display_name || user?.username || 'Usuário',
          avatar_url: profile?.avatar_url,
          emoji,
          created_at: new Date().toISOString()
        };
        await db.addStatusReaction(statusId, reaction);

        let targetAuthorId = authorId;
        if (!targetAuthorId) {
          const s = await db.getStatusById(statusId);
          targetAuthorId = s?.user_id;
        }

        if (targetAuthorId && targetAuthorId !== senderId) {
          this.sendToUser(targetAuthorId, {
            type: 'status:reaction',
            statusId,
            fromUserId: senderId,
            fromUsername: senderWs.username,
            displayName: reaction.display_name,
            avatarUrl: reaction.avatar_url,
            emoji,
            reaction
          });
        }
        break;
      }

      case 'status:view': {
        const { statusId, authorId } = event;
        const profile = await db.getProfile(senderId);
        const user = await db.findUserById(senderId);
        const view = {
          user_id: senderId,
          username: user?.username || senderWs.username || 'usuario',
          display_name: profile?.display_name || user?.username || 'Usuário',
          avatar_url: profile?.avatar_url,
          viewed_at: new Date().toISOString()
        };
        await db.addStatusView(statusId, view);

        let targetAuthorId = authorId;
        if (!targetAuthorId) {
          const s = await db.getStatusById(statusId);
          targetAuthorId = s?.user_id;
        }

        if (targetAuthorId && targetAuthorId !== senderId) {
          this.sendToUser(targetAuthorId, {
            type: 'status:view',
            statusId,
            fromUserId: senderId,
            fromUsername: senderWs.username,
            displayName: view.display_name,
            avatarUrl: view.avatar_url,
            view
          });
        }
        break;
      }

      case 'chat:clear': {
        const { conversationId } = event;
        await db.clearConversationMessages(conversationId);
        this.broadcast({
          type: 'chat:clear',
          conversationId
        });
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

        // Version compatibility check for calls
        if (senderWs.versionCode && senderWs.versionCode < APP_VERSION_RULES.MIN_SUPPORTED_VERSION_CODE) {
          senderWs.send(JSON.stringify({
            type: 'call:rejected',
            reason: 'Seu aplicativo está em uma versão antiga. Atualize para poder realizar chamadas.'
          }));
          return;
        }

        const calleeVersion = (await db.getUserVersionCode(calleeId)) || 0;
        if (calleeVersion > 0 && calleeVersion < APP_VERSION_RULES.MIN_SUPPORTED_VERSION_CODE) {
          senderWs.send(JSON.stringify({
            type: 'call:rejected',
            reason: 'O contato está em uma versão desatualizada do LivreChat. Ambos precisam atualizar para realizar chamadas.'
          }));
          return;
        }

        const callerDisplayName = callerProfile?.display_name || senderWs.username || '';
        const callerAvatarUrl = callerProfile?.avatar_url || '';

        const online = this.sendToUser(calleeId, {
          type: 'call:incoming',
          callerId: senderId,
          callerUsername: senderWs.username,
          callerDisplayName,
          callerAvatarUrl,
          sdp
        });

        // Always send High Priority FCM Push for incoming calls so recipient phone rings even if killed
        fcmService.sendIncomingCallPush(calleeId, {
          callerId: senderId,
          callerUsername: senderWs.username || '',
          callerDisplayName,
          callerAvatarUrl,
          sdp
        }).catch(() => {});

        if (!online) {
          senderWs.send(JSON.stringify({
            type: 'call:offline',
            message: 'O contato está offline no momento. Chamando via notificação push...'
          }));
        }
        break;
      }

      case 'call:answer': {
        const { callerId, sdp } = event;
        const startedAt = Number(event.startedAt) || Date.now();
        this.sendToUser(callerId, {
          type: 'call:answered',
          calleeId: senderId,
          sdp,
          startedAt
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

      case 'user:query_status': {
        const { targetUserId } = event;
        if (targetUserId) {
          const blocked = await db.isBlocked(senderId, targetUserId);
          if (blocked) {
            senderWs.send(JSON.stringify({
              type: 'user:status',
              userId: targetUserId,
              status: 'offline',
              timestamp: ''
            }));
            return;
          }
          const isOnline = this.isUserOnline(targetUserId);
          senderWs.send(JSON.stringify({
            type: 'user:status',
            userId: targetUserId,
            status: isOnline ? 'online' : 'offline',
            timestamp: new Date().toISOString()
          }));
        }
        break;
      }

      default:
        console.warn(`[WS] Tipo de evento desconhecido: ${event.type}`);
    }
  }

  public isUserOnline(userId: string): boolean {
    const sockets = this.userSockets.get(userId);
    return !!sockets && sockets.size > 0;
  }

  public async broadcastUserStatus(userId: string, status: 'online' | 'offline') {
    const onlinePayload = JSON.stringify({
      type: 'user:status',
      userId: userId,
      status: status,
      timestamp: status === 'online' ? new Date().toISOString() : ''
    });

    const offlinePayload = JSON.stringify({
      type: 'user:status',
      userId: userId,
      status: 'offline',
      timestamp: ''
    });

    for (const [targetUserId, sockets] of this.userSockets.entries()) {
      if (targetUserId === userId) continue;
      try {
        const blocked = await db.isBlocked(userId, targetUserId);
        const messageToSend = blocked ? offlinePayload : onlinePayload;
        for (const ws of sockets) {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(messageToSend);
          }
        }
      } catch (e) {
        for (const ws of sockets) {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(onlinePayload);
          }
        }
      }
    }
  }

  public sendToUser(userId: string, payload: any): boolean {
    const sockets = this.userSockets.get(userId);
    if (!sockets || sockets.size === 0) {
      return false;
    }
    const message = JSON.stringify(payload);
    let sent = false;
    for (const ws of sockets) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(message);
        sent = true;
      }
    }
    return sent;
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

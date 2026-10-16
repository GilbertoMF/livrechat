import { initializeApp, cert, App } from 'firebase-admin/app';
import { getMessaging, Messaging, MulticastMessage } from 'firebase-admin/messaging';
import fs from 'fs';
import path from 'path';
import { db } from '../db/database';

class FCMService {
  private app: App | null = null;
  private messaging: Messaging | null = null;
  private initialized = false;

  constructor() {
    this.init();
  }

  private init() {
    try {
      // 1. Try environment variable FIREBASE_SERVICE_ACCOUNT (raw JSON or base64)
      const envCreds = process.env.FIREBASE_SERVICE_ACCOUNT;
      if (envCreds) {
        let credsJson: any;
        try {
          credsJson = JSON.parse(envCreds);
        } catch {
          const decoded = Buffer.from(envCreds, 'base64').toString('utf-8');
          credsJson = JSON.parse(decoded);
        }

        this.app = initializeApp({
          credential: cert(credsJson),
        });
        this.messaging = getMessaging(this.app);
        this.initialized = true;
        console.log('[FCM] Firebase Admin inicializado com sucesso via variável de ambiente.');
        return;
      }

      // 2. Try local file in server root or project root
      const candidates = [
        path.join(process.cwd(), 'firebase-service-account.json'),
        path.join(__dirname, '../../firebase-service-account.json'),
        path.join(__dirname, '../firebase-service-account.json')
      ];

      const foundPath = candidates.find(p => fs.existsSync(p));
      if (foundPath) {
        const raw = fs.readFileSync(foundPath, 'utf-8');
        const creds = JSON.parse(raw);
        this.app = initializeApp({
          credential: cert(creds),
        });
        this.messaging = getMessaging(this.app);
        this.initialized = true;
        console.log(`[FCM] Firebase Admin inicializado com sucesso a partir de ${foundPath}`);
        return;
      }

      console.warn('[FCM] Nenhuma credencial do Firebase encontrada (firebase-service-account.json ou FIREBASE_SERVICE_ACCOUNT). Push notifications desativadas.');
    } catch (error) {
      console.error('[FCM] Erro ao inicializar Firebase Admin:', error);
      this.initialized = false;
    }
  }

  public isAvailable(): boolean {
    return this.initialized && this.messaging !== null;
  }

  /**
   * Envia mensagem de dados pura com Alta Prioridade para um ou mais dispositivos.
   * Não usa 'notification' para dar controle 100% ao app em segundo plano no Kotlin.
   */
  public async sendDataPush(tokens: string[], data: Record<string, string>): Promise<void> {
    if (!this.initialized || !this.messaging || tokens.length === 0) return;

    try {
      // Divide em lotes de 500 (limite do multicast do Firebase)
      const chunkSize = 500;
      for (let i = 0; i < tokens.length; i += chunkSize) {
        const batchTokens = tokens.slice(i, i + chunkSize);
        const message: MulticastMessage = {
          tokens: batchTokens,
          data: data,
          android: {
            priority: 'high',
            ttl: 24 * 3600 * 1000, // 24 horas de retenção
          },
        };

        const response = await this.messaging.sendEachForMulticast(message);

        // Limpa tokens inválidos ou revogados automaticamente do banco
        if (response.failureCount > 0) {
          response.responses.forEach((resp: any, idx: number) => {
            if (!resp.success) {
              const errCode = resp.error?.code;
              if (
                errCode === 'messaging/registration-token-not-registered' ||
                errCode === 'messaging/invalid-registration-token'
              ) {
                const badToken = batchTokens[idx];
                db.removeFcmToken(badToken).catch(() => {});
              }
            }
          });
        }
      }
    } catch (error) {
      console.error('[FCM] Erro ao enviar data push:', error);
    }
  }

  /**
   * Envia notificação de nova mensagem para todos os dispositivos de um usuário
   */
  public async sendNewMessagePush(
    recipientUserId: string,
    message: {
      id: string;
      conversationId: string;
      senderId: string;
      senderName: string;
      senderAvatar?: string;
      content: string;
      type: string;
      createdAt: string;
    }
  ) {
    if (!this.initialized) return;

    const tokens = await db.getFcmTokensByUserId(recipientUserId);
    if (!tokens || tokens.length === 0) return;

    await this.sendDataPush(tokens, {
      type: 'new_message',
      messageId: message.id,
      conversationId: message.conversationId,
      senderId: message.senderId,
      senderName: message.senderName || 'LivreChat',
      senderAvatar: message.senderAvatar || '',
      content: message.content || '',
      messageType: message.type || 'text',
      createdAt: message.createdAt || Date.now().toString(),
    });
  }

  /**
   * Envia notificação de chamada recebida de alta prioridade
   */
  public async sendIncomingCallPush(
    recipientUserId: string,
    callData: {
      callerId: string;
      callerUsername: string;
      callerDisplayName: string;
      callerAvatarUrl?: string;
      sdp: string;
    }
  ) {
    if (!this.initialized) return;

    const tokens = await db.getFcmTokensByUserId(recipientUserId);
    if (!tokens || tokens.length === 0) return;

    await this.sendDataPush(tokens, {
      type: 'incoming_call',
      callerId: callData.callerId,
      callerUsername: callData.callerUsername,
      callerDisplayName: callData.callerDisplayName || callData.callerUsername,
      callerAvatarUrl: callData.callerAvatarUrl || '',
      sdp: callData.sdp,
    });
  }
}

export const fcmService = new FCMService();

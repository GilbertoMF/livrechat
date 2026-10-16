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

      // 3. Fallback to built-in Base64 credentials for seamless cloud deploy
      const DEFAULT_B64 = "ewogICJ0eXBlIjogInNlcnZpY2VfYWNjb3VudCIsCiAgInByb2plY3RfaWQiOiAibGl2cmVjaGF0LWUzNzI3IiwKICAicHJpdmF0ZV9rZXlfaWQiOiAiZWUyMDFhOTQ1ZTMwNGY4MjIzZGQ5OTI3YTZiMDhmNTBmYzExNzNlZCIsCiAgInByaXZhdGVfa2V5IjogIi0tLS0tQkVHSU4gUFJJVkFURSBLRVktLS0tLVxuTUlJRXZRSUJBREFOQmdrcWhraUc5dzBCQVFFRkFBU0NCS2N3Z2dTakFnRUFBb0lCQVFEUVQvVzN3c002bGZFU1xuMUVsRlhYU011Zzhhb3lzYkVjMVgyRjJyaHdQZFJxdVk5ZVJSLzFFUWNVbkxSREJ5b044d2VaYUE1SGgxdTdTR1xuUW1Sd1QxQ3BqUXNRSldGTldHcWFDcG5WTFFnZ0NUcWo5OTFMeXhyNTM2WlFhTzVFMG5lYld5QjdTVXV2WFRWUVxuRzNRK2J0Qy9Dd2xMYXhsL3VzblZ3TTh1akY5QnFnNlAwaCtvR0syOC9XNXI0T1NzeGN4SmRMdXR3N1d6blpaeVxuaDdsNTFKWnN5ajVGd3Q5b01sQzFzSVFnNm1Sczd1UWhrd3FpTC9wTGVEeUcrV21QTnpFWGFaZCs3ZFBLRm1ybVxuaVJYVThkNktUdFFRQzNDRk9EQzlxcXlrbm1pWTNIY1I4Wjc1MlJwd3dTcXAreldLUlNGckFocFJZSW1hMWh5R1xuazQ5UzI1OWRBZ01CQUFFQ2dnRUFHaXFsSHJWZUVTYWpEdDZ3S29Dc0FuNWZwZFN2dGptRS9mOGRldzJkYUdiM1xuYUtJZ2tVVTlRVSs5dVlVQ0FmQWZ3UWJRNXpWcnhUdzBGN1pReUFQS2lkVy8rVUg4eURxdVAxWE1ENGxWenpKaFxubFpXdHpGYkJ0M2dHdSt5R1c0cEZnSFFRcnpQY2lEVnczRFJxcEdvR21pelFlTk1GODdPNnBUbFFQUlJnWmM2MFxuKy9IRE9aT3hobC95Skl5N2t3YjE3S2VtOVpicE5YM0VDa2V0c1ZieEMyTlZhNElFL3VtM2VrVXBoRC9CQXgyN1xuWWpXSmhud2pHV2tvV2NPQjAxRXA2d3paV2Z2TDZHd0EycGNnM2Y5U1BqS1ZzOXFGcUtMazZqd2daazNIait6RFxuWWh6MWlEb0t6dlYwby9hcjRhakF4dmk4SkVNOGRBbngxYjZyYUo0Y2dRS0JnUURuckxFV0YwTy9iS1FRaUpkWVxuSlYySG9lN0FDalJZUHJJa3o0NU5qbUcwOE16YTlHd3I4bThJSVhva2dQRU04ay91a2ZqMURaeU5kTjdhY0tpbVxuc2FBMXlnMVQvTS9YK0ZrKzZIWFNOdEFhRlVsaEJvL3pxUzNOYWZTcXBRQ3owaDRMVHlZVkZWVmo3V1RyUENMRlxuWmJEUHpFM3RnQU8wcHZhSXFpMi9PM3czZ1FLQmdRRG1MMDFBbU9Wd3U4Si9nd2VlZHNUcWdmeVZqQW44MVR4L1xuYjBDNmNvZmwrYlE4M1BtTW1TOEg3YUxHK3E4VXk0azY1TjE0OFd0WWs3TjVMZVk4SkhQdmdIM1FHSjk5eW9MNFxuL1hvb2lUQ1o0RzI2cTk5dkRDVjAzcHoyYkxzRzk3bzBtU3NvZFpTbTdQVlFHNE9BTjdjc1NkekswcVF6ZE00NFxuczRzcDRpSTEzUUtCZ0VhQ2Jwc1VsWS9yRnJ2WmRvWmpTT1lBeU5NMXg5eWJxRWcveTBXYmNlMUo1RzgzT0x6ZlxuV2grVllNRS9aUjNWbWJJNFJ4YjkvNG1sNmpWOVdEYnpsSmtLVjZnYUozL0puQS9qWEFpUW1pRjJIUkVrbUNkR1xuZENReWZUNTZYWTVkQXJ2YWFQSzl2WEZlVFBxeklMb0hTeHJUU1Z4aE83OTltVWJhMUhXQk96c0JBb0dBSUFPMlxuTCtUVURJRGd4VkZaRHU0ZjV5bmJFb1dlb2Y2YVluTDd3ckxKT2ZBbjVFc3JuaVYwQ0J0Q0I0QzROeUV6M2ZBVVxuNll4U1RNaUdMclYyanlqV3d4blE1YzkyNTB6eU9HVXd4MUNGWlBGMDhZQ1VHK0xKbHQ2YllCNnhTSC9OOHlEV1xuZ3RNUHNNUEdQclVFN2lGUnBhUkVSQ1ZWMXg1SXpGUVVaK2Zlc0RVQ2dZRUEyenFzM0J3SDNINUJqbnZVYkpMNlxuZXk3MEl3L2ZhNExsQVEwaTk3azZZbTMyTVAyMGw5L3NnVzAvcjEvYU5kcEdnZlV5dFNsSzBPSVl0bmJIclNGVlxuRkRWTGlUWmhlSnBXWmZFYjRrdzBRQTJSZmhTVDk0TzNXMG53dEFJcW01d2IvQXZyUk1NLzg2dUx5cWV5azRCdVxuR3VpSjY2ZGIzWFVVdmNyWFFHSU9SVms9XG4tLS0tLUVORCBQUklWQVRFIEtFWS0tLS0tXG4iLAogICJjbGllbnRfZW1haWwiOiAiZmlyZWJhc2UtYWRtaW5zZGstZmJzdmNAbGl2cmVjaGF0LWUzNzI3LmlhbS5nc2VydmljZWFjY291bnQuY29tIiwKICAiY2xpZW50X2lkIjogIjEwNjYzNTg0MDQ4Nzc2MzY1NTYyMSIsCiAgImF1dGhfdXJpIjogImh0dHBzOi8vYWNjb3VudHMuZ29vZ2xlLmNvbS9vL29hdXRoMi9hdXRoIiwKICAidG9rZW5fdXJpIjogImh0dHBzOi8vb2F1dGgyLmdvb2dsZWFwaXMuY29tL3Rva2VuIiwKICAiYXV0aF9wcm92aWRlcl94NTA5X2NlcnRfdXJsIjogImh0dHBzOi8vd3d3Lmdvb2dsZWFwaXMuY29tL29hdXRoMi92MS9jZXJ0cyIsCiAgImNsaWVudF94NTA5X2NlcnRfdXJsIjogImh0dHBzOi8vd3d3Lmdvb2dsZWFwaXMuY29tL3JvYm90L3YxL21ldGFkYXRhL3g1MDkvZmlyZWJhc2UtYWRtaW5zZGstZmJzdmMlNDBsaXZyZWNoYXQtZTM3MjcuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLAogICJ1bml2ZXJzZV9kb21haW4iOiAiZ29vZ2xlYXBpcy5jb20iCn0K";
      const decoded = Buffer.from(DEFAULT_B64, 'base64').toString('utf-8');
      const creds = JSON.parse(decoded);
      this.app = initializeApp({
        credential: cert(creds),
      });
      this.messaging = getMessaging(this.app);
      this.initialized = true;
      console.log('[FCM] Firebase Admin inicializado com sucesso via credencial padrão integrada.');
      return;
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

import { Response } from 'express';
import { AuthRequest } from '../../middleware/auth';
import { db, Message, MessageReceipt } from '../../db/database';
import { v4 as uuidv4 } from 'uuid';
import { WebSocketManager } from '../../websocket/ws.server';

export class MessagesController {
  public static async getConversations(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const list = await db.getConversationsForUser(userId);
      return res.status(200).json(list);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async getOrCreateConversation(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { targetUserId } = req.body;

      if (!targetUserId || targetUserId === userId) {
        return res.status(400).json({ error: 'Usuário de destino inválido.' });
      }

      const isBlocked = await db.isBlocked(userId, targetUserId);
      if (isBlocked) {
        return res.status(403).json({ error: 'Não é possível conversar com este usuário.' });
      }

      const conversationId = await db.getOrCreateDirectConversation(userId, targetUserId);
      const otherUser = await db.findUserById(targetUserId);
      const otherProfile = await db.getProfile(targetUserId);

      return res.status(200).json({
        conversationId,
        otherUser: {
          id: targetUserId,
          username: otherUser ? otherUser.username : '',
          displayName: otherProfile ? otherProfile.display_name : (otherUser ? otherUser.username : ''),
          avatarUrl: otherProfile?.avatar_url || null,
          publicKey: otherProfile?.public_key || null
        }
      });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async getMessages(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { conversationId } = req.params;
      const limit = parseInt(req.query.limit as string || '50', 10);
      const before = req.query.before as string | undefined;

      let isMember = await db.isUserInConversation(userId, conversationId);
      if (!isMember) {
        await db.ensureConversationMember(conversationId, userId);
      }

      const messages = await db.getMessages(conversationId, limit, before);

      // Attach receipts & consolidated status
      const enriched = await Promise.all(
        messages.map(async m => {
          const receipts = await db.getReceiptsForMessage(m.id);
          const hasRead = receipts.some(r => r.status === 'read');
          const hasDelivered = receipts.some(r => r.status === 'delivered');
          const status = hasRead ? 'read' : (hasDelivered ? 'delivered' : 'sent');
          return {
            ...m,
            status,
            receipts
          };
        })
      );

      return res.status(200).json(enriched);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async sendMessage(req: AuthRequest, res: Response) {
    try {
      const senderId = req.user!.userId;
      const { conversationId, content, iv, messageType, replyToId, clientMessageId, targetUserId } = req.body;

      await db.ensureConversationMember(conversationId, senderId);
      if (targetUserId) {
        await db.ensureConversationMember(conversationId, targetUserId);
      }

      const id = clientMessageId || uuidv4();
      const now = new Date().toISOString();

      const msg: Message = {
        id,
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

      const saved = await db.createMessage(msg);

      // Add "sent" receipt for sender
      await db.addReceipt({
        id: uuidv4(),
        message_id: id,
        user_id: senderId,
        status: 'sent',
        timestamp: now
      });

      return res.status(201).json(saved);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async editMessage(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { messageId } = req.params;
      const { content } = req.body;

      const updated = await db.editMessage(messageId, userId, content);
      if (!updated) {
        return res.status(404).json({ error: 'Mensagem não encontrada ou você não tem permissão para editá-la.' });
      }

      return res.status(200).json(updated);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async deleteMessage(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { messageId } = req.params;

      const success = await db.deleteMessage(messageId, userId);
      if (!success) {
        return res.status(404).json({ error: 'Mensagem não encontrada ou você não tem permissão.' });
      }

      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async markAsRead(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { messageId } = req.params;

      await db.addReceipt({
        id: uuidv4(),
        message_id: messageId,
        user_id: userId,
        status: 'read',
        timestamp: new Date().toISOString()
      });

      WebSocketManager.getInstance()?.broadcast({
        type: 'chat:receipt',
        messageId,
        status: 'read',
        userId
      });

      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async markConversationAsRead(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { conversationId } = req.params;

      await db.markConversationAsRead(conversationId, userId);

      WebSocketManager.getInstance()?.broadcast({
        type: 'chat:conversation_read',
        conversationId,
        userId
      });

      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async clearConversation(req: AuthRequest, res: Response) {
    try {
      const { conversationId } = req.params;
      await db.clearConversationMessages(conversationId);

      WebSocketManager.getInstance()?.broadcast({
        type: 'chat:clear',
        conversationId
      });

      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async deleteConversation(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { conversationId } = req.params;
      await db.deleteConversationForUser(conversationId, userId);

      WebSocketManager.getInstance()?.broadcast({
        type: 'chat:clear',
        conversationId
      });

      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }
}

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

      if (!targetUserId) {
        return res.status(400).json({ error: 'Usuário de destino inválido.' });
      }

      // Self-chat support ("Mensagens para mim / Note to Self")
      if (targetUserId === userId) {
        const conversationId = await db.getOrCreateDirectConversation(userId, userId);
        const meUser = await db.findUserById(userId);
        const meProfile = await db.getProfile(userId);

        return res.status(200).json({
          conversationId,
          otherUser: {
            id: userId,
            username: meUser ? meUser.username : '',
            displayName: (meProfile?.display_name || meUser?.username || 'Você') + ' (Você)',
            avatarUrl: meProfile?.avatar_url || null,
            publicKey: meProfile?.public_key || null
          }
        });
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

      // Filter out any messages sent by users that the requesting user has blocked
      const blockedList = await db.getBlockedUsers(userId);
      const blockedSenderIds = new Set(blockedList.map(b => b.blocked_id));
      const visibleMessages = messages.filter(m => !blockedSenderIds.has(m.sender_id));

      const userPrefs = await db.getPreferences(userId);
      const userReadReceiptsEnabled = userPrefs?.read_receipts_enabled !== false;

      // Attach receipts & consolidated status
      const enriched = await Promise.all(
        visibleMessages.map(async m => {
          const receipts = await db.getReceiptsForMessage(m.id);
          const otherReceipts = receipts.filter(r => r.user_id !== m.sender_id);

          let hasRead = false;
          if (userReadReceiptsEnabled) {
            for (const r of otherReceipts) {
              if (r.status === 'read') {
                const readerPrefs = await db.getPreferences(r.user_id);
                if (readerPrefs?.read_receipts_enabled !== false) {
                  hasRead = true;
                  break;
                }
              }
            }
          }
          const hasDelivered = otherReceipts.some(r => r.status === 'delivered');
          const status = hasRead ? 'read' : (hasDelivered ? 'delivered' : 'sent');
          const senderProfile = await db.getProfile(m.sender_id);
          const senderUser = await db.findUserById(m.sender_id);
          const senderName = senderProfile?.display_name || senderUser?.username || '';
          return {
            ...m,
            sender_name: senderName,
            sender_username: senderUser?.username || '',
            sender_avatar_url: senderProfile?.avatar_url || null,
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

      const conv = await db.getConversationById(conversationId);
      if (conv?.is_group && conv?.admin_only_message) {
        const isSenderAdmin = await db.isUserGroupAdmin(conversationId, senderId);
        if (!isSenderAdmin) {
          return res.status(403).json({ error: 'Apenas administradores podem enviar mensagens neste grupo.' });
        }
      }

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

      let members = await db.getConversationMembers(conversationId);
      const otherMember = targetUserId || (members.length <= 2 ? members.find(m => m !== senderId) : null);
      if (otherMember) {
        const isBlocked = await db.isBlocked(senderId, otherMember);
        if (isBlocked) {
          // Blocked: return 1 tick ack to sender without persisting for recipient
          return res.status(200).json({
            id,
            conversation_id: conversationId,
            sender_id: senderId,
            content,
            message_type: messageType || 'text',
            status: 'sent',
            created_at: now
          });
        }
      }

      const saved = await db.createMessage(msg);

      // Add "sent" receipt for sender
      await db.addReceipt({
        id: uuidv4(),
        message_id: id,
        user_id: senderId,
        status: 'sent',
        timestamp: now
      });

      const senderProfile = await db.getProfile(senderId);
      const senderUser = await db.findUserById(senderId);
      const senderName = senderProfile?.display_name || senderUser?.username || '';
      const payloadMessage = {
        ...saved,
        sender_name: senderName,
        sender_username: senderUser?.username || '',
        sender_avatar_url: senderProfile?.avatar_url || null
      };

      // Forward to online recipients via WebSocket
      const wsManager = WebSocketManager.getInstance();
      if (wsManager) {
        let members = await db.getConversationMembers(conversationId);
        if (targetUserId && !members.includes(targetUserId)) {
          members.push(targetUserId);
        }
        for (const memberId of members) {
          if (memberId !== senderId) {
            const blocked = await db.isBlocked(senderId, memberId);
            if (blocked) continue;
            wsManager.sendToUser(memberId, {
              type: 'chat:message',
              message: payloadMessage
            });
          }
        }
      }

      return res.status(201).json(payloadMessage);
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

      const existingMsg = await db.getMessageById(messageId);
      const conversationId = existingMsg?.conversation_id || '';

      const success = await db.deleteMessage(messageId, userId);
      if (!success) {
        return res.status(404).json({ error: 'Mensagem não encontrada ou você não tem permissão.' });
      }

      WebSocketManager.getInstance()?.broadcast({
        type: 'chat:delete',
        messageId,
        conversationId,
        deleteForEveryone: true
      });

      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async markAsRead(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { messageId } = req.params;

      const prefs = await db.getPreferences(userId);
      if (prefs && prefs.read_receipts_enabled === false) {
        return res.status(200).json({ success: true, read_receipts: false });
      }

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

      const prefs = await db.getPreferences(userId);
      if (prefs && prefs.read_receipts_enabled === false) {
        return res.status(200).json({ success: true, read_receipts: false });
      }

      await db.markConversationAsRead(conversationId, userId);

      const wsManager = WebSocketManager.getInstance();
      if (wsManager) {
        const members = await db.getConversationMembers(conversationId);
        for (const memberId of members) {
          if (memberId !== userId) {
            wsManager.sendToUser(memberId, {
              type: 'chat:conversation_read',
              conversationId,
              userId
            });
          }
        }
      }

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

  public static async createGroup(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { title, memberIds, avatarUrl } = req.body;

      if (!title || !title.trim()) {
        return res.status(400).json({ error: 'Nome do grupo é obrigatório.' });
      }

      const conv = await db.createGroupConversation(
        title.trim(),
        userId,
        Array.isArray(memberIds) ? memberIds : [],
        avatarUrl
      );

      // Notify members via WS
      const wsManager = WebSocketManager.getInstance();
      if (wsManager) {
        const allMembers = Array.from(new Set([userId, ...(Array.isArray(memberIds) ? memberIds : [])]));
        for (const mId of allMembers) {
          wsManager.sendToUser(mId, {
            type: 'group:created',
            conversation: conv
          });
        }
      }

      return res.status(201).json({
        conversationId: conv.id,
        isGroup: true,
        title: conv.title,
        avatarUrl: conv.avatar_url || null,
        createdBy: conv.created_by,
        createdAt: conv.created_at,
        updatedAt: conv.updated_at
      });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async updateGroup(req: AuthRequest, res: Response) {
    try {
      const { conversationId } = req.params;
      const { title, avatarUrl } = req.body;
      const callerId = req.user!.userId;

      const conv = await db.getConversationById(conversationId);
      if (!conv) {
        return res.status(404).json({ error: 'Grupo não encontrado.' });
      }

      if (conv.admin_only_edit_info) {
        const isCallerAdmin = await db.isUserGroupAdmin(conversationId, callerId);
        if (!isCallerAdmin) {
          return res.status(403).json({ error: 'Apenas administradores podem alterar os dados do grupo.' });
        }
      }

      const updated = await db.updateGroupConversation(conversationId, title, avatarUrl);
      if (!updated) {
        return res.status(404).json({ error: 'Grupo não encontrado.' });
      }

      // Notify members via WS
      const wsManager = WebSocketManager.getInstance();
      if (wsManager) {
        const members = await db.getConversationMembers(conversationId);
        for (const mId of members) {
          wsManager.sendToUser(mId, {
            type: 'group:updated',
            conversationId,
            title: updated.title,
            avatarUrl: updated.avatar_url || null
          });
        }
      }

      return res.status(200).json({ success: true, conversation: updated });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async getConversationMembers(req: AuthRequest, res: Response) {
    try {
      const { conversationId } = req.params;
      const memberDetails = await db.getConversationMemberDetails(conversationId);
      const members = [];
      for (const md of memberDetails) {
        const u = await db.findUserById(md.userId);
        const p = await db.getProfile(md.userId);
        if (u) {
          members.push({
            id: u.id,
            username: u.username,
            displayName: p?.display_name || u.username,
            avatarUrl: p?.avatar_url || null,
            bio: p?.bio || null,
            isAdmin: md.isAdmin
          });
        }
      }
      return res.status(200).json(members);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async setGroupMemberAdmin(req: AuthRequest, res: Response) {
    try {
      const { conversationId, userId } = req.params;
      const { isAdmin } = req.body;
      const callerId = req.user!.userId;

      const isCallerAdmin = await db.isUserGroupAdmin(conversationId, callerId);
      if (!isCallerAdmin) {
        return res.status(403).json({ error: 'Apenas administradores podem alterar permissões de membros.' });
      }

      await db.setGroupMemberAdmin(conversationId, userId, !!isAdmin);

      const wsManager = WebSocketManager.getInstance();
      if (wsManager) {
        const members = await db.getConversationMembers(conversationId);
        for (const mId of members) {
          wsManager.sendToUser(mId, {
            type: 'group:admins_updated',
            conversationId,
            targetUserId: userId,
            isAdmin: !!isAdmin
          });
        }
      }

      return res.status(200).json({ success: true, conversationId, userId, isAdmin: !!isAdmin });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async updateGroupPermissions(req: AuthRequest, res: Response) {
    try {
      const { conversationId } = req.params;
      const { adminOnlyMessage, adminOnlyEditInfo, adminOnlyAddMembers } = req.body;
      const callerId = req.user!.userId;

      const isCallerAdmin = await db.isUserGroupAdmin(conversationId, callerId);
      if (!isCallerAdmin) {
        return res.status(403).json({ error: 'Apenas administradores podem alterar configurações do grupo.' });
      }

      const updated = await db.updateGroupPermissions(conversationId, {
        adminOnlyMessage,
        adminOnlyEditInfo,
        adminOnlyAddMembers
      });

      const wsManager = WebSocketManager.getInstance();
      if (wsManager) {
        const members = await db.getConversationMembers(conversationId);
        for (const mId of members) {
          wsManager.sendToUser(mId, {
            type: 'group:permissions_updated',
            conversationId,
            permissions: {
              adminOnlyMessage: updated?.admin_only_message,
              adminOnlyEditInfo: updated?.admin_only_edit_info,
              adminOnlyAddMembers: updated?.admin_only_add_members
            }
          });
        }
      }

      return res.status(200).json({ success: true, conversation: updated });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async addConversationMembers(req: AuthRequest, res: Response) {
    try {
      const { conversationId } = req.params;
      const { memberIds } = req.body;
      const callerId = req.user!.userId;

      if (!Array.isArray(memberIds) || memberIds.length === 0) {
        return res.status(400).json({ error: 'Nenhum membro informado.' });
      }

      const conv = await db.getConversationById(conversationId);
      if (conv?.admin_only_add_members) {
        const isCallerAdmin = await db.isUserGroupAdmin(conversationId, callerId);
        if (!isCallerAdmin) {
          return res.status(403).json({ error: 'Apenas administradores podem adicionar participantes a este grupo.' });
        }
      }

      const updatedMemberIds = await db.addConversationMembers(conversationId, memberIds);

      // WebSocket notification
      const wsManager = WebSocketManager.getInstance();
      if (wsManager) {
        for (const mId of updatedMemberIds) {
          wsManager.sendToUser(mId, {
            type: 'group:members_updated',
            conversationId,
            memberIds: updatedMemberIds
          });
        }
      }

      return res.status(200).json({ success: true, memberIds: updatedMemberIds });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async removeConversationMember(req: AuthRequest, res: Response) {
    try {
      const { conversationId, memberId } = req.params;
      const callerId = req.user!.userId;

      if (memberId !== callerId) {
        const isCallerAdmin = await db.isUserGroupAdmin(conversationId, callerId);
        if (!isCallerAdmin) {
          return res.status(403).json({ error: 'Apenas administradores podem remover participantes do grupo.' });
        }
      }

      await db.removeConversationMember(conversationId, memberId);

      const wsManager = WebSocketManager.getInstance();
      if (wsManager) {
        const remaining = await db.getConversationMembers(conversationId);
        for (const mId of remaining) {
          wsManager.sendToUser(mId, {
            type: 'group:members_updated',
            conversationId,
            memberIds: remaining
          });
        }
        wsManager.sendToUser(memberId, {
          type: 'group:removed',
          conversationId
        });
      }

      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }
}

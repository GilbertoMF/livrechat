import { Request, Response } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { db, StatusRecord } from '../../db/database';
import { wsManager } from '../../index';

export class StatusController {
  public static async createStatus(req: Request, res: Response) {
    try {
      const userId = (req as any).user?.userId;
      const { contentType, content, caption, backgroundColor } = req.body;

      if (!userId || !contentType || !content) {
        return res.status(400).json({ error: 'contentType e content são obrigatórios' });
      }

      const user = await db.findUserById(userId);
      const profile = await db.getProfile(userId);
      const now = new Date();
      const expiresAt = new Date(now.getTime() + 24 * 60 * 60 * 1000); // 24 hours

      if (req.body.avatarUrl && (!profile?.avatar_url || profile.avatar_url !== req.body.avatarUrl)) {
        await db.upsertProfile({
          user_id: userId,
          display_name: req.body.displayName || profile?.display_name || user?.username || 'Usuário',
          avatar_url: req.body.avatarUrl,
          bio: profile?.bio || '',
          public_key: profile?.public_key || '',
          updated_at: now.toISOString()
        }).catch(() => {});
      }

      const status: StatusRecord = {
        id: uuidv4(),
        user_id: userId,
        display_name: req.body.displayName || profile?.display_name || user?.username || 'Usuário',
        username: user?.username || 'usuario',
        avatar_url: req.body.avatarUrl || profile?.avatar_url || undefined,
        content_type: contentType,
        content,
        caption: caption || undefined,
        background_color: backgroundColor || undefined,
        created_at: now.toISOString(),
        expires_at: expiresAt.toISOString()
      };

      const created = await db.createStatus(status);

      // Notifica os contatos e o próprio usuário
      try {
        const contacts = await db.getContacts(userId);
        const targetUserIds = new Set<string>([userId, ...contacts.map(c => c.contact_user_id)]);
        for (const targetId of targetUserIds) {
          wsManager?.sendToUser(targetId, {
            type: 'status:new',
            status: created
          });
        }
      } catch (e) {}

      res.status(201).json(created);
    } catch (err: any) {
      console.error('[StatusController] createStatus error:', err);
      res.status(500).json({ error: 'Falha ao publicar status' });
    }
  }

  public static async getStatuses(req: Request, res: Response) {
    try {
      const userId = (req as any).user?.userId;
      if (!userId) {
        return res.status(401).json({ error: 'Não autorizado' });
      }

      const statuses = await db.getActiveStatuses(userId);
      res.status(200).json(statuses);
    } catch (err: any) {
      console.error('[StatusController] getStatuses error:', err);
      res.status(500).json({ error: 'Falha ao buscar status' });
    }
  }

  public static async deleteStatus(req: Request, res: Response) {
    try {
      const userId = (req as any).user?.userId;
      const { statusId } = req.params;

      if (!userId || !statusId) {
        return res.status(400).json({ error: 'statusId é obrigatório' });
      }

      await db.deleteStatus(statusId, userId);

      // Notifica remoção imediatamente
      try {
        wsManager?.broadcast({
          type: 'status:deleted',
          statusId
        });
      } catch (e) {}

      res.status(200).json({ success: true, message: 'Status apagado com sucesso' });
    } catch (err: any) {
      console.error('[StatusController] deleteStatus error:', err);
      res.status(500).json({ error: 'Falha ao apagar status' });
    }
  }

  public static async viewStatus(req: Request, res: Response) {
    try {
      const userId = (req as any).user?.userId;
      const { statusId } = req.params;
      if (!userId || !statusId) return res.status(400).json({ error: 'Dados incompletos' });

      const profile = await db.getProfile(userId);
      const user = await db.findUserById(userId);
      const view = {
        user_id: userId,
        username: user?.username || 'usuario',
        display_name: profile?.display_name || user?.username || 'Usuário',
        avatar_url: profile?.avatar_url,
        viewed_at: new Date().toISOString()
      };
      await db.addStatusView(statusId, view);

      try {
        const status = await db.getStatusById(statusId);
        const authorId = status?.user_id;
        if (authorId && authorId !== userId) {
          wsManager.sendToUser(authorId, {
            type: 'status:view',
            statusId,
            fromUserId: userId,
            fromUsername: view.username,
            displayName: view.display_name,
            avatarUrl: view.avatar_url,
            view
          });
        }
      } catch (wsErr) {}

      res.status(200).json({ success: true, view });
    } catch (e: any) {
      res.status(500).json({ error: e.message });
    }
  }

  public static async reactStatus(req: Request, res: Response) {
    try {
      const userId = (req as any).user?.userId;
      const { statusId } = req.params;
      const { emoji } = req.body;
      if (!userId || !statusId || !emoji) return res.status(400).json({ error: 'Dados incompletos' });

      const profile = await db.getProfile(userId);
      const user = await db.findUserById(userId);
      const reaction = {
        user_id: userId,
        username: user?.username || 'usuario',
        display_name: profile?.display_name || user?.username || 'Usuário',
        avatar_url: profile?.avatar_url,
        emoji,
        created_at: new Date().toISOString()
      };
      await db.addStatusReaction(statusId, reaction);

      try {
        const status = await db.getStatusById(statusId);
        const authorId = status?.user_id;
        if (authorId && authorId !== userId) {
          wsManager.sendToUser(authorId, {
            type: 'status:reaction',
            statusId,
            fromUserId: userId,
            fromUsername: reaction.username,
            displayName: reaction.display_name,
            avatarUrl: reaction.avatar_url,
            emoji,
            reaction
          });
        }
      } catch (wsErr) {}

      res.status(200).json({ success: true, reaction });
    } catch (e: any) {
      res.status(500).json({ error: e.message });
    }
  }
}

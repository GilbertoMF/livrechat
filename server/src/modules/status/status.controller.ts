import { Request, Response } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { db, StatusRecord } from '../../db/database';

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

      const status: StatusRecord = {
        id: uuidv4(),
        user_id: userId,
        display_name: profile?.display_name || user?.username || 'Usuário',
        username: user?.username || 'usuario',
        avatar_url: profile?.avatar_url,
        content_type: contentType,
        content,
        caption: caption || undefined,
        background_color: backgroundColor || undefined,
        created_at: now.toISOString(),
        expires_at: expiresAt.toISOString()
      };

      const created = await db.createStatus(status);
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
      res.status(200).json({ success: true, message: 'Status apagado com sucesso' });
    } catch (err: any) {
      console.error('[StatusController] deleteStatus error:', err);
      res.status(500).json({ error: 'Falha ao apagar status' });
    }
  }
}

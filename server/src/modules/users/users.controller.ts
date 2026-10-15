import { Response } from 'express';
import { AuthRequest } from '../../middleware/auth';
import { db } from '../../db/database';
import { v4 as uuidv4 } from 'uuid';

export class UsersController {
  public static async getProfile(req: AuthRequest, res: Response) {
    try {
      const currentUserId = req.user!.userId;
      const targetUserId = req.params.userId || currentUserId;

      const user = await db.findUserById(targetUserId);
      if (!user) {
        return res.status(404).json({ error: 'Usuário não encontrado.' });
      }

      const profile = await db.getProfile(targetUserId);

      return res.status(200).json({
        id: user.id,
        username: user.username,
        displayName: profile?.display_name || user.username,
        avatarUrl: profile?.avatar_url || null,
        bio: profile?.bio || '',
        publicKey: profile?.public_key || null,
        createdAt: user.created_at
      });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async updateProfile(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { displayName, bio, avatarUrl, publicKey } = req.body;

      const profile = await db.upsertProfile({
        user_id: userId,
        display_name: displayName || req.user!.username,
        bio: bio || '',
        avatar_url: avatarUrl,
        public_key: publicKey,
        updated_at: new Date().toISOString()
      });

      return res.status(200).json(profile);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async searchUsers(req: AuthRequest, res: Response) {
    try {
      const currentUserId = req.user!.userId;
      const query = (req.query.q as string || '').trim().toLowerCase();

      if (!query || query.length < 2) {
        return res.status(400).json({ error: 'Termo de busca deve ter no mínimo 2 caracteres.' });
      }

      // Search by exact ID or username substring
      const user = await db.findUserByUsername(query);
      const results = [];

      if (user && user.id !== currentUserId) {
        const isBlocked = await db.isBlocked(currentUserId, user.id);
        if (!isBlocked) {
          const profile = await db.getProfile(user.id);
          results.push({
            id: user.id,
            username: user.username,
            displayName: profile?.display_name || user.username,
            avatarUrl: profile?.avatar_url || null,
            bio: profile?.bio || '',
            publicKey: profile?.public_key || null
          });
        }
      } else {
        // If query looks like a UUID, search by ID
        const byId = await db.findUserById(query);
        if (byId && byId.id !== currentUserId) {
          const isBlocked = await db.isBlocked(currentUserId, byId.id);
          if (!isBlocked) {
            const profile = await db.getProfile(byId.id);
            results.push({
              id: byId.id,
              username: byId.username,
              displayName: profile?.display_name || byId.username,
              avatarUrl: profile?.avatar_url || null,
              bio: profile?.bio || '',
              publicKey: profile?.public_key || null
            });
          }
        }
      }

      return res.status(200).json(results);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async getContacts(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const contacts = await db.getContacts(userId);
      const detailed = [];

      for (const c of contacts) {
        const u = await db.findUserById(c.contact_user_id);
        if (!u) continue;
        const p = await db.getProfile(u.id);
        detailed.push({
          id: c.id,
          contactUserId: u.id,
          username: u.username,
          displayName: c.nickname || p?.display_name || u.username,
          avatarUrl: p?.avatar_url || null,
          publicKey: p?.public_key || null,
          bio: p?.bio || ''
        });
      }

      return res.status(200).json(detailed);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async addContact(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { contactUserId, nickname } = req.body;

      if (!contactUserId || contactUserId === userId) {
        return res.status(400).json({ error: 'ID de contato inválido.' });
      }

      const target = await db.findUserById(contactUserId);
      if (!target) {
        return res.status(404).json({ error: 'Contato não encontrado.' });
      }

      const contact = await db.addContact({
        id: uuidv4(),
        user_id: userId,
        contact_user_id: contactUserId,
        nickname,
        created_at: new Date().toISOString()
      });

      return res.status(201).json(contact);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async removeContact(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { contactUserId } = req.params;
      await db.removeContact(userId, contactUserId);
      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async updateContact(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { contactUserId } = req.params;
      const { nickname } = req.body;
      await db.updateContactNickname(userId, contactUserId, nickname || '');
      return res.status(200).json({ success: true, nickname });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async blockUser(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const blockedUserId = req.body.blockedUserId || req.body.targetUserId;
      if (!blockedUserId) {
        return res.status(400).json({ error: 'ID do usuário a ser bloqueado é obrigatório.' });
      }
      const result = await db.blockUser(userId, blockedUserId);
      return res.status(200).json(result);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async unblockUser(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { blockedUserId } = req.params;
      await db.unblockUser(userId, blockedUserId);
      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async getBlockedUsers(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const blocked = await db.getBlockedUsers(userId);
      return res.status(200).json(blocked);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async reportUser(req: AuthRequest, res: Response) {
    try {
      const reporterId = req.user!.userId;
      const { reportedUserId, reason, details } = req.body;
      const report = await db.createReport({
        id: uuidv4(),
        reporter_id: reporterId,
        reported_user_id: reportedUserId,
        reason,
        details,
        created_at: new Date().toISOString()
      });
      return res.status(201).json(report);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async getPreferences(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const prefs = await db.getPreferences(userId);
      return res.status(200).json(prefs);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async updatePreferences(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const updated = await db.updatePreferences(userId, req.body);
      return res.status(200).json(updated);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }
}

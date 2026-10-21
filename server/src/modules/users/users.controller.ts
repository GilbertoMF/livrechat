import { Response } from 'express';
import { AuthRequest } from '../../middleware/auth';
import { db } from '../../db/database';
import { v4 as uuidv4 } from 'uuid';
import { WebSocketManager } from '../../websocket/ws.server';

function extractPhoneVariants(raw: string): string[] {
  if (!raw) return [];
  const digits = raw.replace(/\D/g, '');
  if (!digits || digits.length < 8) return [];

  const variants = new Set<string>();
  variants.add(digits);

  // If starts with 0 (e.g. 011987654321)
  if (digits.startsWith('0') && digits.length > 9) {
    variants.add(digits.substring(1));
  }

  // Brazilian numbers:
  if (digits.startsWith('55') && (digits.length === 12 || digits.length === 13)) {
    const without55 = digits.substring(2);
    variants.add(without55);
    if (without55.length === 11 && without55[2] === '9') {
      const without9 = without55.substring(0, 2) + without55.substring(3);
      variants.add(without9);
      variants.add('55' + without9);
    }
  } else if (digits.length === 11) {
    variants.add('55' + digits);
    if (digits[2] === '9') {
      const without9 = digits.substring(0, 2) + digits.substring(3);
      variants.add(without9);
      variants.add('55' + without9);
    }
  } else if (digits.length === 10) {
    variants.add('55' + digits);
    const with9 = digits.substring(0, 2) + '9' + digits.substring(2);
    variants.add(with9);
    variants.add('55' + with9);
  }

  return Array.from(variants);
}

interface BurnerRecord {
  userId: string;
  expiresAt: number;
}
const activeBurnerIds = new Map<string, BurnerRecord>();
const userToBurnerMap = new Map<string, string>();

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
        phoneNumber: profile?.phone_number || null,
        createdAt: user.created_at
      });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async updateProfile(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { displayName, bio, avatarUrl, publicKey, phoneNumber } = req.body;

      const existing = await db.getProfile(userId);
      const effectiveDisplayName = (displayName !== undefined && displayName !== null && displayName.trim().length > 0)
        ? displayName.trim()
        : (existing?.display_name || req.user!.username);

      const profile = await db.upsertProfile({
        user_id: userId,
        display_name: effectiveDisplayName,
        bio: bio !== undefined ? bio : (existing?.bio || ''),
        avatar_url: avatarUrl !== undefined ? avatarUrl : existing?.avatar_url,
        public_key: publicKey !== undefined ? publicKey : existing?.public_key,
        phone_number: phoneNumber !== undefined ? phoneNumber : existing?.phone_number,
        updated_at: new Date().toISOString()
      });

      return res.status(200).json({
        id: userId,
        userId: userId,
        username: req.user!.username,
        displayName: profile.display_name,
        display_name: profile.display_name,
        bio: profile.bio,
        avatarUrl: profile.avatar_url,
        avatar_url: profile.avatar_url,
        publicKey: profile.public_key,
        phoneNumber: profile.phone_number
      });
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
        // Search by exact ID or Burner ID
        let byId = await db.findUserById(query);
        if (!byId) {
          const burner = activeBurnerIds.get(query);
          if (burner && burner.expiresAt > Date.now()) {
            byId = await db.findUserById(burner.userId);
          }
        }
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

  public static async syncContacts(req: AuthRequest, res: Response) {
    try {
      const currentUserId = req.user!.userId;
      const rawContacts = req.body.contacts || [];
      const rawPhones = req.body.phones || [];

      const inputItems: Array<{ name: string; phones: string[] }> = [];

      if (Array.isArray(rawContacts)) {
        for (const item of rawContacts) {
          if (typeof item === 'string') {
            inputItems.push({ name: '', phones: [item] });
          } else if (item && typeof item === 'object') {
            const name = (item.name || item.displayName || '').toString().trim();
            const phones = Array.isArray(item.phones)
              ? item.phones.map((p: any) => String(p).trim()).filter(Boolean)
              : (item.phone || item.phoneNumber ? [String(item.phone || item.phoneNumber).trim()] : []);
            if (phones.length > 0 || name.length > 0) {
              inputItems.push({ name, phones });
            }
          }
        }
      }

      if (Array.isArray(rawPhones)) {
        for (const p of rawPhones) {
          if (typeof p === 'string' && p.trim()) {
            inputItems.push({ name: '', phones: [p.trim()] });
          }
        }
      }

      if (inputItems.length === 0) {
        return res.status(200).json({ matchedCount: 0, contacts: [] });
      }

      const allUsers = await db.getAllUsersWithProfiles();
      const existingContacts = await db.getContacts(currentUserId);
      const existingContactIds = new Set(existingContacts.map(c => c.contact_user_id));

      const matchedResults: any[] = [];
      const matchedUserIds = new Set<string>();

      for (const item of inputItems) {
        const contactVariants = new Set<string>();
        for (const ph of item.phones) {
          extractPhoneVariants(ph).forEach(v => contactVariants.add(v));
        }

        for (const { user, profile } of allUsers) {
          if (user.id === currentUserId) continue;
          if (matchedUserIds.has(user.id)) continue;

          const blocked = await db.isBlocked(currentUserId, user.id);
          if (blocked) continue;

          let isMatch = false;
          let matchedPhone: string | undefined = undefined;

          // 1. Match by user profile phone number
          if (profile?.phone_number) {
            const profileVariants = extractPhoneVariants(profile.phone_number);
            for (const pv of profileVariants) {
              if (contactVariants.has(pv)) {
                isMatch = true;
                matchedPhone = profile.phone_number;
                break;
              }
            }
          }

          // 2. Match by username (if digits or username matches phone)
          if (!isMatch) {
            const uVariants = extractPhoneVariants(user.username);
            for (const uv of uVariants) {
              if (contactVariants.has(uv)) {
                isMatch = true;
                matchedPhone = user.username;
                break;
              }
            }
          }

          // 3. Match by contact name == username
          if (!isMatch && item.name && item.name.length >= 3) {
            if (item.name.toLowerCase() === user.username.toLowerCase()) {
              isMatch = true;
            }
          }

          if (isMatch) {
            matchedUserIds.add(user.id);
            const chosenNickname = item.name || profile?.display_name || user.username;

            if (!existingContactIds.has(user.id)) {
              await db.addContact({
                id: uuidv4(),
                user_id: currentUserId,
                contact_user_id: user.id,
                nickname: chosenNickname,
                created_at: new Date().toISOString()
              });
              existingContactIds.add(user.id);
            }

            matchedResults.push({
              contactUserId: user.id,
              username: user.username,
              displayName: chosenNickname,
              avatarUrl: profile?.avatar_url || null,
              publicKey: profile?.public_key || null,
              bio: profile?.bio || '',
              phoneNumber: profile?.phone_number || null,
              matchedPhone: matchedPhone || (item.phones.length > 0 ? item.phones[0] : null)
            });
          }
        }
      }

      return res.status(200).json({
        matchedCount: matchedResults.length,
        contacts: matchedResults
      });
    } catch (e: any) {
      console.error('[UsersController] Erro syncContacts:', e);
      return res.status(500).json({ error: e.message });
    }
  }

  public static async addContact(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { contactUserId, nickname } = req.body;

      let targetUserId = contactUserId;
      const burner = activeBurnerIds.get(contactUserId);
      if (burner && burner.expiresAt > Date.now()) {
        targetUserId = burner.userId;
      }

      if (!targetUserId || targetUserId === userId) {
        return res.status(400).json({ error: 'ID de contato inválido.' });
      }

      const target = await db.findUserById(targetUserId);
      if (!target) {
        return res.status(404).json({ error: 'Contato não encontrado.' });
      }

      const contact = await db.addContact({
        id: uuidv4(),
        user_id: userId,
        contact_user_id: targetUserId,
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
      try {
        const wsManager = WebSocketManager.getInstance();
        if (wsManager) {
          wsManager.sendToUser(blockedUserId, {
            type: 'user:status',
            userId: userId,
            status: 'offline',
            timestamp: ''
          });
          wsManager.sendToUser(userId, {
            type: 'user:status',
            userId: blockedUserId,
            status: 'offline',
            timestamp: ''
          });
        }
      } catch (wsErr) {}
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
      try {
        const wsManager = WebSocketManager.getInstance();
        if (wsManager) {
          if (wsManager.isUserOnline(userId)) {
            wsManager.sendToUser(blockedUserId, {
              type: 'user:status',
              userId: userId,
              status: 'online',
              timestamp: new Date().toISOString()
            });
          }
          if (wsManager.isUserOnline(blockedUserId)) {
            wsManager.sendToUser(userId, {
              type: 'user:status',
              userId: blockedUserId,
              status: 'online',
              timestamp: new Date().toISOString()
            });
          }
        }
      } catch (wsErr) {}
      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async getBlockedUsers(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const blocked = await db.getBlockedUsers(userId);
      const enriched = await Promise.all(
        blocked.map(async b => {
          const user = await db.findUserById(b.blocked_id);
          const profile = await db.getProfile(b.blocked_id);
          return {
            id: b.blocked_id, // Important: id must be the blocked user ID, not the table entry ID
            blocked_entry_id: b.id,
            username: user?.username || '',
            phone_number: profile?.phone_number || '',
            display_name: profile?.display_name || user?.username || 'Usuário',
            avatar_url: profile?.avatar_url || null,
            bio: profile?.bio || null,
            public_key: profile?.public_key || null
          };
        })
      );
      return res.status(200).json(enriched);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async reportUser(req: AuthRequest, res: Response) {
    try {
      const reporterId = req.user!.userId;
      const { reportedUserId, reason, details, conversationId, messageId, recentMessages } = req.body;
      if (!reportedUserId) {
        return res.status(400).json({ error: 'reportedUserId é obrigatório.' });
      }
      if (reporterId === reportedUserId) {
        return res.status(400).json({ error: 'Você não pode denunciar a si mesmo.' });
      }

      const report = await db.createReport({
        id: uuidv4(),
        reporter_id: reporterId,
        reported_user_id: reportedUserId,
        conversation_id: conversationId,
        message_id: messageId,
        reason: reason || 'Outro',
        details,
        recent_messages: Array.isArray(recentMessages) ? recentMessages : [],
        status: 'pending',
        action_taken: 'none',
        created_at: new Date().toISOString()
      });
      return res.status(201).json({ success: true, message: 'Denúncia recebida com sucesso.', report });
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

  public static async registerFcmToken(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { token, deviceName } = req.body;
      const versionCode = parseInt(req.body.versionCode || (req.headers['x-app-version-code'] as string) || '0', 10);
      if (!token) {
        return res.status(400).json({ error: 'Token FCM é obrigatório.' });
      }
      await db.saveFcmToken(userId, token, deviceName, versionCode);
      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async removeFcmToken(req: AuthRequest, res: Response) {
    try {
      const { token } = req.body;
      if (!token) {
        return res.status(400).json({ error: 'Token FCM é obrigatório.' });
      }
      await db.removeFcmToken(token);
      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ==========================================
  // BURNER IDS (Identificadores Descartáveis)
  // ==========================================
  public static async generateBurnerId(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;

      // Remove existing burner if any
      const existing = userToBurnerMap.get(userId);
      if (existing) {
        activeBurnerIds.delete(existing);
        userToBurnerMap.delete(userId);
      }

      // Generate unique 8-digit numeric Burner ID
      let burnerId = '';
      let attempts = 0;
      do {
        burnerId = Math.floor(10000000 + Math.random() * 90000000).toString();
        attempts++;
      } while ((activeBurnerIds.has(burnerId) || await db.findUserById(burnerId)) && attempts < 20);

      const expiresAt = Date.now() + 24 * 60 * 60 * 1000; // 24 hours
      activeBurnerIds.set(burnerId, { userId, expiresAt });
      userToBurnerMap.set(userId, burnerId);

      return res.status(200).json({
        burnerId,
        expiresAt,
        remainingSeconds: 86400
      });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async getBurnerId(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const burnerId = userToBurnerMap.get(userId);

      if (!burnerId) {
        return res.status(200).json({ burnerId: null, expiresAt: 0, remainingSeconds: 0 });
      }

      const record = activeBurnerIds.get(burnerId);
      if (!record || record.expiresAt <= Date.now()) {
        activeBurnerIds.delete(burnerId);
        userToBurnerMap.delete(userId);
        return res.status(200).json({ burnerId: null, expiresAt: 0, remainingSeconds: 0 });
      }

      const remainingSeconds = Math.max(0, Math.floor((record.expiresAt - Date.now()) / 1000));
      return res.status(200).json({
        burnerId,
        expiresAt: record.expiresAt,
        remainingSeconds
      });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async destroyBurnerId(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const burnerId = userToBurnerMap.get(userId);
      if (burnerId) {
        activeBurnerIds.delete(burnerId);
        userToBurnerMap.delete(userId);
      }
      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }
}


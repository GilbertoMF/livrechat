import fs from 'fs';
import path from 'path';
import { Pool } from 'pg';
import { CONFIG } from '../config/env';

export interface User {
  id: string;
  username: string;
  password_hash: string;
  recovery_hash: string;
  created_at: string;
  updated_at: string;
}

export interface UserProfile {
  user_id: string;
  display_name: string;
  avatar_url?: string;
  bio?: string;
  public_key?: string;
  updated_at: string;
}

export interface UserSession {
  id: string;
  user_id: string;
  device_name: string;
  ip_address: string;
  refresh_token_hash: string;
  last_active: string;
  created_at: string;
}

export interface Contact {
  id: string;
  user_id: string;
  contact_user_id: string;
  nickname?: string;
  created_at: string;
}

export interface Conversation {
  id: string;
  created_at: string;
  updated_at: string;
}

export interface ConversationMember {
  conversation_id: string;
  user_id: string;
  is_pinned: boolean;
  is_archived: boolean;
  is_muted: boolean;
  last_read_at?: string;
}

export interface Message {
  id: string;
  conversation_id: string;
  sender_id: string;
  content: string;
  iv?: string;
  message_type: string;
  reply_to_id?: string;
  is_deleted: boolean;
  is_edited: boolean;
  created_at: string;
  updated_at: string;
}

export interface MessageReceipt {
  id: string;
  message_id: string;
  user_id: string;
  status: 'sent' | 'delivered' | 'read';
  timestamp: string;
}

export interface CallHistoryRecord {
  id: string;
  caller_id: string;
  callee_id: string;
  status: 'missed' | 'rejected' | 'completed' | 'busy' | 'failed';
  started_at: string;
  ended_at?: string;
  duration_seconds: number;
}

export interface BlockedUser {
  id: string;
  blocker_id: string;
  blocked_id: string;
  created_at: string;
}

export interface Report {
  id: string;
  reporter_id: string;
  reported_user_id: string;
  reason: string;
  details?: string;
  created_at: string;
}

export interface UserPreferences {
  user_id: string;
  notifications_enabled: boolean;
  read_receipts_enabled: boolean;
  typing_indicator_enabled: boolean;
  theme: string;
  updated_at: string;
}

export interface BackupMetadata {
  user_id: string;
  drive_file_id: string;
  backup_size_bytes: number;
  version: number;
  checksum: string;
  last_backup_at: string;
}

class DatabaseService {
  private pgPool: Pool | null = null;
  private localDataFile: string;
  private memoryStore: {
    users: User[];
    profiles: UserProfile[];
    sessions: UserSession[];
    contacts: Contact[];
    conversations: Conversation[];
    conversation_members: ConversationMember[];
    messages: Message[];
    message_receipts: MessageReceipt[];
    call_history: CallHistoryRecord[];
    blocked_users: BlockedUser[];
    reports: Report[];
    preferences: UserPreferences[];
    backup_metadata: BackupMetadata[];
  };

  constructor() {
    const dataDir = path.join(__dirname, '../../data');
    if (!fs.existsSync(dataDir)) {
      fs.mkdirSync(dataDir, { recursive: true });
    }
    this.localDataFile = path.join(dataDir, 'livrechat.db.json');

    this.memoryStore = {
      users: [],
      profiles: [],
      sessions: [],
      contacts: [],
      conversations: [],
      conversation_members: [],
      messages: [],
      message_receipts: [],
      call_history: [],
      blocked_users: [],
      reports: [],
      preferences: [],
      backup_metadata: []
    };

    this.loadFromLocal();
  }

  private loadFromLocal() {
    try {
      if (fs.existsSync(this.localDataFile)) {
        const raw = fs.readFileSync(this.localDataFile, 'utf-8');
        const parsed = JSON.parse(raw);
        this.memoryStore = { ...this.memoryStore, ...parsed };
      }
    } catch (e) {
      console.error('Error loading local persistent store:', e);
    }
  }

  private saveToLocal() {
    try {
      fs.writeFileSync(this.localDataFile, JSON.stringify(this.memoryStore, null, 2), 'utf-8');
    } catch (e) {
      console.error('Error saving local persistent store:', e);
    }
  }

  public async init() {
    if (CONFIG.DATABASE_URL) {
      try {
        const isSslNeeded = CONFIG.DATABASE_URL.includes('sslmode=require') ||
                            CONFIG.DATABASE_URL.includes('neon.tech') ||
                            CONFIG.DATABASE_URL.includes('supabase.co');

        this.pgPool = new Pool({
          connectionString: CONFIG.DATABASE_URL,
          ssl: isSslNeeded ? { rejectUnauthorized: false } : undefined
        });

        const client = await this.pgPool.connect();
        
        // Find schema.sql in possible locations
        const candidates = [
          path.join(process.cwd(), 'schema.sql'),
          path.join(__dirname, '../../schema.sql'),
          path.join(__dirname, '../schema.sql')
        ];
        const schemaPath = candidates.find(p => fs.existsSync(p));

        if (schemaPath) {
          const sql = fs.readFileSync(schemaPath, 'utf-8');
          await client.query(sql);
          console.log(`[Database] PostgreSQL (${CONFIG.DATABASE_URL.includes('neon.tech') ? 'Neon.tech' : 'Cloud'}) conectado e tabelas migradas com sucesso.`);
        } else {
          console.log('[Database] PostgreSQL conectado.');
        }
        client.release();
      } catch (err) {
        console.warn('[Database] Falha ao conectar no PostgreSQL. Usando armazenamento local:', (err as Error).message);
        this.pgPool = null;
      }
    } else {
      console.log('[Database] Executando em modo Zero-Setup (armazenamento local persistente).');
    }
  }

  // USERS
  public async findUserById(id: string): Promise<User | null> {
    return this.memoryStore.users.find(u => u.id === id) || null;
  }

  public async findUserByUsername(username: string): Promise<User | null> {
    const lower = username.toLowerCase();
    return this.memoryStore.users.find(u => u.username.toLowerCase() === lower) || null;
  }

  public async createUser(user: User): Promise<User> {
    this.memoryStore.users.push(user);
    this.saveToLocal();
    return user;
  }

  public async updateUserPassword(id: string, passwordHash: string): Promise<boolean> {
    const user = this.memoryStore.users.find(u => u.id === id);
    if (!user) return false;
    user.password_hash = passwordHash;
    user.updated_at = new Date().toISOString();
    this.saveToLocal();
    return true;
  }

  public async deleteUser(id: string): Promise<boolean> {
    this.memoryStore.users = this.memoryStore.users.filter(u => u.id !== id);
    this.memoryStore.profiles = this.memoryStore.profiles.filter(p => p.user_id !== id);
    this.memoryStore.sessions = this.memoryStore.sessions.filter(s => s.user_id !== id);
    this.memoryStore.contacts = this.memoryStore.contacts.filter(c => c.user_id !== id && c.contact_user_id !== id);
    this.memoryStore.blocked_users = this.memoryStore.blocked_users.filter(b => b.blocker_id !== id && b.blocked_id !== id);
    this.saveToLocal();
    return true;
  }

  // PROFILES
  public async getProfile(userId: string): Promise<UserProfile | null> {
    return this.memoryStore.profiles.find(p => p.user_id === userId) || null;
  }

  public async upsertProfile(profile: UserProfile): Promise<UserProfile> {
    const idx = this.memoryStore.profiles.findIndex(p => p.user_id === profile.user_id);
    if (idx >= 0) {
      this.memoryStore.profiles[idx] = profile;
    } else {
      this.memoryStore.profiles.push(profile);
    }
    this.saveToLocal();
    return profile;
  }

  // SESSIONS
  public async createSession(session: UserSession): Promise<UserSession> {
    this.memoryStore.sessions.push(session);
    this.saveToLocal();
    return session;
  }

  public async getSessionsByUserId(userId: string): Promise<UserSession[]> {
    return this.memoryStore.sessions.filter(s => s.user_id === userId);
  }

  public async revokeSession(sessionId: string, userId: string): Promise<boolean> {
    const initialLen = this.memoryStore.sessions.length;
    this.memoryStore.sessions = this.memoryStore.sessions.filter(s => !(s.id === sessionId && s.user_id === userId));
    this.saveToLocal();
    return this.memoryStore.sessions.length < initialLen;
  }

  public async revokeAllOtherSessions(userId: string, currentSessionId: string): Promise<number> {
    const before = this.memoryStore.sessions.length;
    this.memoryStore.sessions = this.memoryStore.sessions.filter(s => s.user_id !== userId || s.id === currentSessionId);
    this.saveToLocal();
    return before - this.memoryStore.sessions.length;
  }

  // CONTACTS
  public async getContacts(userId: string): Promise<Contact[]> {
    return this.memoryStore.contacts.filter(c => c.user_id === userId);
  }

  public async addContact(contact: Contact): Promise<Contact> {
    const existing = this.memoryStore.contacts.find(
      c => c.user_id === contact.user_id && c.contact_user_id === contact.contact_user_id
    );
    if (existing) return existing;
    this.memoryStore.contacts.push(contact);
    this.saveToLocal();
    return contact;
  }

  public async removeContact(userId: string, contactUserId: string): Promise<boolean> {
    this.memoryStore.contacts = this.memoryStore.contacts.filter(
      c => !(c.user_id === userId && c.contact_user_id === contactUserId)
    );
    this.saveToLocal();
    return true;
  }

  // BLOCKED USERS
  public async isBlocked(userA: string, userB: string): Promise<boolean> {
    return this.memoryStore.blocked_users.some(
      b => (b.blocker_id === userA && b.blocked_id === userB) ||
           (b.blocker_id === userB && b.blocked_id === userA)
    );
  }

  public async blockUser(blockerId: string, blockedId: string): Promise<BlockedUser> {
    const existing = this.memoryStore.blocked_users.find(b => b.blocker_id === blockerId && b.blocked_id === blockedId);
    if (existing) return existing;
    const entry: BlockedUser = {
      id: `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
      blocker_id: blockerId,
      blocked_id: blockedId,
      created_at: new Date().toISOString()
    };
    this.memoryStore.blocked_users.push(entry);
    this.saveToLocal();
    return entry;
  }

  public async unblockUser(blockerId: string, blockedId: string): Promise<boolean> {
    this.memoryStore.blocked_users = this.memoryStore.blocked_users.filter(
      b => !(b.blocker_id === blockerId && b.blocked_id === blockedId)
    );
    this.saveToLocal();
    return true;
  }

  public async getBlockedUsers(userId: string): Promise<BlockedUser[]> {
    return this.memoryStore.blocked_users.filter(b => b.blocker_id === userId);
  }

  // REPORTS
  public async createReport(report: Report): Promise<Report> {
    this.memoryStore.reports.push(report);
    this.saveToLocal();
    return report;
  }

  // CONVERSATIONS
  public async getOrCreateDirectConversation(userId1: string, userId2: string): Promise<string> {
    // Find conversation where both are members
    const convs1 = this.memoryStore.conversation_members.filter(m => m.user_id === userId1).map(m => m.conversation_id);
    const convs2 = this.memoryStore.conversation_members.filter(m => m.user_id === userId2).map(m => m.conversation_id);
    const common = convs1.find(c => convs2.includes(c));

    if (common) {
      return common;
    }

    const newId = `conv_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
    const now = new Date().toISOString();
    this.memoryStore.conversations.push({
      id: newId,
      created_at: now,
      updated_at: now
    });

    this.memoryStore.conversation_members.push(
      {
        conversation_id: newId,
        user_id: userId1,
        is_pinned: false,
        is_archived: false,
        is_muted: false
      },
      {
        conversation_id: newId,
        user_id: userId2,
        is_pinned: false,
        is_archived: false,
        is_muted: false
      }
    );

    this.saveToLocal();
    return newId;
  }

  public async getConversationsForUser(userId: string) {
    const memberships = this.memoryStore.conversation_members.filter(m => m.user_id === userId);
    const results = [];

    for (const mem of memberships) {
      const conv = this.memoryStore.conversations.find(c => c.id === mem.conversation_id);
      if (!conv) continue;

      // Find the other member
      const otherMem = this.memoryStore.conversation_members.find(
        m => m.conversation_id === mem.conversation_id && m.user_id !== userId
      );
      if (!otherMem) continue;

      const otherUser = this.memoryStore.users.find(u => u.id === otherMem.user_id);
      const otherProfile = this.memoryStore.profiles.find(p => p.user_id === otherMem.user_id);

      // Last message
      const convMessages = this.memoryStore.messages
        .filter(m => m.conversation_id === mem.conversation_id && !m.is_deleted)
        .sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());

      const lastMessage = convMessages[0] || null;

      // Unread count
      const unreadCount = convMessages.filter(m => {
        if (m.sender_id === userId) return false;
        const receipt = this.memoryStore.message_receipts.find(r => r.message_id === m.id && r.user_id === userId && r.status === 'read');
        return !receipt;
      }).length;

      results.push({
        conversationId: mem.conversation_id,
        isPinned: mem.is_pinned,
        isArchived: mem.is_archived,
        isMuted: mem.is_muted,
        otherUser: {
          id: otherMem.user_id,
          username: otherUser ? otherUser.username : 'desconhecido',
          displayName: otherProfile ? otherProfile.display_name : (otherUser ? otherUser.username : 'Desconhecido'),
          avatarUrl: otherProfile?.avatar_url || null,
          publicKey: otherProfile?.public_key || null
        },
        lastMessage,
        unreadCount,
        updatedAt: conv.updated_at
      });
    }

    return results.sort((a, b) => {
      if (a.isPinned !== b.isPinned) return a.isPinned ? -1 : 1;
      return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
    });
  }

  public async isUserInConversation(userId: string, conversationId: string): Promise<boolean> {
    return this.memoryStore.conversation_members.some(
      m => m.conversation_id === conversationId && m.user_id === userId
    );
  }

  public async getConversationMembers(conversationId: string): Promise<string[]> {
    return this.memoryStore.conversation_members
      .filter(m => m.conversation_id === conversationId)
      .map(m => m.user_id);
  }

  // MESSAGES
  public async createMessage(msg: Message): Promise<Message> {
    // Idempotency check: don't create duplicate if id already exists
    const existing = this.memoryStore.messages.find(m => m.id === msg.id);
    if (existing) return existing;

    this.memoryStore.messages.push(msg);

    // Update conversation timestamp
    const conv = this.memoryStore.conversations.find(c => c.id === msg.conversation_id);
    if (conv) {
      conv.updated_at = msg.created_at;
    }

    this.saveToLocal();
    return msg;
  }

  public async getMessages(conversationId: string, limit = 50, beforeTimestamp?: string): Promise<Message[]> {
    let filtered = this.memoryStore.messages.filter(m => m.conversation_id === conversationId && !m.is_deleted);
    if (beforeTimestamp) {
      const beforeTime = new Date(beforeTimestamp).getTime();
      filtered = filtered.filter(m => new Date(m.created_at).getTime() < beforeTime);
    }
    filtered.sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
    return filtered.slice(0, limit).reverse();
  }

  public async editMessage(messageId: string, senderId: string, newContent: string): Promise<Message | null> {
    const msg = this.memoryStore.messages.find(m => m.id === messageId && m.sender_id === senderId);
    if (!msg || msg.is_deleted) return null;
    msg.content = newContent;
    msg.is_edited = true;
    msg.updated_at = new Date().toISOString();
    this.saveToLocal();
    return msg;
  }

  public async deleteMessage(messageId: string, senderId: string): Promise<boolean> {
    const msg = this.memoryStore.messages.find(m => m.id === messageId && m.sender_id === senderId);
    if (!msg) return false;
    msg.is_deleted = true;
    msg.content = 'Mensagem apagada';
    msg.updated_at = new Date().toISOString();
    this.saveToLocal();
    return true;
  }

  // RECEIPTS
  public async addReceipt(receipt: MessageReceipt) {
    const existing = this.memoryStore.message_receipts.find(
      r => r.message_id === receipt.message_id && r.user_id === receipt.user_id && r.status === receipt.status
    );
    if (!existing) {
      this.memoryStore.message_receipts.push(receipt);
      this.saveToLocal();
    }
  }

  public async getReceiptsForMessage(messageId: string): Promise<MessageReceipt[]> {
    return this.memoryStore.message_receipts.filter(r => r.message_id === messageId);
  }

  // CALLS
  public async recordCall(record: CallHistoryRecord) {
    this.memoryStore.call_history.push(record);
    this.saveToLocal();
    return record;
  }

  public async getCallHistory(userId: string, limit = 50): Promise<CallHistoryRecord[]> {
    return this.memoryStore.call_history
      .filter(c => c.caller_id === userId || c.callee_id === userId)
      .sort((a, b) => new Date(b.started_at).getTime() - new Date(a.started_at).getTime())
      .slice(0, limit);
  }

  // PREFERENCES
  public async getPreferences(userId: string): Promise<UserPreferences> {
    let p = this.memoryStore.preferences.find(x => x.user_id === userId);
    if (!p) {
      p = {
        user_id: userId,
        notifications_enabled: true,
        read_receipts_enabled: true,
        typing_indicator_enabled: true,
        theme: 'system',
        updated_at: new Date().toISOString()
      };
      this.memoryStore.preferences.push(p);
      this.saveToLocal();
    }
    return p;
  }

  public async updatePreferences(userId: string, updates: Partial<UserPreferences>): Promise<UserPreferences> {
    const current = await this.getPreferences(userId);
    Object.assign(current, updates, { updated_at: new Date().toISOString() });
    this.saveToLocal();
    return current;
  }

  // BACKUP METADATA
  public async getBackupMetadata(userId: string): Promise<BackupMetadata | null> {
    return this.memoryStore.backup_metadata.find(b => b.user_id === userId) || null;
  }

  public async saveBackupMetadata(meta: BackupMetadata): Promise<BackupMetadata> {
    const idx = this.memoryStore.backup_metadata.findIndex(b => b.user_id === meta.user_id);
    if (idx >= 0) {
      this.memoryStore.backup_metadata[idx] = meta;
    } else {
      this.memoryStore.backup_metadata.push(meta);
    }
    this.saveToLocal();
    return meta;
  }
}

export const db = new DatabaseService();

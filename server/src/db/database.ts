import fs from 'fs';
import path from 'path';
import { Pool } from 'pg';
import { v4 as uuidv4 } from 'uuid';
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
  phone_number?: string;
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
  is_group?: boolean;
  title?: string;
  avatar_url?: string;
  created_by?: string;
  created_at: string;
  updated_at: string;
  admin_only_message?: boolean;
  admin_only_edit_info?: boolean;
  admin_only_add_members?: boolean;
}

export interface ConversationMember {
  conversation_id: string;
  user_id: string;
  is_pinned: boolean;
  is_archived: boolean;
  is_muted: boolean;
  last_read_at?: string;
  is_admin?: boolean;
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
  reactions?: string;
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

export interface StatusReactionRecord {
  user_id: string;
  username: string;
  display_name: string;
  avatar_url?: string;
  emoji: string;
  created_at: string;
}

export interface StatusViewRecord {
  user_id: string;
  username: string;
  display_name: string;
  avatar_url?: string;
  viewed_at: string;
}

export interface StatusRecord {
  id: string;
  user_id: string;
  display_name: string;
  username: string;
  avatar_url?: string;
  content_type: 'text' | 'image' | 'audio' | 'video';
  content: string;
  caption?: string;
  background_color?: string;
  created_at: string;
  expires_at: string;
  reactions?: StatusReactionRecord[];
  views?: StatusViewRecord[];
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
    statuses: StatusRecord[];
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
      backup_metadata: [],
      statuses: []
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
          await client.query('ALTER TABLE messages ALTER COLUMN iv TYPE VARCHAR(255);').catch(() => {});
          await client.query('ALTER TABLE conversations ADD COLUMN IF NOT EXISTS is_group BOOLEAN DEFAULT FALSE;').catch(() => {});
          await client.query('ALTER TABLE conversations ADD COLUMN IF NOT EXISTS title VARCHAR(100);').catch(() => {});
          await client.query('ALTER TABLE conversations ADD COLUMN IF NOT EXISTS avatar_url TEXT;').catch(() => {});
          await client.query('ALTER TABLE conversations ADD COLUMN IF NOT EXISTS created_by VARCHAR(36);').catch(() => {});
          await client.query('ALTER TABLE conversations ADD COLUMN IF NOT EXISTS admin_only_message BOOLEAN DEFAULT FALSE;').catch(() => {});
          await client.query('ALTER TABLE conversations ADD COLUMN IF NOT EXISTS admin_only_edit_info BOOLEAN DEFAULT FALSE;').catch(() => {});
          await client.query('ALTER TABLE conversations ADD COLUMN IF NOT EXISTS admin_only_add_members BOOLEAN DEFAULT FALSE;').catch(() => {});
          await client.query('ALTER TABLE conversation_members ADD COLUMN IF NOT EXISTS is_admin BOOLEAN DEFAULT FALSE;').catch(() => {});
          await client.query('ALTER TABLE statuses ADD COLUMN IF NOT EXISTS reactions JSONB DEFAULT \'[]\'::jsonb;').catch(() => {});
          await client.query('ALTER TABLE statuses ADD COLUMN IF NOT EXISTS views JSONB DEFAULT \'[]\'::jsonb;').catch(() => {});
          await client.query('ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS phone_number VARCHAR(50);').catch(() => {});
          await client.query('CREATE INDEX IF NOT EXISTS idx_user_profiles_phone ON user_profiles(phone_number);').catch(() => {});
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

  public handlePgError(e: any) {
    const msg = (e?.message || '').toLowerCase();
    if (msg.includes('exceeded the quota') || msg.includes('suspended') || msg.includes('limit') || msg.includes('quota')) {
      console.warn('[Database] Alerta Neon/PG: Limite de cota excedido. Alternando automaticamente para Zero-Setup local persistente.');
      try { this.pgPool?.end().catch(() => {}); } catch (_) {}
      this.pgPool = null;
    }
  }

  // ==========================================
  // USERS
  // ==========================================
  public async ensureUserExists(userId: string, username: string = 'usuario'): Promise<void> {
    const now = new Date().toISOString();
    // Memory store
    let u = this.memoryStore.users.find(x => x.id === userId);
    if (!u) {
      u = {
        id: userId,
        username,
        password_hash: 'migrated',
        recovery_hash: 'migrated',
        created_at: now,
        updated_at: now
      };
      this.memoryStore.users.push(u);
    }
    let p = this.memoryStore.profiles.find(x => x.user_id === userId);
    if (!p) {
      p = {
        user_id: userId,
        display_name: username,
        updated_at: now
      };
      this.memoryStore.profiles.push(p);
    }
    this.saveToLocal();

    // PostgreSQL
    if (this.pgPool) {
      try {
        await this.pgPool.query(`
          INSERT INTO users (id, username, password_hash, recovery_hash, created_at, updated_at)
          VALUES ($1, $2, 'migrated', 'migrated', NOW(), NOW())
          ON CONFLICT (id) DO NOTHING
        `, [userId, username]);

        await this.pgPool.query(`
          INSERT INTO user_profiles (user_id, display_name, updated_at)
          VALUES ($1, $2, NOW())
          ON CONFLICT (user_id) DO NOTHING
        `, [userId, username]);
      } catch (e) {
        console.warn('[Database] Erro ao assegurar existência do usuário no PG:', e);
      }
    }
  }

  public async findUserById(id: string): Promise<User | null> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT * FROM users WHERE id = $1', [id]);
        if (res.rows.length > 0) {
          const row = res.rows[0];
          return {
            id: row.id,
            username: row.username,
            password_hash: row.password_hash,
            recovery_hash: row.recovery_hash,
            created_at: row.created_at?.toISOString?.() || row.created_at,
            updated_at: row.updated_at?.toISOString?.() || row.updated_at
          };
        }
      } catch (e) {
        console.warn('[Database] Erro PG findUserById:', e);
      }
    }
    return this.memoryStore.users.find(u => u.id === id) || null;
  }

  public async findUserByUsername(username: string): Promise<User | null> {
    const lower = username.toLowerCase();
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT * FROM users WHERE LOWER(username) = $1', [lower]);
        if (res.rows.length > 0) {
          const row = res.rows[0];
          return {
            id: row.id,
            username: row.username,
            password_hash: row.password_hash,
            recovery_hash: row.recovery_hash,
            created_at: row.created_at?.toISOString?.() || row.created_at,
            updated_at: row.updated_at?.toISOString?.() || row.updated_at
          };
        }
      } catch (e) {
        console.warn('[Database] Erro PG findUserByUsername:', e);
      }
    }
    return this.memoryStore.users.find(u => u.username.toLowerCase() === lower) || null;
  }

  public async createUser(user: User): Promise<User> {
    // Memory
    const idx = this.memoryStore.users.findIndex(u => u.id === user.id);
    if (idx >= 0) {
      this.memoryStore.users[idx] = user;
    } else {
      this.memoryStore.users.push(user);
    }
    this.saveToLocal();

    // PG
    if (this.pgPool) {
      try {
        await this.pgPool.query(`
          INSERT INTO users (id, username, password_hash, recovery_hash, created_at, updated_at)
          VALUES ($1, $2, $3, $4, $5, $6)
          ON CONFLICT (id) DO UPDATE SET
            username = EXCLUDED.username,
            password_hash = EXCLUDED.password_hash,
            recovery_hash = EXCLUDED.recovery_hash,
            updated_at = EXCLUDED.updated_at
        `, [user.id, user.username, user.password_hash, user.recovery_hash, user.created_at, user.updated_at]);
      } catch (e) {
        console.warn('[Database] Erro PG createUser:', e);
      }
    }
    return user;
  }

  public async updateUserPassword(id: string, passwordHash: string): Promise<boolean> {
    const user = this.memoryStore.users.find(u => u.id === id);
    if (user) {
      user.password_hash = passwordHash;
      user.updated_at = new Date().toISOString();
      this.saveToLocal();
    }

    if (this.pgPool) {
      try {
        await this.pgPool.query('UPDATE users SET password_hash = $1, updated_at = NOW() WHERE id = $2', [passwordHash, id]);
        return true;
      } catch (e) {
        console.warn('[Database] Erro PG updateUserPassword:', e);
      }
    }
    return !!user;
  }

  public async deleteUser(id: string): Promise<boolean> {
    this.memoryStore.users = this.memoryStore.users.filter(u => u.id !== id);
    this.memoryStore.profiles = this.memoryStore.profiles.filter(p => p.user_id !== id);
    this.memoryStore.sessions = this.memoryStore.sessions.filter(s => s.user_id !== id);
    this.memoryStore.contacts = this.memoryStore.contacts.filter(c => c.user_id !== id && c.contact_user_id !== id);
    this.memoryStore.blocked_users = this.memoryStore.blocked_users.filter(b => b.blocker_id !== id && b.blocked_id !== id);
    this.saveToLocal();

    if (this.pgPool) {
      try {
        await this.pgPool.query('DELETE FROM users WHERE id = $1', [id]);
        return true;
      } catch (e) {
        console.warn('[Database] Erro PG deleteUser:', e);
      }
    }
    return true;
  }

  // ==========================================
  // PROFILES
  // ==========================================
  public async getProfile(userId: string): Promise<UserProfile | null> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT * FROM user_profiles WHERE user_id = $1', [userId]);
        if (res.rows.length > 0) {
          const row = res.rows[0];
          return {
            user_id: row.user_id,
            display_name: row.display_name,
            avatar_url: row.avatar_url || undefined,
            bio: row.bio || undefined,
            public_key: row.public_key || undefined,
            phone_number: row.phone_number || undefined,
            updated_at: row.updated_at?.toISOString?.() || row.updated_at
          };
        }
      } catch (e) {
        console.warn('[Database] Erro PG getProfile:', e);
      }
    }
    return this.memoryStore.profiles.find(p => p.user_id === userId) || null;
  }

  public async upsertProfile(profile: UserProfile): Promise<UserProfile> {
    const idx = this.memoryStore.profiles.findIndex(p => p.user_id === profile.user_id);
    if (idx >= 0) {
      this.memoryStore.profiles[idx] = { ...this.memoryStore.profiles[idx], ...profile };
    } else {
      this.memoryStore.profiles.push(profile);
    }
    this.saveToLocal();

    if (this.pgPool) {
      try {
        await this.ensureUserExists(profile.user_id, profile.display_name);
        await this.pgPool.query(`
          INSERT INTO user_profiles (user_id, display_name, avatar_url, bio, public_key, phone_number, updated_at)
          VALUES ($1, $2, $3, $4, $5, $6, $7)
          ON CONFLICT (user_id) DO UPDATE SET
            display_name = EXCLUDED.display_name,
            avatar_url = EXCLUDED.avatar_url,
            bio = EXCLUDED.bio,
            public_key = EXCLUDED.public_key,
            phone_number = COALESCE(EXCLUDED.phone_number, user_profiles.phone_number),
            updated_at = EXCLUDED.updated_at
        `, [
          profile.user_id,
          profile.display_name,
          profile.avatar_url || null,
          profile.bio || null,
          profile.public_key || null,
          profile.phone_number || null,
          profile.updated_at
        ]);
      } catch (e) {
        console.warn('[Database] Erro PG upsertProfile:', e);
      }
    }
    return profile;
  }

  public async getAllUsersWithProfiles(): Promise<Array<{ user: User; profile: UserProfile | null }>> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query(`
          SELECT u.id, u.username, u.password_hash, u.recovery_hash, u.created_at, u.updated_at,
                 p.display_name, p.avatar_url, p.bio, p.public_key, p.phone_number, p.updated_at as p_updated_at
          FROM users u
          LEFT JOIN user_profiles p ON u.id = p.user_id
        `);
        return res.rows.map(r => ({
          user: {
            id: r.id,
            username: r.username,
            password_hash: r.password_hash,
            recovery_hash: r.recovery_hash,
            created_at: r.created_at?.toISOString?.() || r.created_at,
            updated_at: r.updated_at?.toISOString?.() || r.updated_at
          },
          profile: r.display_name ? {
            user_id: r.id,
            display_name: r.display_name,
            avatar_url: r.avatar_url || undefined,
            bio: r.bio || undefined,
            public_key: r.public_key || undefined,
            phone_number: r.phone_number || undefined,
            updated_at: r.p_updated_at?.toISOString?.() || r.p_updated_at
          } : null
        }));
      } catch (e) {
        console.warn('[Database] Erro PG getAllUsersWithProfiles:', e);
      }
    }
    return this.memoryStore.users.map(u => ({
      user: u,
      profile: this.memoryStore.profiles.find(p => p.user_id === u.id) || null
    }));
  }

  // ==========================================
  // SESSIONS
  // ==========================================
  public async createSession(session: UserSession): Promise<UserSession> {
    this.memoryStore.sessions.push(session);
    this.saveToLocal();

    if (this.pgPool) {
      try {
        await this.ensureUserExists(session.user_id);
        await this.pgPool.query(`
          INSERT INTO user_sessions (id, user_id, device_name, ip_address, refresh_token_hash, last_active, created_at)
          VALUES ($1, $2, $3, $4, $5, $6, $7)
          ON CONFLICT (id) DO NOTHING
        `, [session.id, session.user_id, session.device_name, session.ip_address, session.refresh_token_hash, session.last_active, session.created_at]);
      } catch (e) {
        console.warn('[Database] Erro PG createSession:', e);
      }
    }
    return session;
  }

  public async getSessionsByUserId(userId: string): Promise<UserSession[]> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT * FROM user_sessions WHERE user_id = $1 ORDER BY created_at DESC', [userId]);
        return res.rows.map(r => ({
          id: r.id,
          user_id: r.user_id,
          device_name: r.device_name,
          ip_address: r.ip_address,
          refresh_token_hash: r.refresh_token_hash,
          last_active: r.last_active?.toISOString?.() || r.last_active,
          created_at: r.created_at?.toISOString?.() || r.created_at
        }));
      } catch (e) {
        console.warn('[Database] Erro PG getSessionsByUserId:', e);
      }
    }
    return this.memoryStore.sessions.filter(s => s.user_id === userId);
  }

  public async revokeSession(sessionId: string, userId: string): Promise<boolean> {
    const initialLen = this.memoryStore.sessions.length;
    this.memoryStore.sessions = this.memoryStore.sessions.filter(s => !(s.id === sessionId && s.user_id === userId));
    this.saveToLocal();

    if (this.pgPool) {
      try {
        await this.pgPool.query('DELETE FROM user_sessions WHERE id = $1 AND user_id = $2', [sessionId, userId]);
        return true;
      } catch (e) {
        console.warn('[Database] Erro PG revokeSession:', e);
      }
    }
    return this.memoryStore.sessions.length < initialLen;
  }

  public async revokeAllOtherSessions(userId: string, currentSessionId: string): Promise<number> {
    const before = this.memoryStore.sessions.length;
    this.memoryStore.sessions = this.memoryStore.sessions.filter(s => s.user_id !== userId || s.id === currentSessionId);
    this.saveToLocal();

    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('DELETE FROM user_sessions WHERE user_id = $1 AND id != $2', [userId, currentSessionId]);
        return res.rowCount || (before - this.memoryStore.sessions.length);
      } catch (e) {
        console.warn('[Database] Erro PG revokeAllOtherSessions:', e);
      }
    }
    return before - this.memoryStore.sessions.length;
  }

  // ==========================================
  // CONTACTS
  // ==========================================
  public async getContacts(userId: string): Promise<Contact[]> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT * FROM contacts WHERE user_id = $1', [userId]);
        return res.rows.map(r => ({
          id: r.id,
          user_id: r.user_id,
          contact_user_id: r.contact_user_id,
          nickname: r.nickname || undefined,
          created_at: r.created_at?.toISOString?.() || r.created_at
        }));
      } catch (e) {
        console.warn('[Database] Erro PG getContacts:', e);
      }
    }
    return this.memoryStore.contacts.filter(c => c.user_id === userId);
  }

  public async addContact(contact: Contact): Promise<Contact> {
    const existing = this.memoryStore.contacts.find(
      c => c.user_id === contact.user_id && c.contact_user_id === contact.contact_user_id
    );
    if (!existing) {
      this.memoryStore.contacts.push(contact);
      this.saveToLocal();
    }

    if (this.pgPool) {
      try {
        await this.ensureUserExists(contact.user_id);
        await this.ensureUserExists(contact.contact_user_id);
        await this.pgPool.query(`
          INSERT INTO contacts (id, user_id, contact_user_id, nickname, created_at)
          VALUES ($1, $2, $3, $4, $5)
          ON CONFLICT (user_id, contact_user_id) DO NOTHING
        `, [contact.id, contact.user_id, contact.contact_user_id, contact.nickname || null, contact.created_at]);
      } catch (e) {
        console.warn('[Database] Erro PG addContact:', e);
      }
    }
    return existing || contact;
  }

  public async updateContactNickname(userId: string, contactUserId: string, nickname: string): Promise<boolean> {
    const contact = this.memoryStore.contacts.find(
      c => c.user_id === userId && c.contact_user_id === contactUserId
    );
    if (contact) {
      contact.nickname = nickname;
      this.saveToLocal();
    }
    if (this.pgPool) {
      try {
        await this.pgPool.query(
          'UPDATE contacts SET nickname = $1 WHERE user_id = $2 AND contact_user_id = $3',
          [nickname, userId, contactUserId]
        );
        return true;
      } catch (e) {
        console.warn('[Database] Erro PG updateContactNickname:', e);
      }
    }
    return true;
  }

  public async removeContact(userId: string, contactUserId: string): Promise<boolean> {
    this.memoryStore.contacts = this.memoryStore.contacts.filter(
      c => !(c.user_id === userId && c.contact_user_id === contactUserId)
    );

    // Also remove direct conversation memberships
    const directConvs = this.memoryStore.conversation_members
      .filter(m => m.user_id === userId)
      .map(m => m.conversation_id)
      .filter(convId => 
        this.memoryStore.conversation_members.some(m => m.conversation_id === convId && m.user_id === contactUserId)
      );

    for (const cId of directConvs) {
      this.memoryStore.conversation_members = this.memoryStore.conversation_members.filter(
        m => !(m.conversation_id === cId && m.user_id === userId)
      );
      this.memoryStore.messages = this.memoryStore.messages.filter(m => m.conversation_id !== cId);
    }
    this.saveToLocal();

    if (this.pgPool) {
      try {
        await this.pgPool.query('DELETE FROM contacts WHERE user_id = $1 AND contact_user_id = $2', [userId, contactUserId]);
        await this.pgPool.query(`
          DELETE FROM conversation_members 
          WHERE user_id = $1 AND conversation_id IN (
            SELECT conversation_id FROM conversation_members WHERE user_id = $2
          )
        `, [userId, contactUserId]);
        return true;
      } catch (e) {
        console.warn('[Database] Erro PG removeContact:', e);
      }
    }
    return true;
  }

  // ==========================================
  // BLOCKED USERS
  // ==========================================
  public async isBlocked(userA: string, userB: string): Promise<boolean> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query(`
          SELECT 1 FROM blocked_users
          WHERE (blocker_id = $1 AND blocked_id = $2)
             OR (blocker_id = $2 AND blocked_id = $1)
          LIMIT 1
        `, [userA, userB]);
        return res.rows.length > 0;
      } catch (e) {
        console.warn('[Database] Erro PG isBlocked:', e);
      }
    }
    return this.memoryStore.blocked_users.some(
      b => (b.blocker_id === userA && b.blocked_id === userB) ||
           (b.blocker_id === userB && b.blocked_id === userA)
    );
  }

  public async blockUser(blockerId: string, blockedId: string): Promise<BlockedUser> {
    const entry: BlockedUser = {
      id: `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
      blocker_id: blockerId,
      blocked_id: blockedId,
      created_at: new Date().toISOString()
    };
    this.memoryStore.blocked_users.push(entry);
    this.saveToLocal();

    if (this.pgPool) {
      try {
        await this.ensureUserExists(blockerId);
        await this.ensureUserExists(blockedId);
        await this.pgPool.query(`
          INSERT INTO blocked_users (id, blocker_id, blocked_id, created_at)
          VALUES ($1, $2, $3, $4)
          ON CONFLICT (blocker_id, blocked_id) DO NOTHING
        `, [entry.id, blockerId, blockedId, entry.created_at]);
      } catch (e) {
        console.warn('[Database] Erro PG blockUser:', e);
      }
    }
    return entry;
  }

  public async unblockUser(blockerId: string, blockedId: string): Promise<boolean> {
    this.memoryStore.blocked_users = this.memoryStore.blocked_users.filter(
      b => !(b.blocker_id === blockerId && b.blocked_id === blockedId)
    );
    this.saveToLocal();

    if (this.pgPool) {
      try {
        await this.pgPool.query('DELETE FROM blocked_users WHERE blocker_id = $1 AND blocked_id = $2', [blockerId, blockedId]);
        return true;
      } catch (e) {
        console.warn('[Database] Erro PG unblockUser:', e);
      }
    }
    return true;
  }

  public async getBlockedUsers(userId: string): Promise<BlockedUser[]> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT * FROM blocked_users WHERE blocker_id = $1', [userId]);
        return res.rows.map(r => ({
          id: r.id,
          blocker_id: r.blocker_id,
          blocked_id: r.blocked_id,
          created_at: r.created_at?.toISOString?.() || r.created_at
        }));
      } catch (e) {
        console.warn('[Database] Erro PG getBlockedUsers:', e);
      }
    }
    return this.memoryStore.blocked_users.filter(b => b.blocker_id === userId);
  }

  // ==========================================
  // REPORTS
  // ==========================================
  public async createReport(report: Report): Promise<Report> {
    this.memoryStore.reports.push(report);
    this.saveToLocal();

    if (this.pgPool) {
      try {
        await this.ensureUserExists(report.reporter_id);
        await this.ensureUserExists(report.reported_user_id);
        await this.pgPool.query(`
          INSERT INTO reports (id, reporter_id, reported_user_id, reason, details, created_at)
          VALUES ($1, $2, $3, $4, $5, $6)
        `, [report.id, report.reporter_id, report.reported_user_id, report.reason, report.details || null, report.created_at]);
      } catch (e) {
        console.warn('[Database] Erro PG createReport:', e);
      }
    }
    return report;
  }

  // ==========================================
  // CONVERSATIONS
  // ==========================================
  public async ensureConversationMember(conversationId: string, userId: string): Promise<void> {
    const now = new Date().toISOString();
    // Memory
    if (!this.memoryStore.conversations.some(c => c.id === conversationId)) {
      this.memoryStore.conversations.push({ id: conversationId, created_at: now, updated_at: now });
    }
    if (!this.memoryStore.conversation_members.some(m => m.conversation_id === conversationId && m.user_id === userId)) {
      this.memoryStore.conversation_members.push({
        conversation_id: conversationId,
        user_id: userId,
        is_pinned: false,
        is_archived: false,
        is_muted: false
      });
    }
    this.saveToLocal();

    // PG
    if (this.pgPool) {
      try {
        await this.ensureUserExists(userId);
        await this.pgPool.query(`
          INSERT INTO conversations (id, created_at, updated_at)
          VALUES ($1, NOW(), NOW())
          ON CONFLICT (id) DO NOTHING
        `, [conversationId]);
        await this.pgPool.query(`
          INSERT INTO conversation_members (conversation_id, user_id)
          VALUES ($1, $2)
          ON CONFLICT (conversation_id, user_id) DO NOTHING
        `, [conversationId, userId]);
      } catch (e) {
        console.warn('[Database] Erro PG ensureConversationMember:', e);
      }
    }
  }

  public async getOrCreateDirectConversation(userId1: string, userId2: string): Promise<string> {
    const isSelf = userId1 === userId2;

    // 1. Check PostgreSQL first if connected
    if (this.pgPool) {
      try {
        await this.ensureUserExists(userId1);
        if (!isSelf) {
          await this.ensureUserExists(userId2);
        }

        const query = isSelf ? `
          SELECT m1.conversation_id
          FROM conversation_members m1
          JOIN conversations c ON c.id = m1.conversation_id
          WHERE m1.user_id = $1 AND (c.is_group IS FALSE OR c.is_group IS NULL)
          GROUP BY m1.conversation_id
          HAVING COUNT(*) = 1
          LIMIT 1
        ` : `
          SELECT m1.conversation_id
          FROM conversation_members m1
          JOIN conversation_members m2 ON m1.conversation_id = m2.conversation_id
          JOIN conversations c ON c.id = m1.conversation_id
          WHERE m1.user_id = $1 AND m2.user_id = $2 AND (c.is_group IS FALSE OR c.is_group IS NULL)
          LIMIT 1
        `;
        const params = isSelf ? [userId1] : [userId1, userId2];
        const res = await this.pgPool.query(query, params);

        if (res.rows.length > 0) {
          const foundId = res.rows[0].conversation_id;
          // Synchronize memory store
          await this.ensureConversationMember(foundId, userId1);
          if (!isSelf) {
            await this.ensureConversationMember(foundId, userId2);
          }
          return foundId;
        }

        const newId = `conv_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
        await this.pgPool.query(`
          INSERT INTO conversations (id, is_group, created_at, updated_at)
          VALUES ($1, false, NOW(), NOW())
          ON CONFLICT (id) DO NOTHING
        `, [newId]);

        if (isSelf) {
          await this.pgPool.query(`
            INSERT INTO conversation_members (conversation_id, user_id)
            VALUES ($1, $2)
            ON CONFLICT (conversation_id, user_id) DO NOTHING
          `, [newId, userId1]);
          await this.ensureConversationMember(newId, userId1);
        } else {
          await this.pgPool.query(`
            INSERT INTO conversation_members (conversation_id, user_id)
            VALUES ($1, $2), ($1, $3)
            ON CONFLICT (conversation_id, user_id) DO NOTHING
          `, [newId, userId1, userId2]);
          await this.ensureConversationMember(newId, userId1);
          await this.ensureConversationMember(newId, userId2);
        }

        return newId;
      } catch (e) {
        console.warn('[Database] Erro PG getOrCreateDirectConversation:', e);
      }
    }

    // 2. Memory store fallback
    if (isSelf) {
      const existingSelf = this.memoryStore.conversations.find(c => {
        const members = this.memoryStore.conversation_members.filter(m => m.conversation_id === c.id);
        return members.length === 1 && members[0].user_id === userId1 && !c.is_group;
      });
      if (existingSelf) return existingSelf.id;

      const newId = `conv_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
      const now = new Date().toISOString();
      this.memoryStore.conversations.push({
        id: newId,
        is_group: false,
        created_at: now,
        updated_at: now
      });
      this.memoryStore.conversation_members.push({
        conversation_id: newId,
        user_id: userId1,
        is_pinned: false,
        is_archived: false,
        is_muted: false
      });
      this.saveToLocal();
      return newId;
    }

    const convs1 = this.memoryStore.conversation_members.filter(m => m.user_id === userId1).map(m => m.conversation_id);
    const convs2 = this.memoryStore.conversation_members.filter(m => m.user_id === userId2).map(m => m.conversation_id);
    const common = convs1.find(c => {
      const conv = this.memoryStore.conversations.find(cv => cv.id === c);
      return !conv?.is_group && convs2.includes(c);
    });

    if (common) {
      return common;
    }

    const newId = `conv_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
    const now = new Date().toISOString();
    this.memoryStore.conversations.push({
      id: newId,
      is_group: false,
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

  public async createGroupConversation(
    title: string,
    creatorId: string,
    memberIds: string[],
    avatarUrl?: string
  ): Promise<Conversation> {
    const id = uuidv4();
    const now = new Date().toISOString();
    const conv: Conversation = {
      id,
      is_group: true,
      title,
      avatar_url: avatarUrl || '',
      created_by: creatorId,
      created_at: now,
      updated_at: now
    };
    this.memoryStore.conversations.push(conv);

    const allMemberIds = Array.from(new Set([creatorId, ...memberIds]));
    for (const mId of allMemberIds) {
      this.memoryStore.conversation_members.push({
        conversation_id: id,
        user_id: mId,
        is_pinned: false,
        is_archived: false,
        is_muted: false
      });
    }
    this.saveToLocal();

    if (this.pgPool) {
      try {
        await this.ensureUserExists(creatorId);
        await this.pgPool.query(
          'INSERT INTO conversations (id, is_group, title, avatar_url, created_by, created_at, updated_at) VALUES ($1, $2, $3, $4, $5, $6, $7)',
          [id, true, title, avatarUrl || null, creatorId, now, now]
        );
        for (const mId of allMemberIds) {
          await this.ensureUserExists(mId);
          await this.pgPool.query(
            'INSERT INTO conversation_members (conversation_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING',
            [id, mId]
          );
        }
      } catch (e) {
        console.warn('[Database] Erro PG createGroupConversation:', e);
      }
    }
    return conv;
  }

  public async updateGroupConversation(
    conversationId: string,
    title?: string,
    avatarUrl?: string
  ): Promise<Conversation | null> {
    let conv = this.memoryStore.conversations.find(c => c.id === conversationId);
    const now = new Date().toISOString();
    if (conv) {
      if (title !== undefined) conv.title = title;
      if (avatarUrl !== undefined) conv.avatar_url = avatarUrl;
      conv.updated_at = now;
      this.saveToLocal();
    }

    if (this.pgPool) {
      try {
        const fields: string[] = [];
        const params: any[] = [conversationId];
        let idx = 2;
        if (title !== undefined) {
          fields.push(`title = $${idx++}`);
          params.push(title);
        }
        if (avatarUrl !== undefined) {
          fields.push(`avatar_url = $${idx++}`);
          params.push(avatarUrl);
        }
        fields.push(`updated_at = $${idx++}`);
        params.push(now);

        if (fields.length > 1) {
          await this.pgPool.query(
            `UPDATE conversations SET ${fields.join(', ')} WHERE id = $1`,
            params
          );
        }
      } catch (e) {
        console.warn('[Database] Erro PG updateGroupConversation:', e);
      }
    }

    return conv || null;
  }

  public async getConversationsForUser(userId: string) {
    if (this.pgPool) {
      try {
        await this.ensureUserExists(userId);
        const res = await this.pgPool.query(`
          SELECT 
            cm.conversation_id,
            c.is_group,
            c.title AS group_title,
            c.avatar_url AS group_avatar_url,
            c.created_by,
            c.admin_only_message,
            c.admin_only_edit_info,
            c.admin_only_add_members,
            cm.is_pinned,
            cm.is_archived,
            cm.is_muted,
            c.updated_at,
            other_m.user_id AS other_user_id,
            u.username AS other_username,
            p.display_name AS other_display_name,
            p.avatar_url AS other_avatar_url,
            p.public_key AS other_public_key
          FROM conversation_members cm
          JOIN conversations c ON c.id = cm.conversation_id
          LEFT JOIN conversation_members other_m ON other_m.conversation_id = cm.conversation_id AND other_m.user_id != $1 AND (c.is_group IS FALSE OR c.is_group IS NULL)
          LEFT JOIN users u ON u.id = other_m.user_id
          LEFT JOIN user_profiles p ON p.user_id = other_m.user_id
          WHERE cm.user_id = $1
          ORDER BY cm.is_pinned DESC, c.updated_at DESC
        `, [userId]);

        const list = [];
        for (const row of res.rows) {
          const convId = row.conversation_id;
          
          // Last message
          const lastMsgRes = await this.pgPool.query(`
            SELECT * FROM messages 
            WHERE conversation_id = $1 AND is_deleted = false 
            ORDER BY created_at DESC LIMIT 1
          `, [convId]);

          let lastMessage: Message | null = null;
          if (lastMsgRes.rows.length > 0) {
            const m = lastMsgRes.rows[0];
            lastMessage = {
              id: m.id,
              conversation_id: m.conversation_id,
              sender_id: m.sender_id,
              content: m.content,
              iv: m.iv || undefined,
              message_type: m.message_type,
              reply_to_id: m.reply_to_id || undefined,
              is_deleted: m.is_deleted,
              is_edited: m.is_edited,
              created_at: m.created_at?.toISOString?.() || m.created_at,
              updated_at: m.updated_at?.toISOString?.() || m.updated_at
            };
          }

          // Unread count
          const unreadRes = await this.pgPool.query(`
            SELECT COUNT(*)::int AS count FROM messages m
            WHERE m.conversation_id = $1 
              AND m.sender_id != $2
              AND NOT EXISTS (
                SELECT 1 FROM message_receipts r 
                WHERE r.message_id = m.id AND r.user_id = $2 AND r.status = 'read'
              )
          `, [convId, userId]);

          const isGroup = !!row.is_group;
          list.push({
            conversationId: convId,
            isPinned: !!row.is_pinned,
            isArchived: !!row.is_archived,
            isMuted: !!row.is_muted,
            isGroup,
            title: isGroup ? row.group_title : undefined,
            avatarUrl: isGroup ? row.group_avatar_url : undefined,
            adminOnlyMessage: isGroup ? !!row.admin_only_message : false,
            adminOnlyEditInfo: isGroup ? !!row.admin_only_edit_info : false,
            adminOnlyAddMembers: isGroup ? !!row.admin_only_add_members : false,
            otherUser: isGroup ? {
              id: convId,
              username: row.group_title || 'Grupo',
              displayName: row.group_title || 'Grupo',
              avatarUrl: row.group_avatar_url || null,
              publicKey: null
            } : {
              id: row.other_user_id || 'unknown',
              username: row.other_username || 'desconhecido',
              displayName: row.other_display_name || row.other_username || 'Desconhecido',
              avatarUrl: row.other_avatar_url || null,
              publicKey: row.other_public_key || null
            },
            lastMessage,
            unreadCount: unreadRes.rows[0]?.count || 0,
            updatedAt: row.updated_at?.toISOString?.() || row.updated_at
          });
        }

        return list;
      } catch (e) {
        console.warn('[Database] Erro PG getConversationsForUser:', e);
      }
    }

    // Memory fallback
    const memberships = this.memoryStore.conversation_members.filter(m => m.user_id === userId);
    const results = [];

    for (const mem of memberships) {
      const conv = this.memoryStore.conversations.find(c => c.id === mem.conversation_id);
      if (!conv) continue;

      const convMessages = this.memoryStore.messages
        .filter(m => m.conversation_id === mem.conversation_id && !m.is_deleted)
        .sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());

      const lastMessage = convMessages[0] || null;

      const unreadCount = convMessages.filter(m => {
        if (m.sender_id === userId) return false;
        const receipt = this.memoryStore.message_receipts.find(r => r.message_id === m.id && r.user_id === userId && r.status === 'read');
        return !receipt;
      }).length;

      if (conv.is_group) {
        results.push({
          conversationId: mem.conversation_id,
          isPinned: mem.is_pinned,
          isArchived: mem.is_archived,
          isMuted: mem.is_muted,
          isGroup: true,
          title: conv.title,
          avatarUrl: conv.avatar_url,
          adminOnlyMessage: !!conv.admin_only_message,
          adminOnlyEditInfo: !!conv.admin_only_edit_info,
          adminOnlyAddMembers: !!conv.admin_only_add_members,
          otherUser: {
            id: conv.id,
            username: conv.title || 'Grupo',
            displayName: conv.title || 'Grupo',
            avatarUrl: conv.avatar_url || null,
            publicKey: null
          },
          lastMessage,
          unreadCount,
          updatedAt: conv.updated_at
        });
        continue;
      }

      const otherMem = this.memoryStore.conversation_members.find(
        m => m.conversation_id === mem.conversation_id && m.user_id !== userId
      );
      if (!otherMem) continue;

      const otherUser = this.memoryStore.users.find(u => u.id === otherMem.user_id);
      const otherProfile = this.memoryStore.profiles.find(p => p.user_id === otherMem.user_id);

      results.push({
        conversationId: mem.conversation_id,
        isPinned: mem.is_pinned,
        isArchived: mem.is_archived,
        isMuted: mem.is_muted,
        isGroup: false,
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
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query(`
          SELECT 1 FROM conversation_members WHERE conversation_id = $1 AND user_id = $2 LIMIT 1
        `, [conversationId, userId]);
        if (res.rows.length > 0) return true;
      } catch (e) {
        console.warn('[Database] Erro PG isUserInConversation:', e);
      }
    }
    return this.memoryStore.conversation_members.some(
      m => m.conversation_id === conversationId && m.user_id === userId
    );
  }

  public async getConversationMembers(conversationId: string): Promise<string[]> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query(`
          SELECT user_id FROM conversation_members WHERE conversation_id = $1
        `, [conversationId]);
        if (res.rows.length > 0) {
          return res.rows.map(r => r.user_id);
        }
      } catch (e) {
        console.warn('[Database] Erro PG getConversationMembers:', e);
      }
    }
    return this.memoryStore.conversation_members
      .filter(m => m.conversation_id === conversationId)
      .map(m => m.user_id);
  }

  public async getConversationById(conversationId: string): Promise<Conversation | null> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT * FROM conversations WHERE id = $1', [conversationId]);
        if (res.rows.length > 0) return res.rows[0];
      } catch (e) {
        console.warn('[Database] Erro PG getConversationById:', e);
      }
    }
    return this.memoryStore.conversations.find(c => c.id === conversationId) || null;
  }

  public async addConversationMembers(conversationId: string, memberIds: string[]): Promise<string[]> {
    for (const mId of memberIds) {
      if (!this.memoryStore.conversation_members.some(m => m.conversation_id === conversationId && m.user_id === mId)) {
        this.memoryStore.conversation_members.push({
          conversation_id: conversationId,
          user_id: mId,
          is_pinned: false,
          is_archived: false,
          is_muted: false
        });
      }
    }
    this.saveToLocal();

    if (this.pgPool) {
      try {
        for (const mId of memberIds) {
          await this.ensureUserExists(mId);
          await this.pgPool.query(
            'INSERT INTO conversation_members (conversation_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING',
            [conversationId, mId]
          );
        }
      } catch (e) {
        console.warn('[Database] Erro PG addConversationMembers:', e);
      }
    }
    return this.getConversationMembers(conversationId);
  }

  public async removeConversationMember(conversationId: string, memberId: string): Promise<boolean> {
    this.memoryStore.conversation_members = this.memoryStore.conversation_members.filter(
      m => !(m.conversation_id === conversationId && m.user_id === memberId)
    );
    this.saveToLocal();

    if (this.pgPool) {
      try {
        await this.pgPool.query(
          'DELETE FROM conversation_members WHERE conversation_id = $1 AND user_id = $2',
          [conversationId, memberId]
        );
      } catch (e) {
        console.warn('[Database] Erro PG removeConversationMember:', e);
      }
    }
    return true;
  }

  public async isUserGroupAdmin(conversationId: string, userId: string): Promise<boolean> {
    const conv = await this.getConversationById(conversationId);
    if (!conv) return false;
    if (conv.created_by === userId) return true;

    if (this.pgPool) {
      try {
        const res = await this.pgPool.query(
          'SELECT is_admin FROM conversation_members WHERE conversation_id = $1 AND user_id = $2',
          [conversationId, userId]
        );
        if (res.rows.length > 0 && res.rows[0].is_admin) return true;
      } catch (e) {
        console.warn('[Database] Erro PG isUserGroupAdmin:', e);
      }
    }
    const mem = this.memoryStore.conversation_members.find(
      m => m.conversation_id === conversationId && m.user_id === userId
    );
    return mem?.is_admin === true;
  }

  public async setGroupMemberAdmin(conversationId: string, userId: string, isAdmin: boolean): Promise<boolean> {
    const mem = this.memoryStore.conversation_members.find(
      m => m.conversation_id === conversationId && m.user_id === userId
    );
    if (mem) {
      mem.is_admin = isAdmin;
      this.saveToLocal();
    }

    if (this.pgPool) {
      try {
        await this.pgPool.query(
          'UPDATE conversation_members SET is_admin = $1 WHERE conversation_id = $2 AND user_id = $3',
          [isAdmin, conversationId, userId]
        );
      } catch (e) {
        console.warn('[Database] Erro PG setGroupMemberAdmin:', e);
      }
    }
    return true;
  }

  public async updateGroupPermissions(
    conversationId: string,
    permissions: { adminOnlyMessage?: boolean; adminOnlyEditInfo?: boolean; adminOnlyAddMembers?: boolean }
  ): Promise<Conversation | null> {
    let conv = this.memoryStore.conversations.find(c => c.id === conversationId);
    const now = new Date().toISOString();
    if (conv) {
      if (permissions.adminOnlyMessage !== undefined) conv.admin_only_message = permissions.adminOnlyMessage;
      if (permissions.adminOnlyEditInfo !== undefined) conv.admin_only_edit_info = permissions.adminOnlyEditInfo;
      if (permissions.adminOnlyAddMembers !== undefined) conv.admin_only_add_members = permissions.adminOnlyAddMembers;
      conv.updated_at = now;
      this.saveToLocal();
    }

    if (this.pgPool) {
      try {
        const fields: string[] = [];
        const params: any[] = [conversationId];
        let idx = 2;
        if (permissions.adminOnlyMessage !== undefined) {
          fields.push(`admin_only_message = $${idx++}`);
          params.push(permissions.adminOnlyMessage);
        }
        if (permissions.adminOnlyEditInfo !== undefined) {
          fields.push(`admin_only_edit_info = $${idx++}`);
          params.push(permissions.adminOnlyEditInfo);
        }
        if (permissions.adminOnlyAddMembers !== undefined) {
          fields.push(`admin_only_add_members = $${idx++}`);
          params.push(permissions.adminOnlyAddMembers);
        }
        fields.push(`updated_at = $${idx++}`);
        params.push(now);

        if (fields.length > 1) {
          await this.pgPool.query(
            `UPDATE conversations SET ${fields.join(', ')} WHERE id = $1`,
            params
          );
        }
      } catch (e) {
        console.warn('[Database] Erro PG updateGroupPermissions:', e);
      }
    }
    return await this.getConversationById(conversationId);
  }

  public async getConversationMemberDetails(conversationId: string): Promise<Array<{ userId: string; isAdmin: boolean }>> {
    const conv = await this.getConversationById(conversationId);
    const creatorId = conv?.created_by;

    if (this.pgPool) {
      try {
        const res = await this.pgPool.query(
          'SELECT user_id, is_admin FROM conversation_members WHERE conversation_id = $1',
          [conversationId]
        );
        if (res.rows.length > 0) {
          return res.rows.map(r => ({
            userId: r.user_id,
            isAdmin: r.user_id === creatorId || r.is_admin === true
          }));
        }
      } catch (e) {
        console.warn('[Database] Erro PG getConversationMemberDetails:', e);
      }
    }

    return this.memoryStore.conversation_members
      .filter(m => m.conversation_id === conversationId)
      .map(m => ({
        userId: m.user_id,
        isAdmin: m.user_id === creatorId || m.is_admin === true
      }));
  }

  // ==========================================
  // MESSAGES
  // ==========================================
  public async createMessage(msg: Message): Promise<Message> {
    // Memory
    const existing = this.memoryStore.messages.find(m => m.id === msg.id);
    if (!existing) {
      this.memoryStore.messages.push(msg);
      const conv = this.memoryStore.conversations.find(c => c.id === msg.conversation_id);
      if (conv) {
        conv.updated_at = msg.created_at;
      }
      this.saveToLocal();
    }

    // PG
    if (this.pgPool) {
      try {
        // Ensure conversation and sender exist to prevent foreign key errors
        await this.ensureConversationMember(msg.conversation_id, msg.sender_id);

        await this.pgPool.query(`
          INSERT INTO messages (id, conversation_id, sender_id, content, iv, message_type, reply_to_id, is_deleted, is_edited, created_at, updated_at)
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
          ON CONFLICT (id) DO NOTHING
        `, [
          msg.id,
          msg.conversation_id,
          msg.sender_id,
          msg.content,
          msg.iv || null,
          msg.message_type || 'text',
          msg.reply_to_id || null,
          msg.is_deleted || false,
          msg.is_edited || false,
          msg.created_at,
          msg.updated_at
        ]);

        await this.pgPool.query(`
          UPDATE conversations SET updated_at = $1 WHERE id = $2
        `, [msg.created_at, msg.conversation_id]);
      } catch (e) {
        console.warn('[Database] Erro PG createMessage:', e);
      }
    }

    return msg;
  }

  public async getMessages(conversationId: string, limit = 50, beforeTimestamp?: string): Promise<Message[]> {
    if (this.pgPool) {
      try {
        let query = `
          SELECT * FROM messages
          WHERE conversation_id = $1 AND is_deleted = false
        `;
        const params: any[] = [conversationId];

        if (beforeTimestamp) {
          query += ` AND created_at < $2`;
          params.push(beforeTimestamp);
          query += ` ORDER BY created_at DESC LIMIT $3`;
          params.push(limit);
        } else {
          query += ` ORDER BY created_at DESC LIMIT $2`;
          params.push(limit);
        }

        const res = await this.pgPool.query(query, params);
        return res.rows.map(m => ({
          id: m.id,
          conversation_id: m.conversation_id,
          sender_id: m.sender_id,
          content: m.content,
          iv: m.iv || undefined,
          message_type: m.message_type,
          reply_to_id: m.reply_to_id || undefined,
          is_deleted: m.is_deleted,
          is_edited: m.is_edited,
          created_at: m.created_at?.toISOString?.() || m.created_at,
          updated_at: m.updated_at?.toISOString?.() || m.updated_at
        })).reverse();
      } catch (e) {
        console.warn('[Database] Erro PG getMessages:', e);
      }
    }

    let filtered = this.memoryStore.messages.filter(m => m.conversation_id === conversationId && !m.is_deleted);
    if (beforeTimestamp) {
      const beforeTime = new Date(beforeTimestamp).getTime();
      filtered = filtered.filter(m => new Date(m.created_at).getTime() < beforeTime);
    }
    filtered.sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
    return filtered.slice(0, limit).reverse();
  }

  public async getMessageById(messageId: string): Promise<Message | null> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT * FROM messages WHERE id = $1', [messageId]);
        if (res.rows.length > 0) {
          const m = res.rows[0];
          return {
            id: m.id,
            conversation_id: m.conversation_id,
            sender_id: m.sender_id,
            content: m.content,
            iv: m.iv || undefined,
            message_type: m.message_type,
            reply_to_id: m.reply_to_id || undefined,
            is_deleted: m.is_deleted,
            is_edited: m.is_edited,
            created_at: m.created_at?.toISOString?.() || m.created_at,
            updated_at: m.updated_at?.toISOString?.() || m.updated_at
          };
        }
      } catch (e) {
        console.warn('[Database] Erro PG getMessageById:', e);
      }
    }
    return this.memoryStore.messages.find(m => m.id === messageId) || null;
  }

  public async getPendingMessagesForUser(userId: string): Promise<Message[]> {
    if (this.pgPool) {
      try {
        const query = `
          SELECT m.*
          FROM messages m
          JOIN conversation_members cm ON m.conversation_id = cm.conversation_id
          WHERE cm.user_id = $1
            AND m.sender_id != $1
            AND m.is_deleted = false
            AND NOT EXISTS (
              SELECT 1 FROM message_receipts r
              WHERE r.message_id = m.id AND r.user_id = $1 AND r.status IN ('delivered', 'read')
            )
            AND NOT EXISTS (
              SELECT 1 FROM blocked_users b
              WHERE (b.blocker_id = $1 AND b.blocked_id = m.sender_id)
                 OR (b.blocker_id = m.sender_id AND b.blocked_id = $1)
            )
          ORDER BY m.created_at ASC
          LIMIT 100
        `;
        const res = await this.pgPool.query(query, [userId]);
        return res.rows.map(m => ({
          id: m.id,
          conversation_id: m.conversation_id,
          sender_id: m.sender_id,
          content: m.content,
          iv: m.iv || undefined,
          message_type: m.message_type,
          reply_to_id: m.reply_to_id || undefined,
          is_deleted: m.is_deleted,
          is_edited: m.is_edited,
          created_at: m.created_at?.toISOString?.() || m.created_at,
          updated_at: m.updated_at?.toISOString?.() || m.updated_at
        }));
      } catch (e) {
        console.warn('[Database] Erro PG getPendingMessagesForUser:', e);
      }
    }

    const userConvs = this.memoryStore.conversation_members.filter(cm => cm.user_id === userId).map(cm => cm.conversation_id);
    return this.memoryStore.messages.filter(m => {
      if (!userConvs.includes(m.conversation_id) || m.sender_id === userId || m.is_deleted) return false;
      if (this.memoryStore.blocked_users.some(b => 
        (b.blocker_id === userId && b.blocked_id === m.sender_id) ||
        (b.blocker_id === m.sender_id && b.blocked_id === userId)
      )) return false;
      const hasReceipt = this.memoryStore.message_receipts.some(r => r.message_id === m.id && r.user_id === userId && (r.status === 'delivered' || r.status === 'read'));
      return !hasReceipt;
    });
  }

  public async editMessage(messageId: string, senderId: string, newContent: string): Promise<Message | null> {
    const msg = this.memoryStore.messages.find(m => m.id === messageId && m.sender_id === senderId);
    if (msg) {
      msg.content = newContent;
      msg.is_edited = true;
      msg.updated_at = new Date().toISOString();
      this.saveToLocal();
    }

    if (this.pgPool) {
      try {
        const res = await this.pgPool.query(`
          UPDATE messages 
          SET content = $1, is_edited = true, updated_at = NOW()
          WHERE id = $2 AND sender_id = $3
          RETURNING *
        `, [newContent, messageId, senderId]);
        if (res.rows.length > 0) {
          const m = res.rows[0];
          return {
            id: m.id,
            conversation_id: m.conversation_id,
            sender_id: m.sender_id,
            content: m.content,
            iv: m.iv || undefined,
            message_type: m.message_type,
            reply_to_id: m.reply_to_id || undefined,
            is_deleted: m.is_deleted,
            is_edited: m.is_edited,
            created_at: m.created_at?.toISOString?.() || m.created_at,
            updated_at: m.updated_at?.toISOString?.() || m.updated_at
          };
        }
      } catch (e) {
        console.warn('[Database] Erro PG editMessage:', e);
      }
    }

    return msg || null;
  }

  public async deleteMessage(messageId: string, senderId?: string): Promise<boolean> {
    const msg = this.memoryStore.messages.find(m => m.id === messageId && (!senderId || m.sender_id === senderId));
    if (msg) {
      msg.is_deleted = true;
      msg.content = 'Mensagem apagada';
      msg.updated_at = new Date().toISOString();
      this.saveToLocal();
    }

    if (this.pgPool) {
      try {
        if (senderId) {
          await this.pgPool.query(`
            UPDATE messages 
            SET is_deleted = true, content = 'Mensagem apagada', updated_at = NOW()
            WHERE id = $1 AND sender_id = $2
          `, [messageId, senderId]);
        } else {
          await this.pgPool.query(`
            UPDATE messages 
            SET is_deleted = true, content = 'Mensagem apagada', updated_at = NOW()
            WHERE id = $1
          `, [messageId]);
        }
        return true;
      } catch (e) {
        console.warn('[Database] Erro PG deleteMessage:', e);
      }
    }

    return !!msg;
  }

  public async addOrToggleReaction(messageId: string, userId: string, emoji: string): Promise<string> {
    const msg = this.memoryStore.messages.find(m => m.id === messageId);
    let reactions: { emoji: string; userId: string }[] = [];
    if (msg?.reactions) {
      try {
        reactions = JSON.parse(msg.reactions);
      } catch (e) {
        reactions = [];
      }
    }
    const existingIndex = reactions.findIndex(r => r.userId === userId);
    if (existingIndex >= 0) {
      if (reactions[existingIndex].emoji === emoji) {
        reactions.splice(existingIndex, 1);
      } else {
        reactions[existingIndex].emoji = emoji;
      }
    } else {
      reactions.push({ emoji, userId });
    }
    const reactionsStr = JSON.stringify(reactions);
    if (msg) {
      msg.reactions = reactionsStr;
      this.saveToLocal();
    }

    if (this.pgPool) {
      try {
        await this.pgPool.query(`
          UPDATE messages SET reactions = $1 WHERE id = $2
        `, [reactionsStr, messageId]);
      } catch (e) {
        console.warn('[Database] Erro PG addOrToggleReaction:', e);
      }
    }

    return reactionsStr;
  }

  // ==========================================
  // RECEIPTS
  // ==========================================
  public async addReceipt(receipt: MessageReceipt) {
    const existing = this.memoryStore.message_receipts.find(
      r => r.message_id === receipt.message_id && r.user_id === receipt.user_id && r.status === receipt.status
    );
    if (!existing) {
      this.memoryStore.message_receipts.push(receipt);
      this.saveToLocal();
    }

    if (this.pgPool) {
      try {
        await this.ensureUserExists(receipt.user_id);
        await this.pgPool.query(`
          INSERT INTO message_receipts (id, message_id, user_id, status, timestamp)
          VALUES ($1, $2, $3, $4, $5)
          ON CONFLICT (message_id, user_id, status) DO NOTHING
        `, [receipt.id, receipt.message_id, receipt.user_id, receipt.status, receipt.timestamp]);
      } catch (e) {
        console.warn('[Database] Erro PG addReceipt:', e);
      }
    }
  }

  public async getReceiptsForMessage(messageId: string): Promise<MessageReceipt[]> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query(`
          SELECT * FROM message_receipts WHERE message_id = $1
        `, [messageId]);
        if (res.rows.length > 0) {
          return res.rows.map(r => ({
            id: r.id,
            message_id: r.message_id,
            user_id: r.user_id,
            status: r.status,
            timestamp: r.timestamp?.toISOString?.() || r.timestamp
          }));
        }
      } catch (e) {
        console.warn('[Database] Erro PG getReceiptsForMessage:', e);
      }
    }
    return this.memoryStore.message_receipts.filter(r => r.message_id === messageId);
  }

  // ==========================================
  // CALLS
  // ==========================================
  public async recordCall(record: CallHistoryRecord) {
    this.memoryStore.call_history.push(record);
    this.saveToLocal();

    if (this.pgPool) {
      try {
        await this.ensureUserExists(record.caller_id);
        await this.ensureUserExists(record.callee_id);
        await this.pgPool.query(`
          INSERT INTO call_history (id, caller_id, callee_id, status, started_at, ended_at, duration_seconds)
          VALUES ($1, $2, $3, $4, $5, $6, $7)
          ON CONFLICT (id) DO NOTHING
        `, [record.id, record.caller_id, record.callee_id, record.status, record.started_at, record.ended_at || null, record.duration_seconds || 0]);
      } catch (e) {
        console.warn('[Database] Erro PG recordCall:', e);
      }
    }
    return record;
  }

  public async getCallHistory(userId: string, limit = 50): Promise<CallHistoryRecord[]> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query(`
          SELECT * FROM call_history
          WHERE caller_id = $1 OR callee_id = $1
          ORDER BY started_at DESC
          LIMIT $2
        `, [userId, limit]);
        return res.rows.map(r => ({
          id: r.id,
          caller_id: r.caller_id,
          callee_id: r.callee_id,
          status: r.status,
          started_at: r.started_at?.toISOString?.() || r.started_at,
          ended_at: r.ended_at?.toISOString?.() || r.ended_at,
          duration_seconds: r.duration_seconds || 0
        }));
      } catch (e) {
        console.warn('[Database] Erro PG getCallHistory:', e);
      }
    }

    return this.memoryStore.call_history
      .filter(c => c.caller_id === userId || c.callee_id === userId)
      .sort((a, b) => new Date(b.started_at).getTime() - new Date(a.started_at).getTime())
      .slice(0, limit);
  }

  public async deleteCallHistory(userId: string): Promise<boolean> {
    if (this.pgPool) {
      try {
        await this.pgPool.query(`
          DELETE FROM call_history WHERE caller_id = $1 OR callee_id = $1
        `, [userId]);
        return true;
      } catch (e) {
        console.warn('[Database] Erro PG deleteCallHistory:', e);
      }
    }
    this.memoryStore.call_history = this.memoryStore.call_history.filter(
      c => c.caller_id !== userId && c.callee_id !== userId
    );
    this.saveToLocal();
    return true;
  }

  public async markConversationAsRead(conversationId: string, userId: string): Promise<boolean> {
    if (this.pgPool) {
      try {
        await this.pgPool.query(`
          INSERT INTO message_receipts (id, message_id, user_id, status, timestamp)
          SELECT gen_random_uuid()::varchar, m.id, $2, 'read', NOW()
          FROM messages m
          WHERE m.conversation_id = $1 AND m.sender_id != $2
          ON CONFLICT (message_id, user_id, status) DO NOTHING
        `, [conversationId, userId]);

        await this.pgPool.query(`
          UPDATE conversation_members
          SET last_read_at = NOW()
          WHERE conversation_id = $1 AND user_id = $2
        `, [conversationId, userId]);
        return true;
      } catch (e) {
        console.warn('[Database] Erro PG markConversationAsRead:', e);
      }
    }

    const msgs = this.memoryStore.messages.filter(m => m.conversation_id === conversationId && m.sender_id !== userId);
    for (const m of msgs) {
      const exists = this.memoryStore.message_receipts.some(r => r.message_id === m.id && r.user_id === userId && r.status === 'read');
      if (!exists) {
        this.memoryStore.message_receipts.push({
          id: uuidv4(),
          message_id: m.id,
          user_id: userId,
          status: 'read',
          timestamp: new Date().toISOString()
        });
      }
    }
    this.saveToLocal();
    return true;
  }

  public async clearConversationMessages(conversationId: string): Promise<boolean> {
    if (this.pgPool) {
      try {
        await this.pgPool.query(`DELETE FROM messages WHERE conversation_id = $1`, [conversationId]);
        return true;
      } catch (e) {
        console.warn('[Database] Erro PG clearConversationMessages:', e);
      }
    }
    this.memoryStore.messages = this.memoryStore.messages.filter(m => m.conversation_id !== conversationId);
    this.saveToLocal();
    return true;
  }

  public async deleteConversationForUser(conversationId: string, userId: string): Promise<boolean> {
    // 1. Remove from memory store
    const otherMem = this.memoryStore.conversation_members.find(
      m => m.conversation_id === conversationId && m.user_id !== userId
    );
    if (otherMem) {
      this.memoryStore.contacts = this.memoryStore.contacts.filter(
        c => !(c.user_id === userId && c.contact_user_id === otherMem.user_id)
      );
    }
    this.memoryStore.conversation_members = this.memoryStore.conversation_members.filter(
      m => !(m.conversation_id === conversationId && m.user_id === userId)
    );
    this.memoryStore.messages = this.memoryStore.messages.filter(m => m.conversation_id !== conversationId);
    const remainingMems = this.memoryStore.conversation_members.filter(m => m.conversation_id === conversationId);
    if (remainingMems.length === 0) {
      this.memoryStore.conversations = this.memoryStore.conversations.filter(c => c.id !== conversationId);
    }
    this.saveToLocal();

    // 2. Remove from PostgreSQL
    if (this.pgPool) {
      try {
        const otherRes = await this.pgPool.query(
          'SELECT user_id FROM conversation_members WHERE conversation_id = $1 AND user_id != $2',
          [conversationId, userId]
        );
        for (const row of otherRes.rows) {
          await this.pgPool.query(
            'DELETE FROM contacts WHERE user_id = $1 AND contact_user_id = $2',
            [userId, row.user_id]
          );
        }
        await this.pgPool.query(
          'DELETE FROM conversation_members WHERE conversation_id = $1 AND user_id = $2',
          [conversationId, userId]
        );
        await this.pgPool.query('DELETE FROM messages WHERE conversation_id = $1', [conversationId]);
        await this.pgPool.query(`
          DELETE FROM conversations 
          WHERE id = $1 AND (SELECT COUNT(*) FROM conversation_members WHERE conversation_id = $1) = 0
        `, [conversationId]);
        return true;
      } catch (e) {
        console.warn('[Database] Erro PG deleteConversationForUser:', e);
      }
    }
    return true;
  }

  // ==========================================
  // PREFERENCES
  // ==========================================
  public async getPreferences(userId: string): Promise<UserPreferences> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT * FROM user_preferences WHERE user_id = $1', [userId]);
        if (res.rows.length > 0) {
          const r = res.rows[0];
          return {
            user_id: r.user_id,
            notifications_enabled: r.notifications_enabled,
            read_receipts_enabled: r.read_receipts_enabled,
            typing_indicator_enabled: r.typing_indicator_enabled,
            theme: r.theme || 'system',
            updated_at: r.updated_at?.toISOString?.() || r.updated_at
          };
        }
      } catch (e) {
        console.warn('[Database] Erro PG getPreferences:', e);
      }
    }

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

    if (this.pgPool) {
      try {
        await this.ensureUserExists(userId);
        await this.pgPool.query(`
          INSERT INTO user_preferences (user_id, notifications_enabled, read_receipts_enabled, typing_indicator_enabled, theme, updated_at)
          VALUES ($1, $2, $3, $4, $5, NOW())
          ON CONFLICT (user_id) DO UPDATE SET
            notifications_enabled = EXCLUDED.notifications_enabled,
            read_receipts_enabled = EXCLUDED.read_receipts_enabled,
            typing_indicator_enabled = EXCLUDED.typing_indicator_enabled,
            theme = EXCLUDED.theme,
            updated_at = NOW()
        `, [
          userId,
          current.notifications_enabled,
          current.read_receipts_enabled,
          current.typing_indicator_enabled,
          current.theme
        ]);
      } catch (e) {
        console.warn('[Database] Erro PG updatePreferences:', e);
      }
    }

    return current;
  }

  // ==========================================
  // BACKUP METADATA
  // ==========================================
  public async getBackupMetadata(userId: string): Promise<BackupMetadata | null> {
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT * FROM backup_metadata WHERE user_id = $1', [userId]);
        if (res.rows.length > 0) {
          const r = res.rows[0];
          return {
            user_id: r.user_id,
            drive_file_id: r.drive_file_id,
            backup_size_bytes: parseInt(r.backup_size_bytes, 10) || 0,
            version: r.version || 1,
            checksum: r.checksum,
            last_backup_at: r.last_backup_at?.toISOString?.() || r.last_backup_at
          };
        }
      } catch (e) {
        console.warn('[Database] Erro PG getBackupMetadata:', e);
      }
    }
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

    if (this.pgPool) {
      try {
        await this.ensureUserExists(meta.user_id);
        await this.pgPool.query(`
          INSERT INTO backup_metadata (user_id, drive_file_id, backup_size_bytes, version, checksum, last_backup_at)
          VALUES ($1, $2, $3, $4, $5, $6)
          ON CONFLICT (user_id) DO UPDATE SET
            drive_file_id = EXCLUDED.drive_file_id,
            backup_size_bytes = EXCLUDED.backup_size_bytes,
            version = EXCLUDED.version,
            checksum = EXCLUDED.checksum,
            last_backup_at = EXCLUDED.last_backup_at
        `, [meta.user_id, meta.drive_file_id, meta.backup_size_bytes, meta.version, meta.checksum, meta.last_backup_at]);
      } catch (e) {
        console.warn('[Database] Erro PG saveBackupMetadata:', e);
      }
    }
    return meta;
  }

  // ==========================================
  // STATUS / STORIES
  // ==========================================
  public async createStatus(status: StatusRecord): Promise<StatusRecord> {
    this.memoryStore.statuses.push(status);
    this.saveToLocal();

    if (this.pgPool) {
      try {
        await this.ensureUserExists(status.user_id, status.username);
        await this.pgPool.query(`
          INSERT INTO statuses (id, user_id, content_type, content, caption, background_color, created_at, expires_at)
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
        `, [
          status.id,
          status.user_id,
          status.content_type,
          status.content,
          status.caption || null,
          status.background_color || null,
          status.created_at,
          status.expires_at
        ]);
      } catch (e) {
        this.handlePgError(e);
        console.warn('[Database] Erro PG createStatus:', e);
      }
    }
    return status;
  }

  public async getActiveStatuses(userId: string): Promise<StatusRecord[]> {
    const now = new Date().toISOString();

    if (this.pgPool) {
      try {
        const query = `
          SELECT s.*, u.username, up.display_name, up.avatar_url
          FROM statuses s
          JOIN users u ON u.id = s.user_id
          LEFT JOIN user_profiles up ON up.user_id = s.user_id
          WHERE s.expires_at > NOW()
            AND (
              s.user_id = $1
              OR s.user_id IN (SELECT contact_user_id FROM contacts WHERE user_id = $1)
              OR s.user_id IN (SELECT user_id FROM contacts WHERE contact_user_id = $1)
              OR s.user_id IN (
                SELECT cm2.user_id 
                FROM conversation_members cm1 
                JOIN conversation_members cm2 ON cm1.conversation_id = cm2.conversation_id 
                WHERE cm1.user_id = $1
              )
            )
          ORDER BY s.created_at ASC
        `;
        const res = await this.pgPool.query(query, [userId]);
        return res.rows.map(r => ({
          id: r.id,
          user_id: r.user_id,
          display_name: r.display_name || r.username,
          username: r.username,
          avatar_url: r.avatar_url,
          content_type: r.content_type,
          content: r.content,
          caption: r.caption,
          background_color: r.background_color,
          created_at: r.created_at?.toISOString?.() || r.created_at,
          expires_at: r.expires_at?.toISOString?.() || r.expires_at,
          reactions: typeof r.reactions === 'string' ? JSON.parse(r.reactions) : (r.reactions || []),
          views: typeof r.views === 'string' ? JSON.parse(r.views) : (r.views || [])
        }));
      } catch (e) {
        this.handlePgError(e);
        console.warn('[Database] Erro PG getActiveStatuses:', e);
      }
    }

    // Fallback in-memory (filter only user's own statuses and saved contacts)
    const userContacts = new Set(this.memoryStore.contacts.filter(c => c.user_id === userId).map(c => c.contact_user_id));
    const active = this.memoryStore.statuses.filter(s => s.expires_at > now && (s.user_id === userId || userContacts.has(s.user_id)));
    return active.map(s => {
      const prof = this.memoryStore.profiles.find(p => p.user_id === s.user_id);
      return {
        ...s,
        avatar_url: s.avatar_url || prof?.avatar_url,
        display_name: s.display_name || prof?.display_name || s.username,
        reactions: s.reactions || [],
        views: s.views || []
      };
    }).sort((a, b) => a.created_at.localeCompare(b.created_at));
  }

  public async getStatusById(statusId: string): Promise<StatusRecord | null> {
    const mem = this.memoryStore.statuses.find(s => s.id === statusId);
    if (mem) return mem;
    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT * FROM statuses WHERE id = $1', [statusId]);
        if (res.rows.length > 0) return res.rows[0];
      } catch (e) {
        this.handlePgError(e);
        console.warn('[Database] Erro PG getStatusById:', e);
      }
    }
    return null;
  }

  public async addStatusReaction(statusId: string, reaction: StatusReactionRecord): Promise<void> {
    const status = this.memoryStore.statuses.find(s => s.id === statusId);
    if (status) {
      if (!status.reactions) status.reactions = [];
      const existingIdx = status.reactions.findIndex(r => r.user_id === reaction.user_id);
      if (existingIdx >= 0) {
        status.reactions[existingIdx] = reaction;
      } else {
        status.reactions.push(reaction);
      }
      this.saveToLocal();
    }

    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT reactions FROM statuses WHERE id = $1', [statusId]);
        if (res.rows.length > 0) {
          let list: StatusReactionRecord[] = [];
          try {
            const raw = res.rows[0].reactions;
            list = typeof raw === 'string' ? JSON.parse(raw) : (raw || []);
          } catch (e) { list = []; }
          const idx = list.findIndex(r => r.user_id === reaction.user_id);
          if (idx >= 0) {
            list[idx] = reaction;
          } else {
            list.push(reaction);
          }
          await this.pgPool.query('UPDATE statuses SET reactions = $1 WHERE id = $2', [JSON.stringify(list), statusId]);
        }
      } catch (e) {
        this.handlePgError(e);
        console.warn('[Database] Erro PG addStatusReaction:', e);
      }
    }
  }

  public async addStatusView(statusId: string, view: StatusViewRecord): Promise<void> {
    const status = this.memoryStore.statuses.find(s => s.id === statusId);
    if (status) {
      if (!status.views) status.views = [];
      const existingIdx = status.views.findIndex(v => v.user_id === view.user_id);
      if (existingIdx >= 0) {
        status.views[existingIdx] = view;
      } else {
        status.views.push(view);
      }
      this.saveToLocal();
    }

    if (this.pgPool) {
      try {
        const res = await this.pgPool.query('SELECT views FROM statuses WHERE id = $1', [statusId]);
        if (res.rows.length > 0) {
          let list: StatusViewRecord[] = [];
          try {
            const raw = res.rows[0].views;
            list = typeof raw === 'string' ? JSON.parse(raw) : (raw || []);
          } catch (e) { list = []; }
          const idx = list.findIndex(v => v.user_id === view.user_id);
          if (idx >= 0) {
            list[idx] = view;
          } else {
            list.push(view);
          }
          await this.pgPool.query('UPDATE statuses SET views = $1 WHERE id = $2', [JSON.stringify(list), statusId]);
        }
      } catch (e) {
        this.handlePgError(e);
        console.warn('[Database] Erro PG addStatusView:', e);
      }
    }
  }

  public async deleteStatus(statusId: string, userId: string): Promise<boolean> {
    const idx = this.memoryStore.statuses.findIndex(s => s.id === statusId && s.user_id === userId);
    if (idx >= 0) {
      this.memoryStore.statuses.splice(idx, 1);
      this.saveToLocal();
    }

    if (this.pgPool) {
      try {
        await this.pgPool.query('DELETE FROM statuses WHERE id = $1 AND user_id = $2', [statusId, userId]);
      } catch (e) {
        this.handlePgError(e);
        console.warn('[Database] Erro PG deleteStatus:', e);
      }
    }
    return true;
  }
}

export const db = new DatabaseService();


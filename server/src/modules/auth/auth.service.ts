import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import crypto from 'crypto';
import { db, User, UserProfile, UserSession } from '../../db/database';
import { CONFIG } from '../../config/env';

// List of words for generating a secure 12-word recovery mnemonic
const MNEMONIC_WORDS = [
  'azul', 'brisa', 'campo', 'doce', 'estrela', 'fogo', 'gruta', 'horizonte',
  'ilha', 'jardim', 'leao', 'montanha', 'nuvem', 'oceano', 'pedra', 'rio',
  'sol', 'terra', 'vento', 'valente', 'arvore', 'lua', 'porto', 'cristal',
  'diamante', 'esperanca', 'floresta', 'harmonia', 'infinito', 'ouro', 'prata', 'sereno'
];

export function generateRecoveryKey(): string {
  const words: string[] = [];
  const randomBytes = crypto.randomBytes(12);
  for (let i = 0; i < 12; i++) {
    const idx = randomBytes[i] % MNEMONIC_WORDS.length;
    words.push(MNEMONIC_WORDS[idx]);
  }
  return words.join(' ');
}

export class AuthService {
  public static async register(data: {
    username: string;
    password: string;
    displayName: string;
    avatarUrl?: string;
    publicKey?: string;
    deviceName?: string;
    ipAddress?: string;
  }) {
    // 1. Validation
    const usernameRegex = /^[a-zA-Z0-9_]{3,30}$/;
    if (!usernameRegex.test(data.username)) {
      throw new Error('O nome de usuário deve ter entre 3 e 30 caracteres (letras, números ou _)');
    }
    if (!data.password || data.password.length < 6) {
      throw new Error('A senha deve ter no mínimo 6 caracteres.');
    }

    // 2. Check if username exists
    const existing = await db.findUserByUsername(data.username);
    if (existing) {
      throw new Error('Este nome de usuário já está em uso.');
    }

    // 3. Hash password and generate recovery mnemonic
    const passwordHash = await bcrypt.hash(data.password, 12);
    const recoveryKey = generateRecoveryKey();
    const recoveryHash = await bcrypt.hash(recoveryKey.trim().toLowerCase(), 10);

    const userId = uuidv4();
    const now = new Date().toISOString();

    const newUser: User = {
      id: userId,
      username: data.username.toLowerCase(),
      password_hash: passwordHash,
      recovery_hash: recoveryHash,
      created_at: now,
      updated_at: now
    };

    await db.createUser(newUser);

    // 4. Create Profile
    const profile: UserProfile = {
      user_id: userId,
      display_name: data.displayName || data.username,
      avatar_url: data.avatarUrl,
      public_key: data.publicKey,
      updated_at: now
    };
    await db.upsertProfile(profile);

    // 5. Create default preferences
    await db.getPreferences(userId);

    // 6. Generate Session & Tokens
    const sessionId = uuidv4();
    const refreshToken = uuidv4();
    const refreshTokenHash = await bcrypt.hash(refreshToken, 8);

    const session: UserSession = {
      id: sessionId,
      user_id: userId,
      device_name: data.deviceName || 'Android Device',
      ip_address: data.ipAddress || '127.0.0.1',
      refresh_token_hash: refreshTokenHash,
      last_active: now,
      created_at: now
    };
    await db.createSession(session);

    const accessToken = jwt.sign(
      { userId, username: newUser.username, sessionId },
      CONFIG.JWT_SECRET,
      { expiresIn: CONFIG.JWT_EXPIRES_IN as any }
    );

    return {
      user: {
        id: userId,
        username: newUser.username,
        displayName: profile.display_name,
        avatarUrl: profile.avatar_url,
        publicKey: profile.public_key,
        createdAt: newUser.created_at
      },
      recoveryKey, // Present once to the user to securely backup
      tokens: {
        accessToken,
        refreshToken,
        expiresIn: CONFIG.JWT_EXPIRES_IN
      }
    };
  }

  public static async login(data: {
    username: string;
    password: string;
    deviceName?: string;
    ipAddress?: string;
  }) {
    const user = await db.findUserByUsername(data.username);
    if (!user) {
      throw new Error('Nome de usuário ou senha incorretos.');
    }

    const matches = await bcrypt.compare(data.password, user.password_hash);
    if (!matches) {
      throw new Error('Nome de usuário ou senha incorretos.');
    }

    const profile = await db.getProfile(user.id);

    // Create session
    const sessionId = uuidv4();
    const refreshToken = uuidv4();
    const refreshTokenHash = await bcrypt.hash(refreshToken, 8);
    const now = new Date().toISOString();

    const session: UserSession = {
      id: sessionId,
      user_id: user.id,
      device_name: data.deviceName || 'Android Device',
      ip_address: data.ipAddress || '127.0.0.1',
      refresh_token_hash: refreshTokenHash,
      last_active: now,
      created_at: now
    };
    await db.createSession(session);

    const accessToken = jwt.sign(
      { userId: user.id, username: user.username, sessionId },
      CONFIG.JWT_SECRET,
      { expiresIn: CONFIG.JWT_EXPIRES_IN as any }
    );

    return {
      user: {
        id: user.id,
        username: user.username,
        displayName: profile?.display_name || user.username,
        avatarUrl: profile?.avatar_url,
        publicKey: profile?.public_key,
        createdAt: user.created_at
      },
      tokens: {
        accessToken,
        refreshToken,
        expiresIn: CONFIG.JWT_EXPIRES_IN
      }
    };
  }

  public static async recoverAccount(data: {
    username: string;
    recoveryKey: string;
    newPassword: string;
  }) {
    if (!data.newPassword || data.newPassword.length < 6) {
      throw new Error('A nova senha deve ter no mínimo 6 caracteres.');
    }

    const user = await db.findUserByUsername(data.username);
    if (!user) {
      throw new Error('Usuário não encontrado.');
    }

    const matches = await bcrypt.compare(data.recoveryKey.trim().toLowerCase(), user.recovery_hash);
    if (!matches) {
      throw new Error('Chave de recuperação inválida.');
    }

    const newHash = await bcrypt.hash(data.newPassword, 12);
    await db.updateUserPassword(user.id, newHash);

    // Revoke all existing sessions for security
    await db.revokeAllOtherSessions(user.id, '');

    return { success: true, message: 'Senha redefinida com sucesso. Faça login com a nova senha.' };
  }

  public static async getSessions(userId: string) {
    return db.getSessionsByUserId(userId);
  }

  public static async revokeSession(userId: string, sessionId: string) {
    return db.revokeSession(sessionId, userId);
  }

  public static async revokeAllOtherSessions(userId: string, currentSessionId: string) {
    return db.revokeAllOtherSessions(userId, currentSessionId);
  }

  public static async deleteAccount(userId: string) {
    return db.deleteUser(userId);
  }

  // ==========================================
  // VIRTUAL NUMBER & SMS VERIFICATION
  // ==========================================
  private static virtualNumberStore = new Map<string, { code: string; expiresAt: number }>();

  public static async requestVirtualNumberCode(virtualNumber: string) {
    const cleanNumber = virtualNumber.replace(/\D/g, '');
    if (cleanNumber.length !== 8) {
      throw new Error('O número virtual deve conter exatamente 8 dígitos numéricos.');
    }

    // 4-digit code
    const code = Math.floor(1000 + Math.random() * 9000).toString();
    this.virtualNumberStore.set(cleanNumber, {
      code,
      expiresAt: Date.now() + 10 * 60 * 1000
    });

    console.log(`[SMS VIRTUAL] Código de verificação para ${cleanNumber}: ${code}`);

    return {
      success: true,
      virtualNumber: cleanNumber,
      code,
      message: 'Código de confirmação SMS enviado com sucesso.'
    };
  }

  public static async verifyVirtualNumberCode(data: {
    virtualNumber: string;
    code: string;
    deviceName?: string;
    ipAddress?: string;
  }) {
    const cleanNumber = data.virtualNumber.replace(/\D/g, '');
    if (cleanNumber.length !== 8) {
      throw new Error('O número virtual deve conter exatamente 8 dígitos numéricos.');
    }

    const entry = this.virtualNumberStore.get(cleanNumber);
    if (!entry || entry.code !== data.code.trim() || Date.now() > entry.expiresAt) {
      throw new Error('Código SMS incorreto ou expirado. Tente novamente.');
    }

    this.virtualNumberStore.delete(cleanNumber);

    let user = await db.findUserByUsername(cleanNumber);
    let recoveryKey = '';

    if (!user) {
      const passwordHash = await bcrypt.hash('vn_' + cleanNumber + '_' + uuidv4(), 10);
      recoveryKey = generateRecoveryKey();
      const recoveryHash = await bcrypt.hash(recoveryKey.trim().toLowerCase(), 10);

      const userId = uuidv4();
      const now = new Date().toISOString();

      user = {
        id: userId,
        username: cleanNumber,
        password_hash: passwordHash,
        recovery_hash: recoveryHash,
        created_at: now,
        updated_at: now
      };
      await db.createUser(user);

      const profile: UserProfile = {
        user_id: userId,
        display_name: `ID ${cleanNumber}`,
        updated_at: now
      };
      await db.upsertProfile(profile);
      await db.getPreferences(userId);
    }

    const profile = await db.getProfile(user.id);

    const sessionId = uuidv4();
    const refreshToken = uuidv4();
    const refreshTokenHash = await bcrypt.hash(refreshToken, 8);
    const now = new Date().toISOString();

    const session: UserSession = {
      id: sessionId,
      user_id: user.id,
      device_name: data.deviceName || 'Android Device',
      ip_address: data.ipAddress || '127.0.0.1',
      refresh_token_hash: refreshTokenHash,
      last_active: now,
      created_at: now
    };
    await db.createSession(session);

    const accessToken = jwt.sign(
      { userId: user.id, username: user.username, sessionId },
      CONFIG.JWT_SECRET,
      { expiresIn: CONFIG.JWT_EXPIRES_IN as any }
    );

    return {
      user: {
        id: user.id,
        username: user.username,
        displayName: profile?.display_name || `ID ${user.username}`,
        avatarUrl: profile?.avatar_url,
        publicKey: profile?.public_key,
        createdAt: user.created_at
      },
      tokens: {
        accessToken,
        refreshToken,
        expiresIn: CONFIG.JWT_EXPIRES_IN
      },
      recoveryKey: recoveryKey || 'Conta verificada com sucesso'
    };
  }
}

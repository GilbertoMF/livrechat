import { Request, Response } from 'express';
import { AuthService } from './auth.service';
import { AuthRequest } from '../../middleware/auth';

export class AuthController {
  public static async register(req: Request, res: Response) {
    try {
      const { username, password, displayName, avatarUrl, publicKey, deviceName } = req.body;
      const ipAddress = req.ip || req.socket.remoteAddress;

      const result = await AuthService.register({
        username,
        password,
        displayName,
        avatarUrl,
        publicKey,
        deviceName,
        ipAddress
      });

      return res.status(201).json(result);
    } catch (error: any) {
      return res.status(400).json({ error: error.message || 'Erro ao criar conta.' });
    }
  }

  public static async login(req: Request, res: Response) {
    try {
      const { username, password, deviceName } = req.body;
      const ipAddress = req.ip || req.socket.remoteAddress;

      const result = await AuthService.login({
        username,
        password,
        deviceName,
        ipAddress
      });

      return res.status(200).json(result);
    } catch (error: any) {
      return res.status(401).json({ error: error.message || 'Falha na autenticação.' });
    }
  }

  public static async recover(req: Request, res: Response) {
    try {
      const { username, recoveryKey, newPassword } = req.body;
      const result = await AuthService.recoverAccount({ username, recoveryKey, newPassword });
      return res.status(200).json(result);
    } catch (error: any) {
      return res.status(400).json({ error: error.message || 'Erro na recuperação de conta.' });
    }
  }

  public static async getSessions(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const sessions = await AuthService.getSessions(userId);
      return res.status(200).json(sessions);
    } catch (error: any) {
      return res.status(500).json({ error: error.message });
    }
  }

  public static async revokeSession(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { sessionId } = req.params;
      const success = await AuthService.revokeSession(userId, sessionId);
      return res.status(200).json({ success });
    } catch (error: any) {
      return res.status(500).json({ error: error.message });
    }
  }

  public static async revokeOtherSessions(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const count = await AuthService.revokeAllOtherSessions(userId, '');
      return res.status(200).json({ revokedCount: count });
    } catch (error: any) {
      return res.status(500).json({ error: error.message });
    }
  }

  public static async deleteAccount(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      await AuthService.deleteAccount(userId);
      return res.status(200).json({ success: true, message: 'Conta excluída com sucesso.' });
    } catch (error: any) {
      return res.status(500).json({ error: error.message });
    }
  }

  public static async requestVirtualNumberCode(req: Request, res: Response) {
    try {
      const { virtualNumber } = req.body;
      const result = await AuthService.requestVirtualNumberCode(virtualNumber);
      return res.status(200).json(result);
    } catch (error: any) {
      return res.status(400).json({ error: error.message || 'Erro ao gerar código SMS.' });
    }
  }

  public static async verifyVirtualNumberCode(req: Request, res: Response) {
    try {
      const { virtualNumber, code, deviceName, displayName } = req.body;
      const ipAddress = req.ip || req.socket.remoteAddress;
      const result = await AuthService.verifyVirtualNumberCode({
        virtualNumber,
        code,
        deviceName,
        ipAddress,
        displayName
      });
      return res.status(200).json(result);
    } catch (error: any) {
      return res.status(400).json({ error: error.message || 'Falha ao confirmar código SMS.' });
    }
  }
}

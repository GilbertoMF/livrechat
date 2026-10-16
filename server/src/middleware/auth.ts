import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { CONFIG } from '../config/env';

export interface AuthRequest extends Request {
  user?: {
    userId: string;
    username: string;
    sessionId?: string;
  };
}

export function authenticateToken(req: AuthRequest, res: Response, next: NextFunction) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ error: 'Token de autenticação não fornecido.' });
  }

  jwt.verify(token, CONFIG.JWT_SECRET, (err, decoded: any) => {
    if (err) {
      return res.status(403).json({ error: 'Token inválido ou expirado.' });
    }
    req.user = {
      userId: decoded.userId,
      username: decoded.username,
      sessionId: decoded.sessionId
    };
    next();
  });
}

// In-memory rate limiting to protect endpoints against brute force
const requestCounts = new Map<string, { count: number; resetTime: number }>();

export function rateLimiter(limit: number, windowMs: number) {
  return (req: Request, res: Response, next: NextFunction) => {
    const ip = req.ip || req.socket.remoteAddress || 'unknown';
    const now = Date.now();
    const entry = requestCounts.get(ip);

    if (!entry || now > entry.resetTime) {
      requestCounts.set(ip, { count: 1, resetTime: now + windowMs });
      return next();
    }

    if (entry.count >= limit) {
      return res.status(429).json({
        error: 'Muitas requisições. Aguarde alguns instantes antes de tentar novamente.'
      });
    }

    entry.count++;
    next();
  };
}

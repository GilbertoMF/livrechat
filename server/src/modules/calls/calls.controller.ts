import { Response } from 'express';
import { AuthRequest } from '../../middleware/auth';
import { db } from '../../db/database';
import { CONFIG } from '../../config/env';
import { v4 as uuidv4 } from 'uuid';

export class CallsController {
  public static async getIceServers(req: AuthRequest, res: Response) {
    try {
      const iceServers = [
        ...CONFIG.STUN_SERVERS.map(url => ({ urls: url })),
        ...CONFIG.TURN_SERVERS
      ];
      return res.status(200).json({ iceServers });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async getCallHistory(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const history = await db.getCallHistory(userId);
      const detailed = [];

      for (const call of history) {
        const otherUserId = call.caller_id === userId ? call.callee_id : call.caller_id;
        const otherUser = await db.findUserById(otherUserId);
        const otherProfile = await db.getProfile(otherUserId);

        detailed.push({
          id: call.id,
          callerId: call.caller_id,
          calleeId: call.callee_id,
          isOutgoing: call.caller_id === userId,
          status: call.status,
          startedAt: call.started_at,
          endedAt: call.ended_at,
          durationSeconds: call.duration_seconds,
          contact: {
            id: otherUserId,
            username: otherUser ? otherUser.username : '',
            displayName: otherProfile ? otherProfile.display_name : (otherUser ? otherUser.username : ''),
            avatarUrl: otherProfile?.avatar_url || null
          }
        });
      }

      return res.status(200).json(detailed);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async recordCall(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      const { calleeId, status, startedAt, endedAt, durationSeconds } = req.body;

      const record = await db.recordCall({
        id: uuidv4(),
        caller_id: userId,
        callee_id: calleeId,
        status: status || 'completed',
        started_at: startedAt || new Date().toISOString(),
        ended_at: endedAt || new Date().toISOString(),
        duration_seconds: durationSeconds || 0
      });

      return res.status(201).json(record);
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }

  public static async clearCallHistory(req: AuthRequest, res: Response) {
    try {
      const userId = req.user!.userId;
      await db.deleteCallHistory(userId);
      return res.status(200).json({ success: true });
    } catch (e: any) {
      return res.status(500).json({ error: e.message });
    }
  }
}

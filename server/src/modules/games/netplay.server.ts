import { Server as HttpServer } from 'http';
import { Server as SocketIOServer, Socket } from 'socket.io';
import { Router, Request, Response } from 'express';

export interface NetplayRoom {
  sessionId: string;
  roomName: string;
  gameId: string;
  domain: string;
  password?: string;
  maxPlayers: number;
  ownerSocketId: string;
  players: Map<string, any>; // playerId -> player metadata
  peers: Array<{ source: string; target: string }>;
  createdAt: number;
}

interface SocketIndex {
  sessionId: string;
  playerId: string;
  lastChatAt: number;
}

class NetplayManager {
  private io: SocketIOServer | null = null;
  private rooms: Map<string, NetplayRoom> = new Map();
  private socketIndex: Map<string, SocketIndex> = new Map();

  public init(server: HttpServer) {
    this.io = new SocketIOServer(server, {
      cors: {
        origin: '*',
        methods: ['GET', 'POST'],
      },
      transports: ['websocket', 'polling'],
      allowEIO3: true,
    });

    this.io.on('connection', (socket: Socket) => {
      this.handleConnection(socket);
    });

    // Clean up empty or stale rooms every 2 minutes
    setInterval(() => {
      const now = Date.now();
      for (const [sessionId, room] of this.rooms.entries()) {
        if (room.players.size === 0 || now - room.createdAt > 24 * 60 * 60 * 1000) {
          this.rooms.delete(sessionId);
        }
      }
    }, 120000);

    console.log('🎮 [Netplay] Servidor de sinalização EmulatorJS WebRTC & Socket.io inicializado!');
  }

  private handleConnection(socket: Socket) {
    socket.join(socket.id);

    // 1. OPEN ROOM
    socket.on('open-room', (data: any, ack?: (err: any) => void) => {
      try {
        const extra = data?.extra || {};
        const sessionId = String(extra.sessionid || '').trim();
        const playerId = String(extra.userid || extra.playerId || '').trim();

        if (!sessionId || !playerId) {
          if (typeof ack === 'function') ack('sessionId e playerId são obrigatórios');
          return;
        }

        const roomName = String(extra.room_name || `Sala ${sessionId}`).trim();
        const gameId = String(extra.game_id || 'default').trim();
        const domain = String(extra.domain || 'livrechat').trim();
        const maxPlayers = Number(data?.maxPlayers) || 4;
        const password = data?.password ? String(data.password).trim() : undefined;

        if (this.rooms.has(sessionId)) {
          if (typeof ack === 'function') ack('Sala já existe com este ID');
          return;
        }

        extra.socketId = socket.id;
        const players = new Map<string, any>();
        players.set(playerId, extra);

        const room: NetplayRoom = {
          sessionId,
          roomName,
          gameId,
          domain,
          password: password && password.toLowerCase() !== 'none' ? password : undefined,
          maxPlayers,
          ownerSocketId: socket.id,
          players,
          peers: [],
          createdAt: Date.now(),
        };

        this.rooms.set(sessionId, room);
        socket.join(sessionId);

        this.socketIndex.set(socket.id, {
          sessionId,
          playerId,
          lastChatAt: 0,
        });

        if (typeof ack === 'function') ack(null);

        // Notificar jogadores da sala (users-updated)
        this.broadcastUsersUpdated(sessionId);
        console.log(`🎮 [Netplay] Sala criada: "${roomName}" (${sessionId}) por ${extra.player_name || playerId} no jogo ${gameId}`);
      } catch (e: any) {
        console.error('Erro no open-room:', e);
        if (typeof ack === 'function') ack(e.message);
      }
    });

    // 2. JOIN ROOM
    socket.on('join-room', (data: any, ack?: (err: any, playersSnapshot?: any) => void) => {
      try {
        const extra = data?.extra || {};
        const sessionId = String(extra.sessionid || data?.sessionid || '').trim();
        const playerId = String(extra.userid || extra.playerId || '').trim();

        if (!sessionId || !playerId) {
          if (typeof ack === 'function') ack('sessionId e playerId são obrigatórios');
          return;
        }

        const room = this.rooms.get(sessionId);
        if (!room) {
          if (typeof ack === 'function') ack('Sala não encontrada');
          return;
        }

        if (room.players.size >= room.maxPlayers) {
          if (typeof ack === 'function') ack('Sala cheia');
          return;
        }

        if (room.password) {
          const providedPw = String(data?.password || '').trim();
          if (providedPw !== room.password) {
            if (typeof ack === 'function') ack('Senha incorreta');
            return;
          }
        }

        extra.socketId = socket.id;
        room.players.set(playerId, extra);
        socket.join(sessionId);

        this.socketIndex.set(socket.id, {
          sessionId,
          playerId,
          lastChatAt: 0,
        });

        const snapshot = Object.fromEntries(room.players.entries());
        if (typeof ack === 'function') ack(null, snapshot);

        this.broadcastUsersUpdated(sessionId);
        console.log(`🎮 [Netplay] ${extra.player_name || playerId} entrou na sala "${room.roomName}" (${sessionId})`);
      } catch (e: any) {
        console.error('Erro no join-room:', e);
        if (typeof ack === 'function') ack(e.message);
      }
    });

    // 3. LEAVE ROOM & DISCONNECT
    socket.on('leave-room', () => {
      this.handleLeave(socket);
    });

    socket.on('disconnect', () => {
      this.handleLeave(socket);
    });

    // 4. WEBRTC SIGNAL RELAY
    socket.on('webrtc-signal', (data: any) => {
      const target = data?.target;
      if (!target) return;

      const payload = {
        sender: socket.id,
        candidate: data.candidate,
        offer: data.offer,
        answer: data.answer,
        requestRenegotiate: data.requestRenegotiate,
      };

      this.io?.to(target).emit('webrtc-signal', payload);
    });

    // 5. DATA MESSAGE (Fallback relay if WebRTC direct channel not used)
    socket.on('data-message', (data: any) => {
      const idx = this.socketIndex.get(socket.id);
      if (!idx) return;
      socket.to(idx.sessionId).emit('data-message', data);
    });

    // 6. SNAPSHOT & INPUT RELAYS
    socket.on('snapshot', (data: any) => {
      const idx = this.socketIndex.get(socket.id);
      if (!idx) return;
      socket.to(idx.sessionId).emit('snapshot', data);
    });

    socket.on('input', (data: any) => {
      const idx = this.socketIndex.get(socket.id);
      if (!idx) return;
      socket.to(idx.sessionId).emit('input', data);
    });

    // 7. CHAT MESSAGE
    socket.on('chat-message', (data: any, ack?: (res: any) => void) => {
      const idx = this.socketIndex.get(socket.id);
      if (!idx) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Not in a room' });
        return;
      }

      const room = this.rooms.get(idx.sessionId);
      if (!room) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Room not found' });
        return;
      }

      const senderPlayer = room.players.get(idx.playerId);
      const playerName = senderPlayer?.player_name || 'Jogador';
      const message = String(data?.message || data || '').slice(0, 300).trim();

      const payload = {
        ts: Date.now(),
        to: 'all',
        userid: idx.playerId,
        player_name: playerName,
        message,
      };

      this.io?.to(idx.sessionId).emit('chat-message', payload);
      if (typeof ack === 'function') ack({ ok: true });
    });
  }

  private handleLeave(socket: Socket) {
    const idx = this.socketIndex.get(socket.id);
    if (!idx) return;

    this.socketIndex.delete(socket.id);
    const room = this.rooms.get(idx.sessionId);
    if (!room) return;

    room.players.delete(idx.playerId);
    room.peers = room.peers.filter(p => p.source !== socket.id && p.target !== socket.id);

    socket.leave(idx.sessionId);

    if (room.players.size === 0) {
      this.rooms.delete(idx.sessionId);
      console.log(`🎮 [Netplay] Sala vazia removida: ${idx.sessionId}`);
    } else {
      // Se quem saiu era o dono, passa a liderança pro próximo jogador
      if (room.ownerSocketId === socket.id) {
        const firstPlayer = room.players.values().next().value;
        if (firstPlayer?.socketId) {
          room.ownerSocketId = firstPlayer.socketId;
          console.log(`🎮 [Netplay] Novo dono da sala ${idx.sessionId}: ${firstPlayer.player_name}`);
        }
      }
      this.broadcastUsersUpdated(idx.sessionId);
    }
  }

  private broadcastUsersUpdated(sessionId: string) {
    const room = this.rooms.get(sessionId);
    if (!room || !this.io) return;

    const payload = Object.fromEntries(room.players.entries());
    this.io.to(sessionId).emit('users-updated', payload);
  }

  // HTTP list endpoint query
  public listRooms(domain?: string, gameId?: string): Record<string, any> {
    const out: Record<string, any> = {};
    for (const [sessionId, room] of this.rooms.entries()) {
      if (domain && room.domain !== domain) continue;
      if (gameId && room.gameId !== gameId) continue;

      let ownerName = 'Desconhecido';
      for (const p of room.players.values()) {
        if (p.socketId === room.ownerSocketId) {
          ownerName = p.player_name || 'Jogador 1';
          break;
        }
      }

      out[sessionId] = {
        room_name: room.roomName,
        current: room.players.size,
        max: room.maxPlayers,
        player_name: ownerName,
        hasPassword: Boolean(room.password),
        gameId: room.gameId,
      };
    }
    return out;
  }
}

export const netplayManager = new NetplayManager();

export const netplayRouter = Router();

// Endpoint que o EmulatorJS consome: GET /list?domain=...&game_id=...
netplayRouter.get(['/', '/list'], (req: Request, res: Response) => {
  const domain = req.query.domain as string | undefined;
  const gameId = (req.query.game_id || req.query.gameId) as string | undefined;
  const list = netplayManager.listRooms(domain, gameId);
  res.json(list);
});


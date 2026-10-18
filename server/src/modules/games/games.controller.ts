import { Router, Request, Response } from 'express';

export interface GameItem {
  id: string;
  title: string;
  subtitle?: string;
  category: 'Luta' | 'Ação' | 'Corrida' | 'Futebol' | 'Arcade';
  coverUrl: string;
  downloadUrl: string;
  size: string;
  sizeBytes: number;
  fileName: string;
  description: string;
  maxPlayers: number;
  gameCode?: string;
}

export const gamesRouter = Router();

// PPSSPP Multiplayer Server Configuration Info
const SERVER_INFO = {
  proAdhocServer: '164.152.40.160',
  portOffset: 10000,
  adhocPort: 27312,
  status: 'online',
  serverName: 'LivreGames Brasil (Oracle Cloud VPS)',
  ppssppPlayStoreUrl: 'https://play.google.com/store/apps/details?id=org.ppsspp.ppsspp',
  ppssppApkUrl: 'https://www.ppsspp.org/files/1_18_1/ppsspp.apk',
};

// Curated list of popular PSP games with high-speed CSO/ISO downloads and active AdHoc multiplayer
const GAMES_CATALOG: GameItem[] = [
  {
    id: 'dbz_sb',
    title: 'Dragon Ball Z: Shin Budokai',
    subtitle: 'Another Road',
    category: 'Luta',
    coverUrl: 'https://images.igdb.com/igdb/image/upload/t_cover_big/co2047.webp',
    downloadUrl: 'https://archive.org/download/psp-cso-collection/Dragon%20Ball%20Z%20-%20Shin%20Budokai%20%28USA%29.cso',
    size: '315 MB',
    sizeBytes: 330301440,
    fileName: 'dbz_shin_budokai.cso',
    description: 'Batalhas intensas no ar e no chão com Goku, Vegeta, Trunks e Gohan. Modo Versus Ad-Hoc 1v1 ultrarrápido sem lag!',
    maxPlayers: 2,
    gameCode: 'ULUS10077',
  },
  {
    id: 'gta_vcs',
    title: 'GTA: Vice City Stories',
    subtitle: 'Empire & Gang Wars',
    category: 'Ação',
    coverUrl: 'https://images.igdb.com/igdb/image/upload/t_cover_big/co1x4k.webp',
    downloadUrl: 'https://archive.org/download/psp-cso-collection/Grand%20Theft%20Auto%20-%20Vice%20City%20Stories%20%28USA%29.cso',
    size: '710 MB',
    sizeBytes: 744488960,
    fileName: 'gta_vice_city_stories.cso',
    description: 'Explore Vice City e enfrente seus amigos nos modos multiplayer Ad-Hoc: Empire Takedown, Street Race e Deathmatch até 6 jogadores.',
    maxPlayers: 6,
    gameCode: 'ULUS10160',
  },
  {
    id: 'naruto_unh3',
    title: 'Naruto Shippuden: Ultimate Ninja Heroes 3',
    subtitle: 'Ninjas do Mundo Shinobi',
    category: 'Luta',
    coverUrl: 'https://images.igdb.com/igdb/image/upload/t_cover_big/co2k9u.webp',
    downloadUrl: 'https://archive.org/download/psp-cso-collection/Naruto%20Shippuden%20-%20Ultimate%20Ninja%20Heroes%203%20%28USA%29.cso',
    size: '480 MB',
    sizeBytes: 503316480,
    fileName: 'naruto_shippuden_unh3.cso',
    description: 'Combates ninja dinâmicos com jutsus supremos. Suporta até 4 ninjas em batalha simultânea na mesma arena via servidor LivreGames.',
    maxPlayers: 4,
    gameCode: 'ULUS10518',
  },
  {
    id: 'tekken_dr',
    title: 'Tekken: Dark Resurrection',
    subtitle: 'King of Iron Fist Tournament',
    category: 'Luta',
    coverUrl: 'https://images.igdb.com/igdb/image/upload/t_cover_big/co1x68.webp',
    downloadUrl: 'https://archive.org/download/psp-cso-collection/Tekken%20-%20Dark%20Resurrection%20%28USA%29.cso',
    size: '560 MB',
    sizeBytes: 587202560,
    fileName: 'tekken_dark_resurrection.cso',
    description: 'Um dos melhores jogos de luta 3D de todos os tempos a 60 FPS com Jin, Kazuya, Paul e Lili no modo Ad-Hoc Versus 1v1.',
    maxPlayers: 2,
    gameCode: 'ULUS10170',
  },
  {
    id: 'nfs_mw',
    title: 'Need for Speed: Most Wanted 5-1-0',
    subtitle: 'Blacklist Racers',
    category: 'Corrida',
    coverUrl: 'https://images.igdb.com/igdb/image/upload/t_cover_big/co1x9v.webp',
    downloadUrl: 'https://archive.org/download/psp-cso-collection/Need%20for%20Speed%20-%20Most%20Wanted%205-1-0%20%28USA%29.cso',
    size: '168 MB',
    sizeBytes: 176160768,
    fileName: 'need_for_speed_most_wanted.cso',
    description: 'Velocidade, nitro e perseguições policiais. Dispute corridas ilegais contra seus amigos no modo wireless ad-hoc.',
    maxPlayers: 4,
    gameCode: 'ULUS10036',
  },
  {
    id: 'bomberman_psp',
    title: 'Bomberman (PSP)',
    subtitle: 'Battle Arena Clássica',
    category: 'Arcade',
    coverUrl: 'https://images.igdb.com/igdb/image/upload/t_cover_big/co2kbb.webp',
    downloadUrl: 'https://archive.org/download/psp-cso-collection/Bomberman%20%28USA%29.cso',
    size: '78 MB',
    sizeBytes: 81788928,
    fileName: 'bomberman.cso',
    description: 'Clássico divertido e viciante! Plante bombas e encurrale até 4 amigos em arenas multiplayer cheias de power-ups.',
    maxPlayers: 4,
    gameCode: 'ULUS10125',
  },
  {
    id: 'pes_2014',
    title: 'Pro Evolution Soccer 2014',
    subtitle: 'Futebol Clássico no Bolso',
    category: 'Futebol',
    coverUrl: 'https://images.igdb.com/igdb/image/upload/t_cover_big/co2kb0.webp',
    downloadUrl: 'https://archive.org/download/psp-cso-collection/Pro%20Evolution%20Soccer%202014%20%28USA%29.cso',
    size: '515 MB',
    sizeBytes: 540016640,
    fileName: 'pes_2014.cso',
    description: 'Jogue partidas de futebol 1v1 contra outros contatos do LivreChat usando o servidor de rede Ad-Hoc oficial.',
    maxPlayers: 2,
    gameCode: 'ULUS10653',
  },
];

// GET /api/games - List all games
gamesRouter.get('/', (req: Request, res: Response) => {
  const { category, search } = req.query;
  let results = GAMES_CATALOG;

  if (category && typeof category === 'string' && category !== 'Todos') {
    results = results.filter(g => g.category.toLowerCase() === category.toLowerCase());
  }

  if (search && typeof search === 'string') {
    const q = search.toLowerCase();
    results = results.filter(g => g.title.toLowerCase().includes(q) || g.description.toLowerCase().includes(q));
  }

  res.json({
    success: true,
    count: results.length,
    server: SERVER_INFO,
    games: results,
  });
});

// GET /api/games/server-info - Get Adhoc Server information
gamesRouter.get('/server-info', (req: Request, res: Response) => {
  res.json(SERVER_INFO);
});

// GET /api/games/:id - Get game details
gamesRouter.get('/:id', (req: Request, res: Response) => {
  const game = GAMES_CATALOG.find(g => g.id === req.params.id);
  if (!game) {
    return res.status(404).json({ error: 'Jogo não encontrado' });
  }
  res.json({
    success: true,
    server: SERVER_INFO,
    game,
  });
});

import { Router, Request, Response } from 'express';

export interface GameItem {
  id: string;
  title: string;
  subtitle?: string;
  category: 'Luta' | 'Ação' | 'Corrida' | 'Futebol' | 'Arcade';
  coverUrl: string;
  downloadUrl: string;
  fallbackUrl?: string;
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

const CDN_BASE = 'https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/games';

// Curated list of popular PSP games with high-speed Cloudflare R2 downloads and active AdHoc multiplayer
const GAMES_CATALOG: GameItem[] = [
  {
    id: 'dbz_sb',
    title: 'Dragon Ball Z: Shin Budokai',
    subtitle: 'Another Road',
    category: 'Luta',
    coverUrl: 'https://upload.wikimedia.org/wikipedia/en/1/12/Dragon_Ball_Z_Shin_Budokai_cover.jpg',
    downloadUrl: `${CDN_BASE}/dbz_shin_budokai.cso`,
    fallbackUrl: 'http://164.152.40.160:3000/games/dbz_shin_budokai.cso',
    size: '209 MB',
    sizeBytes: 219194706,
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
    coverUrl: 'https://upload.wikimedia.org/wikipedia/en/3/3e/GTA_Vice_City_Stories_PSP_boxart.jpg',
    downloadUrl: `${CDN_BASE}/gta_vice_city_stories.cso`,
    fallbackUrl: 'http://164.152.40.160:3000/games/gta_vice_city_stories.cso',
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
    coverUrl: 'https://images.launchbox-app.com/3470cf1d-89e7-4f64-a499-38db60e3dc96.jpg',
    downloadUrl: `${CDN_BASE}/naruto_shippuden_unh3.cso`,
    fallbackUrl: 'http://164.152.40.160:3000/games/naruto_shippuden_unh3.cso',
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
    coverUrl: 'https://upload.wikimedia.org/wikipedia/en/d/d0/PSP-TekkenDarkRessurectionUSversion-FrontCover.jpg',
    downloadUrl: `${CDN_BASE}/tekken_dark_resurrection.cso`,
    fallbackUrl: 'http://164.152.40.160:3000/games/tekken_dark_resurrection.cso',
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
    coverUrl: 'https://upload.wikimedia.org/wikipedia/en/8/8e/Need_for_Speed_Most_Wanted_Box_Art.jpg',
    downloadUrl: `${CDN_BASE}/need_for_speed_most_wanted.cso`,
    fallbackUrl: 'http://164.152.40.160:3000/games/need_for_speed_most_wanted.cso',
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
    coverUrl: 'https://upload.wikimedia.org/wikipedia/en/9/99/Bomberman_PSP.jpg',
    downloadUrl: `${CDN_BASE}/bomberman.cso`,
    fallbackUrl: 'http://164.152.40.160:3000/games/bomberman.cso',
    size: '75 MB',
    sizeBytes: 78643200,
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
    coverUrl: 'https://upload.wikimedia.org/wikipedia/en/2/2b/Pes2014eucover.png',
    downloadUrl: `${CDN_BASE}/pes_2014.cso`,
    fallbackUrl: 'http://164.152.40.160:3000/games/pes_2014.cso',
    size: '515 MB',
    sizeBytes: 540016640,
    fileName: 'pes_2014.cso',
    description: 'Jogue partidas de futebol 1v1 contra outros contatos do LivreChat usando o servidor de rede Ad-Hoc oficial.',
    maxPlayers: 2,
    gameCode: 'ULUS10653',
  },
];

export interface InstantGameItem {
  id: string;
  title: string;
  subtitle: string;
  category: 'Luta' | 'Ação' | 'Corrida' | 'Futebol' | 'Arcade';
  coverUrl: string;
  romUrl: string;
  core: string;
  size: string;
  maxPlayers: number;
  gameId: number;
  description: string;
  instantRunnerUrl: string;
}

const INSTANT_CDN = 'https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/instant-games';

export const INSTANT_GAMES: InstantGameItem[] = [
  {
    id: 'super_bomberman_2',
    title: 'Super Bomberman 2',
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Arcade',
    coverUrl: 'https://upload.wikimedia.org/wikipedia/en/2/29/Super_Bomberman_2_Coverart.png',
    romUrl: `${INSTANT_CDN}/super_bomberman_2.smc`,
    core: 'snes',
    size: '1.0 MB',
    maxPlayers: 2,
    gameId: 1001,
    description: 'Batalha clássica de bombas com power-ups, arenas destrutíveis e multiplayer em tempo real com controle virtual na tela.',
    instantRunnerUrl: '/instant.html?game=super_bomberman_2',
  },
  {
    id: 'super_mario_kart',
    title: 'Super Mario Kart',
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Corrida',
    coverUrl: 'https://upload.wikimedia.org/wikipedia/en/3/38/Super_Mario_Kart_Cover.JPG',
    romUrl: `${INSTANT_CDN}/super_mario_kart.smc`,
    core: 'snes',
    size: '0.5 MB',
    maxPlayers: 2,
    gameId: 1002,
    description: 'O lendário jogo de kart da Nintendo! Disputas em tela dividida, cascos de tartaruga, bananas e Battle Mode.',
    instantRunnerUrl: '/instant.html?game=super_mario_kart',
  },
  {
    id: 'mortal_kombat_3',
    title: 'Mortal Kombat 3',
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Luta',
    coverUrl: 'https://upload.wikimedia.org/wikipedia/en/7/77/Mortal_Kombat_3_Cover.JPG',
    romUrl: `${INSTANT_CDN}/mortal_kombat_3.smc`,
    core: 'snes',
    size: '4.0 MB',
    maxPlayers: 2,
    gameId: 1003,
    description: 'Sub-Zero, Cyrax, Sektor, Jax, Sonya e Shang Tsung em combates brutais com combos e fatalities instantâneos.',
    instantRunnerUrl: '/instant.html?game=mortal_kombat_3',
  },
  {
    id: 'super_street_fighter_2',
    title: 'Super Street Fighter II',
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Luta',
    coverUrl: 'https://upload.wikimedia.org/wikipedia/en/1/1e/Super_Street_Fighter_II_The_New_Challengers_cover.jpg',
    romUrl: `${INSTANT_CDN}/super_street_fighter_2.smc`,
    core: 'snes',
    size: '4.0 MB',
    maxPlayers: 2,
    gameId: 1004,
    description: 'Ryu, Ken, Guile, Cammy e Chun-Li! Hadoukens e Shoryukens com resposta imediata e combate 1v1.',
    instantRunnerUrl: '/instant.html?game=super_street_fighter_2',
  },
  {
    id: 'top_gear_2',
    title: 'Top Gear 2',
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Corrida',
    coverUrl: 'https://upload.wikimedia.org/wikipedia/en/0/07/Top_Gear_2_cover.jpg',
    romUrl: `${INSTANT_CDN}/top_gear_2.smc`,
    core: 'snes',
    size: '1.0 MB',
    maxPlayers: 2,
    gameId: 1005,
    description: 'A trilha sonora mais nostálgica de todos os tempos! Corra em pistas pelo mundo com nitro e upgrades.',
    instantRunnerUrl: '/instant.html?game=top_gear_2',
  },
];

// GET /api/games - List all games
gamesRouter.get('/', (req: Request, res: Response) => {
  const { category, search } = req.query;
  let results = GAMES_CATALOG;
  let instantResults = INSTANT_GAMES;

  if (category && typeof category === 'string' && category !== 'Todos') {
    results = results.filter(g => g.category.toLowerCase() === category.toLowerCase());
    instantResults = instantResults.filter(g => g.category.toLowerCase() === category.toLowerCase());
  }

  if (search && typeof search === 'string') {
    const q = search.toLowerCase();
    results = results.filter(g => g.title.toLowerCase().includes(q) || g.description.toLowerCase().includes(q));
    instantResults = instantResults.filter(g => g.title.toLowerCase().includes(q) || g.description.toLowerCase().includes(q));
  }

  res.json({
    success: true,
    count: results.length,
    instantCount: instantResults.length,
    server: SERVER_INFO,
    games: results,
    instantGames: instantResults,
  });
});

// GET /api/games/instant - List only instant cloud games
gamesRouter.get('/instant', (req: Request, res: Response) => {
  res.json({
    success: true,
    count: INSTANT_GAMES.length,
    games: INSTANT_GAMES,
  });
});

// GET /api/games/server-info - Get Adhoc Server information
gamesRouter.get('/server-info', (req: Request, res: Response) => {
  res.json(SERVER_INFO);
});

// GET /api/games/:id - Get game details
gamesRouter.get('/:id', (req: Request, res: Response) => {
  const instantGame = INSTANT_GAMES.find(g => g.id === req.params.id);
  if (instantGame) {
    return res.json({
      success: true,
      server: SERVER_INFO,
      isInstant: true,
      game: instantGame,
    });
  }

  const game = GAMES_CATALOG.find(g => g.id === req.params.id);
  if (!game) {
    return res.status(404).json({ error: 'Jogo não encontrado' });
  }
  res.json({
    success: true,
    server: SERVER_INFO,
    isInstant: false,
    game,
  });
});


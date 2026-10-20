import { Router, Request, Response } from 'express';

export interface GameItem {
  id: string;
  title: string;
  subtitle?: string;
  category: 'Luta' | 'Ação' | 'Corrida' | 'Futebol' | 'Arcade';
  console: 'PlayStation Portable (PSP)' | 'Super Nintendo (SNES)' | string;
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
const COVERS_CDN = 'https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/game-covers';

// Curated list of popular PSP games with high-speed Cloudflare R2 downloads and active AdHoc multiplayer
const GAMES_CATALOG: GameItem[] = [
  {
    id: 'dbz_sb',
    title: 'Dragon Ball Z: Shin Budokai',
    subtitle: 'Another Road',
    category: 'Luta',
    console: 'PlayStation Portable (PSP)',
    coverUrl: `${COVERS_CDN}/dbz_sb.jpg`,
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
    console: 'PlayStation Portable (PSP)',
    coverUrl: `${COVERS_CDN}/gta_vcs.jpg`,
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
    console: 'PlayStation Portable (PSP)',
    coverUrl: `${COVERS_CDN}/naruto_unh3.jpg`,
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
    console: 'PlayStation Portable (PSP)',
    coverUrl: `${COVERS_CDN}/tekken_dr.jpg`,
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
    console: 'PlayStation Portable (PSP)',
    coverUrl: `${COVERS_CDN}/nfs_mw.jpg`,
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
    console: 'PlayStation Portable (PSP)',
    coverUrl: `${COVERS_CDN}/bomberman_psp.jpg`,
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
    console: 'PlayStation Portable (PSP)',
    coverUrl: `${COVERS_CDN}/pes_2014.png`,
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
  console: 'Super Nintendo (SNES)' | 'PlayStation Portable (PSP)' | string;
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
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/super_bomberman_2.jpg`,
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
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/super_mario_kart.jpg`,
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
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/mortal_kombat_3.jpg`,
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
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/super_street_fighter_2.png`,
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
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/top_gear_2.jpg`,
    romUrl: `${INSTANT_CDN}/top_gear_2.smc`,
    core: 'snes',
    size: '1.0 MB',
    maxPlayers: 2,
    gameId: 1005,
    description: 'A trilha sonora mais nostálgica de todos os tempos! Corra em pistas pelo mundo com nitro e upgrades.',
    instantRunnerUrl: '/instant.html?game=top_gear_2',
  },
  {
    id: 'super_mario_world',
    title: 'Super Mario World',
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Ação',
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/super_mario_world.png`,
    romUrl: `${INSTANT_CDN}/super_mario_world.smc`,
    core: 'snes',
    size: '0.5 MB',
    maxPlayers: 2,
    gameId: 1006,
    description: 'O clássico supremo do Super Nintendo! Explore Dinosaur Land com Mario, Luigi e Yoshi através de 96 saídas e mundos secretos.',
    instantRunnerUrl: '/instant.html?game=super_mario_world',
  },
  {
    id: 'donkey_kong_country',
    title: 'Donkey Kong Country',
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Ação',
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/donkey_kong_country.png`,
    romUrl: `${INSTANT_CDN}/donkey_kong_country.smc`,
    core: 'snes',
    size: '4.0 MB',
    maxPlayers: 2,
    gameId: 1007,
    description: 'Donkey Kong e Diddy Kong numa jornada lendária com gráficos pré-renderizados revolucionários e trilha sonora inesquecível de David Wise.',
    instantRunnerUrl: '/instant.html?game=donkey_kong_country',
  },
  {
    id: 'mega_man_x',
    title: 'Mega Man X',
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Ação',
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/mega_man_x.png`,
    romUrl: `${INSTANT_CDN}/mega_man_x.smc`,
    core: 'snes',
    size: '1.5 MB',
    maxPlayers: 1,
    gameId: 1008,
    description: 'Escale paredes, dê dashs supersônicos e conquiste os poderes dos 8 Mavericks liderados pelo temível Sigma.',
    instantRunnerUrl: '/instant.html?game=mega_man_x',
  },
  {
    id: 'zelda_alttp',
    title: 'Zelda: A Link to the Past',
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Ação',
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/zelda_alttp.jpg`,
    romUrl: `${INSTANT_CDN}/zelda_alttp.smc`,
    core: 'snes',
    size: '1.0 MB',
    maxPlayers: 1,
    gameId: 1009,
    description: 'Viaje entre o Light World e o Dark World, empunhe a Master Sword e salve a Princesa Zelda das garras de Agahnim e Ganon.',
    instantRunnerUrl: '/instant.html?game=zelda_alttp',
  },
  {
    id: 'super_metroid',
    title: 'Super Metroid',
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Ação',
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/super_metroid.jpg`,
    romUrl: `${INSTANT_CDN}/super_metroid.smc`,
    core: 'snes',
    size: '3.0 MB',
    maxPlayers: 1,
    gameId: 1010,
    description: 'A caçadora de recompensas Samus Aran no misterioso planeta Zebes para resgatar o último bebê Metroid das mãos dos Space Pirates.',
    instantRunnerUrl: '/instant.html?game=super_metroid',
  },
  {
    id: 'top_gear',
    title: 'Top Gear',
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Corrida',
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/top_gear.jpg`,
    romUrl: `${INSTANT_CDN}/top_gear.smc`,
    core: 'snes',
    size: '0.5 MB',
    maxPlayers: 2,
    gameId: 1011,
    description: 'O jogo de corrida mais amado do Brasil! Trilha sonora antológica, marchas manuais, nitros e corridas disputadas em tela dividida.',
    instantRunnerUrl: '/instant.html?game=top_gear',
  },
  {
    id: 'chrono_trigger',
    title: 'Chrono Trigger',
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Ação',
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/chrono_trigger.jpg`,
    romUrl: `${INSTANT_CDN}/chrono_trigger.smc`,
    core: 'snes',
    size: '4.0 MB',
    maxPlayers: 1,
    gameId: 1012,
    description: 'Crono, Marle, Lucca, Frog e Robo viajam pelas eras da história para salvar o futuro da aniquilação causada por Lavos.',
    instantRunnerUrl: '/instant.html?game=chrono_trigger',
  },
  {
    id: 'aladdin',
    title: "Disney's Aladdin",
    subtitle: 'Nuvem Instantânea (Sem Download)',
    category: 'Arcade',
    console: 'Super Nintendo (SNES)',
    coverUrl: `${COVERS_CDN}/aladdin.jpg`,
    romUrl: `${INSTANT_CDN}/aladdin.smc`,
    core: 'snes',
    size: '1.5 MB',
    maxPlayers: 1,
    gameId: 1013,
    description: 'Controle Aladdin e Abu pelas ruas de Agrabah, Caverna das Maravilhas e palácio do Sultão neste clássico da Capcom para SNES.',
    instantRunnerUrl: '/instant.html?game=aladdin',
  },
];

// GET /api/games - List all games
gamesRouter.get('/', (req: Request, res: Response) => {
  const { category, console: consoleFilter, search } = req.query;
  let results = GAMES_CATALOG;
  let instantResults = INSTANT_GAMES;

  if (consoleFilter && typeof consoleFilter === 'string' && consoleFilter !== 'Todos') {
    results = results.filter(g => g.console?.toLowerCase().includes(consoleFilter.toLowerCase()));
    instantResults = instantResults.filter(g => g.console?.toLowerCase().includes(consoleFilter.toLowerCase()));
  }

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

  const pspGame = GAMES_CATALOG.find(g => g.id === req.params.id);
  if (pspGame) {
    return res.json({
      success: true,
      server: SERVER_INFO,
      isInstant: false,
      game: pspGame,
    });
  }

  res.status(404).json({
    success: false,
    message: 'Jogo não encontrado.',
  });
});

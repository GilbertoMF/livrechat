import { Request, Response } from 'express';
// @ts-ignore
import ytSearch from 'yt-search';

interface CacheItem {
  timestamp: number;
  data: any;
}

const cache = new Map<string, CacheItem>();
const CACHE_TTL_MS = 3 * 60 * 1000; // 3 minutes for fresh trending

function getFromCache(key: string): any | null {
  const item = cache.get(key);
  if (!item) return null;
  if (Date.now() - item.timestamp > CACHE_TTL_MS) {
    cache.delete(key);
    return null;
  }
  return item.data;
}

function setInCache(key: string, data: any) {
  cache.set(key, { timestamp: Date.now(), data });
}

// Healthy Invidious instances to query for direct streams
const INVIDIOUS_INSTANCES = [
  'https://invidious.f5.si',
  'https://invidious.nerdvpn.de',
  'https://inv.nadeko.net',
  'https://yewtu.be'
];

export class LivreTubeController {
  // GET /api/livretube/trending?type=all|music|podcasts|gaming|news
  static async getTrending(req: Request, res: Response): Promise<void> {
    try {
      const type = (req.query.type as string) || 'all';
      const cacheKey = `trending_${type}`;
      const cached = getFromCache(cacheKey);
      if (cached) {
        res.status(200).json(cached);
        return;
      }

      let queries: string[] = [];
      if (type === 'music') {
        queries = ['lançamento música brasil clipe oficial', 'músicas virais brasil hoje', 'novos clipes brasil'];
      } else if (type === 'podcasts') {
        queries = ['cortes podcasts brasil da semana', 'podcasts brasil novidades', 'melhores podcasts brasil'];
      } else if (type === 'gaming') {
        queries = ['jogos gameplay brasil novidades', 'gameplay brasil', 'novidades games brasil'];
      } else if (type === 'news') {
        queries = ['notícias brasil hoje', 'últimas notícias brasil', 'jornal brasil hoje'];
      } else {
        // 'all' / 'Em Alta do Momento'
        queries = [
          'notícias brasil hoje',
          'assuntos do momento brasil',
          'cortes podcasts brasil da semana',
          'futebol melhores momentos brasil',
          'lançamento música brasil'
        ];
      }

      const searchResults = await Promise.all(
        queries.map(q => ytSearch(q).catch(() => ({ videos: [] })))
      );

      const allVideos: any[] = [];
      const seen = new Set<string>();

      // Interleave results so the feed has rich variety (news, entertainment, podcasts, music, sports)
      const maxLen = Math.max(...searchResults.map(r => (r.videos || []).length), 0);
      for (let i = 0; i < maxLen; i++) {
        for (const r of searchResults) {
          const v = (r.videos || [])[i];
          if (v && v.videoId && !seen.has(v.videoId)) {
            const lowerTitle = (v.title || '').toLowerCase();
            const ago = (v.ago || '').toLowerCase();
            // Filter out old static playlists that plague generic searches
            const isOld = ago.includes('mo ago') || ago.includes('y ago') || ago.includes('mês') || ago.includes('ano');
            if (isOld && (lowerTitle.includes('top 50') || lowerTitle.includes('hits tiktok') || lowerTitle.includes('spotify brasil'))) {
              continue;
            }
            seen.add(v.videoId);
            allVideos.push({
              videoId: v.videoId,
              title: v.title,
              author: v.author?.name || 'Canal',
              authorUrl: v.author?.url || '',
              authorThumbnail: (v.author as any)?.thumbnail || null,
              thumbnailUrl: v.thumbnail || `https://i.ytimg.com/vi/${v.videoId}/hqdefault.jpg`,
              lengthSeconds: v.seconds || 0,
              durationText: v.timestamp || '0:00',
              viewCount: v.views || 0,
              viewsText: v.views ? formatViews(v.views) : '',
              publishedText: v.ago || ''
            });
          }
        }
      }

      const finalVideos = allVideos.slice(0, 50);
      setInCache(cacheKey, finalVideos);
      res.status(200).json(finalVideos);
    } catch (e: any) {
      console.error('[LivreTube] Erro ao buscar trending:', e.message);
      res.status(500).json({ error: 'Falha ao buscar vídeos em alta', message: e.message });
    }
  }

  // GET /api/livretube/search?q=query
  static async search(req: Request, res: Response): Promise<void> {
    try {
      const query = (req.query.q as string || '').trim();
      if (!query) {
        res.status(400).json({ error: 'Parâmetro q é obrigatório' });
        return;
      }

      const cacheKey = `search_${query.toLowerCase()}`;
      const cached = getFromCache(cacheKey);
      if (cached) {
        res.status(200).json(cached);
        return;
      }

      const results = await ytSearch(query);
      const videos = (results.videos || []).slice(0, 30).map((v: any) => ({
        videoId: v.videoId,
        title: v.title,
        author: v.author?.name || 'Canal',
        authorUrl: v.author?.url || '',
        authorThumbnail: v.author?.thumbnail || null,
        thumbnailUrl: v.thumbnail || `https://i.ytimg.com/vi/${v.videoId}/hqdefault.jpg`,
        lengthSeconds: v.seconds || 0,
        durationText: v.timestamp || '0:00',
        viewCount: v.views || 0,
        viewsText: v.views ? formatViews(v.views) : '',
        publishedText: v.ago || ''
      }));

      setInCache(cacheKey, videos);
      res.status(200).json(videos);
    } catch (e: any) {
      console.error('[LivreTube] Erro na pesquisa:', e.message);
      res.status(500).json({ error: 'Falha ao realizar pesquisa', message: e.message });
    }
  }

  // GET /api/livretube/video/:id
  static async getVideoDetails(req: Request, res: Response): Promise<void> {
    const videoId = req.params.id;
    if (!videoId) {
      res.status(400).json({ error: 'ID do vídeo obrigatório' });
      return;
    }

    const cacheKey = `video_${videoId}`;
    const cached = getFromCache(cacheKey);
    if (cached) {
      res.status(200).json(cached);
      return;
    }

    // Try Invidious instances for direct stream URLs
    let invidiousData: any = null;
    for (const instance of INVIDIOUS_INSTANCES) {
      try {
        const response = await fetch(`${instance}/api/v1/videos/${videoId}`, {
          signal: AbortSignal.timeout(3500)
        });
        if (response.ok) {
          invidiousData = await response.json();
          break;
        }
      } catch (err) {
        // Try next instance
      }
    }

    const embedUrl = `https://invidious.f5.si/embed/${videoId}?autoplay=1`;
    const fallbackEmbedUrl = `https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&modestbranding=1&rel=0`;

    if (invidiousData) {
      const streams = (invidiousData.formatStreams || []).map((s: any) => ({
        url: s.url,
        quality: s.quality || s.qualityLabel || '360p',
        container: s.container || 'mp4',
        resolution: s.resolution || s.qualityLabel || '360p'
      }));

      const details = {
        videoId,
        title: invidiousData.title || '',
        description: invidiousData.description || '',
        author: invidiousData.author || '',
        authorThumbnail: invidiousData.authorThumbnails?.[0]?.url || null,
        subCountText: invidiousData.subCountText || null,
        lengthSeconds: invidiousData.lengthSeconds || 0,
        viewCount: invidiousData.viewCount || 0,
        streams,
        hlsUrl: invidiousData.hlsUrl || null,
        embedUrl,
        fallbackEmbedUrl
      };

      setInCache(cacheKey, details);
      res.status(200).json(details);
      return;
    }

    // Fallback: Resolve metadata via ytSearch video
    try {
      const ytVideo: any = await ytSearch({ videoId });
      const details = {
        videoId,
        title: ytVideo.title || '',
        description: ytVideo.description || '',
        author: ytVideo.author?.name || '',
        authorThumbnail: null,
        subCountText: null,
        lengthSeconds: ytVideo.seconds || 0,
        viewCount: ytVideo.views || 0,
        streams: [],
        hlsUrl: null,
        embedUrl,
        fallbackEmbedUrl
      };

      setInCache(cacheKey, details);
      res.status(200).json(details);
    } catch (e: any) {
      // Minimal response with working embeds
      const minimal = {
        videoId,
        title: 'Vídeo do LivreTube',
        description: '',
        author: '',
        authorThumbnail: null,
        subCountText: null,
        lengthSeconds: 0,
        viewCount: 0,
        streams: [],
        hlsUrl: null,
        embedUrl,
        fallbackEmbedUrl
      };
      res.status(200).json(minimal);
    }
  }
}

function formatViews(views: number): string {
  if (views >= 1000000) {
    return `${(views / 1000000).toFixed(1)}M visualizações`;
  } else if (views >= 1000) {
    return `${(views / 1000).toFixed(1)}K visualizações`;
  }
  return `${views} visualizações`;
}

import { Request, Response } from 'express';
// @ts-ignore
import ytSearch from 'yt-search';

interface CacheItem {
  timestamp: number;
  data: any;
}

const cache = new Map<string, CacheItem>();
const CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes

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

      let searchQuery = 'em alta brasil';
      if (type === 'music') searchQuery = 'músicas em alta brasil';
      else if (type === 'podcasts') searchQuery = 'melhores podcasts brasil';
      else if (type === 'gaming') searchQuery = 'jogos gameplay brasil';
      else if (type === 'news') searchQuery = 'notícias brasil hoje';

      const results = await ytSearch(searchQuery);
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

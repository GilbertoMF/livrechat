import { Request, Response } from 'express';
import multer from 'multer';
import { v4 as uuidv4 } from 'uuid';
import path from 'path';
import { R2Service } from '../../services/r2.service';
import { AuthRequest } from '../../middleware/auth';

const storage = multer.memoryStorage();
export const uploadMiddleware = multer({
  storage,
  limits: {
    fileSize: 50 * 1024 * 1024, // 50MB max
  },
});

export class MediaController {
  public static async uploadMedia(req: AuthRequest, res: Response) {
    try {
      if (!req.file) {
        return res.status(400).json({ error: 'Nenhum arquivo enviado.' });
      }

      const file = req.file;
      const folder = (req.body.folder || 'media').replace(/[^a-zA-Z0-9_-]/g, '');
      const ext = path.extname(file.originalname).toLowerCase() || '.bin';
      const safeBase = path.basename(file.originalname, ext).replace(/[^a-zA-Z0-9_-]/g, '_').slice(0, 30);
      const uniqueId = uuidv4().slice(0, 12);
      const key = `${folder}/${uniqueId}_${safeBase}${ext}`;

      const { url } = await R2Service.uploadBuffer(file.buffer, key, file.mimetype);

      return res.status(200).json({
        url,
        key,
        size: file.size,
        mimeType: file.mimetype,
        fileName: file.originalname,
      });
    } catch (e: any) {
      console.error('Erro no upload R2:', e);
      return res.status(500).json({ error: 'Falha ao salvar arquivo no Cloudflare R2: ' + e.message });
    }
  }

  public static async getPresignedUrl(req: AuthRequest, res: Response) {
    try {
      const { fileName, mimeType, folder = 'media' } = req.body;
      if (!fileName || !mimeType) {
        return res.status(400).json({ error: 'fileName e mimeType são obrigatórios.' });
      }

      const safeFolder = folder.replace(/[^a-zA-Z0-9_-]/g, '');
      const ext = path.extname(fileName).toLowerCase() || '.bin';
      const safeBase = path.basename(fileName, ext).replace(/[^a-zA-Z0-9_-]/g, '_').slice(0, 30);
      const uniqueId = uuidv4().slice(0, 12);
      const key = `${safeFolder}/${uniqueId}_${safeBase}${ext}`;

      const { uploadUrl, publicUrl } = await R2Service.getPresignedUploadUrl(key, mimeType, 900);

      return res.status(200).json({
        uploadUrl,
        publicUrl,
        key,
      });
    } catch (e: any) {
      console.error('Erro ao gerar presigned URL R2:', e);
      return res.status(500).json({ error: 'Falha ao gerar URL de upload: ' + e.message });
    }
  }
}

import { S3Client, PutObjectCommand, DeleteObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';

const R2_ACCOUNT_ID = process.env.R2_ACCOUNT_ID || '9a8e754d976094a4d4b422fdc38eaf99';
const R2_ACCESS_KEY_ID = process.env.R2_ACCESS_KEY_ID || 'c31ee3bcf61e6f47df6a0fed8feadab9';
const R2_SECRET_ACCESS_KEY = process.env.R2_SECRET_ACCESS_KEY || '3feb7af31853dec25d95acd7f3ec2cf28bc7bd1f0fb37f98ada3b1680c5c438b';
const R2_BUCKET_NAME = process.env.R2_BUCKET_NAME || 'livrechat-media';
const R2_ENDPOINT = process.env.R2_ENDPOINT || `https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com`;
const R2_PUBLIC_URL = process.env.R2_PUBLIC_URL || 'https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev';

export class R2Service {
  private static client: S3Client = new S3Client({
    region: 'auto',
    endpoint: R2_ENDPOINT,
    credentials: {
      accessKeyId: R2_ACCESS_KEY_ID,
      secretAccessKey: R2_SECRET_ACCESS_KEY,
    },
  });

  public static async uploadBuffer(
    buffer: Buffer,
    key: string,
    contentType: string
  ): Promise<{ url: string; key: string }> {
    const command = new PutObjectCommand({
      Bucket: R2_BUCKET_NAME,
      Key: key,
      Body: buffer,
      ContentType: contentType,
    });

    await this.client.send(command);

    const cleanPublicUrl = R2_PUBLIC_URL.replace(/\/+$/, '');
    const cleanKey = key.replace(/^\/+/, '');
    return {
      url: `${cleanPublicUrl}/${cleanKey}`,
      key: cleanKey,
    };
  }

  public static async getPresignedUploadUrl(
    key: string,
    contentType: string,
    expiresInSeconds: number = 900
  ): Promise<{ uploadUrl: string; publicUrl: string; key: string }> {
    const command = new PutObjectCommand({
      Bucket: R2_BUCKET_NAME,
      Key: key,
      ContentType: contentType,
    });

    const uploadUrl = await getSignedUrl(this.client, command, { expiresIn: expiresInSeconds });
    const cleanPublicUrl = R2_PUBLIC_URL.replace(/\/+$/, '');
    const cleanKey = key.replace(/^\/+/, '');

    return {
      uploadUrl,
      publicUrl: `${cleanPublicUrl}/${cleanKey}`,
      key: cleanKey,
    };
  }

  public static async deleteFile(key: string): Promise<void> {
    try {
      const command = new DeleteObjectCommand({
        Bucket: R2_BUCKET_NAME,
        Key: key,
      });
      await this.client.send(command);
    } catch (e) {
      console.error('Erro ao deletar arquivo no R2:', e);
    }
  }

  public static getPublicUrl(key: string): string {
    const cleanPublicUrl = R2_PUBLIC_URL.replace(/\/+$/, '');
    const cleanKey = key.replace(/^\/+/, '');
    return `${cleanPublicUrl}/${cleanKey}`;
  }
}

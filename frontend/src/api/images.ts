import { fr } from '../i18n/fr';

/** What the API accepts as an image (format read from the content there; the type of the file here). */
export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;

/** Limits of the API: checked here first to spare a useless upload, checked again by the API. */
export const AVATAR_MAX_BYTES = 1024 * 1024;
export const PRODUCT_IMAGE_MAX_BYTES = 2 * 1024 * 1024;

/** Why a chosen file cannot be sent as an image, or undefined when it can. */
export function imageProblem(file: File, maxBytes: number): string | undefined {
  if (!(IMAGE_TYPES as readonly string[]).includes(file.type)) return fr.errors.unsupportedImage;
  if (file.size > maxBytes) return fr.images.tooLarge(maxBytes / (1024 * 1024));
  return undefined;
}

/**
 * Request options of an image upload: multipart field `file`. The generated type of a binary part is a string; the
 * browser sends the file itself.
 */
export function imageUpload(file: File) {
  return {
    body: { file: file as unknown as string },
    bodySerializer: (body: { file: string } | undefined) => {
      const form = new FormData();
      form.append('file', body!.file as unknown as File);
      return form;
    },
  };
}

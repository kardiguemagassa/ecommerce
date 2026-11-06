import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined';
import Box from '@mui/material/Box';
import { fr } from '../i18n/fr';

const TINTS = ['#E3EEEF', '#EDE6DA', '#E6E9F2', '#EFE3E0'];

/**
 * Product image, 4:3 like the stored JPEG (1200 x 900 at most). Its box is sized before the image arrives (no layout
 * shift); below the fold it loads only when about to be seen. Without image, a neutral visual of the same size.
 * `decorative`: next to the product's name (tables, cart lines), hidden from screen readers so it is not read twice.
 */
export function ProductImage({ product, eager = false, decorative = false }: { product: { id?: number; name?: string; imageUrl?: string }; eager?: boolean; decorative?: boolean }) {
  const name = product.name ?? '';
  if (!product.imageUrl) {
    return (
      <Box
        role={decorative ? undefined : 'img'}
        aria-hidden={decorative || undefined}
        aria-label={decorative ? undefined : `${fr.product.photoOf(name)} : ${fr.product.noPhoto}`}
        sx={{ aspectRatio: '4 / 3', width: '100%', bgcolor: TINTS[(product.id ?? 0) % TINTS.length], display: 'grid', placeItems: 'center', color: 'text.secondary' }}
      >
        <ImageOutlinedIcon fontSize="large" aria-hidden />
      </Box>
    );
  }
  return (
    <Box
      component="img"
      src={product.imageUrl}
      alt={decorative ? '' : fr.product.photoOf(name)}
      width={1200}
      height={900}
      loading={eager ? 'eager' : 'lazy'}
      decoding="async"
      sx={{ aspectRatio: '4 / 3', width: '100%', height: 'auto', objectFit: 'cover', display: 'block', bgcolor: 'primary.light' }}
    />
  );
}

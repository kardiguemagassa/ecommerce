import PhotoOutlinedIcon from '@mui/icons-material/AddPhotoAlternateOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import { useState, type DragEvent } from 'react';
import { useDeleteProductImage, useReplaceProductImage } from '../api/admin';
import type { Product } from '../api/catalog';
import { PRODUCT_IMAGE_MAX_BYTES, imageProblem } from '../api/images';
import { notify } from '../app/notifications';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { FilePickerButton } from '../components/FilePickerButton';
import { fr } from '../i18n/fr';
import { ProductImage } from '../product/ProductImage';

/**
 * Image of a product: dropped on the zone or chosen with the button (keyboard), checked here (type, 2 MB), sent at once.
 * The API reduces it (whole, never cropped) and strips its metadata; the answer replaces the product everywhere (table, shop).
 */
export function ImageDropzone({ product }: { product: Product }) {
  const replace = useReplaceProductImage();
  const remove = useDeleteProductImage();
  const [over, setOver] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const busy = replace.isPending || remove.isPending;

  const send = (file: File | undefined) => {
    if (!file || busy) return;
    const problem = imageProblem(file, PRODUCT_IMAGE_MAX_BYTES);
    if (problem) notify.error(problem);
    else replace.mutate({ id: product.id!, file });
  };
  const drop = (event: DragEvent) => {
    event.preventDefault();
    setOver(false);
    send(event.dataTransfer.files[0]);
  };

  return (
    <Box component="section" aria-labelledby="product-image" sx={{ display: 'grid', gap: 1.5 }}>
      <Typography id="product-image" variant="subtitle1" component="h3" sx={{ fontWeight: 600 }}>
        {fr.admin.image.title}
      </Typography>
      <Box
        onDragOver={(event) => {
          event.preventDefault();
          setOver(true);
        }}
        onDragLeave={() => setOver(false)}
        onDrop={drop}
        aria-busy={busy}
        sx={{
          display: 'grid', gridTemplateColumns: '120px 1fr', gap: 2, alignItems: 'center', p: 2, borderRadius: 4,
          border: '2px dashed', borderColor: over ? 'primary.main' : 'divider', bgcolor: over ? 'primary.light' : 'transparent',
          opacity: busy ? 0.6 : 1, transition: 'background-color 150ms, border-color 150ms',
        }}
      >
        <Box sx={{ borderRadius: 2, overflow: 'hidden' }}>
          {product.imageUrl ? <ProductImage product={product} /> : <PhotoOutlinedIcon color="primary" sx={{ fontSize: 48, mx: 'auto', display: 'block' }} />}
        </Box>
        <Box sx={{ display: 'grid', gap: 1, justifyItems: 'start' }}>
          <Typography variant="body2">{fr.admin.image.drop}</Typography>
          <Typography variant="caption" color="text.secondary">
            {fr.admin.image.hint}
          </Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
            <FilePickerButton label={fr.admin.image.choose} onFile={send} size="small" variant="outlined" loading={replace.isPending} disabled={busy} />
            {product.imageUrl && (
              <Button size="small" color="error" loading={remove.isPending} disabled={busy} onClick={() => setConfirming(true)}>
                {fr.admin.image.remove}
              </Button>
            )}
          </Box>
        </Box>
      </Box>
      <ConfirmDialog
        open={confirming}
        title={fr.admin.image.removeTitle}
        hint={fr.admin.image.removeHint}
        confirmLabel={fr.admin.image.remove}
        onConfirm={() => remove.mutate(product.id!)}
        onClose={() => setConfirming(false)}
      />
    </Box>
  );
}

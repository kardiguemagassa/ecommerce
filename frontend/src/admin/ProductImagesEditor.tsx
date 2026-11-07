import PhotoOutlinedIcon from '@mui/icons-material/AddPhotoAlternateOutlined';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import { useState, type DragEvent } from 'react';
import { MAX_PRODUCT_IMAGES, useAddProductImage, useRemoveProductImage, useReorderProductImages } from '../api/admin';
import type { Product } from '../api/catalog';
import { PRODUCT_IMAGE_MAX_BYTES, imageProblem } from '../api/images';
import { notify } from '../app/notifications';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { FilePickerButton } from '../components/FilePickerButton';
import { fr } from '../i18n/fr';

type View = NonNullable<Product['images']>[number];

/**
 * The views of a product (4 at most), the first one being its main image in the catalogue. Each one moves before or
 * after its neighbour with buttons (the keyboard reaches them, a drag would not) or leaves after a confirmation. A new
 * view is dropped on the last tile or chosen with its button, checked here first (type, 2 MB), sent at once; several
 * dropped files go one after the other, as long as there is room.
 */
export function ProductImagesEditor({ product }: { product: Product }) {
  const add = useAddProductImage();
  const remove = useRemoveProductImage();
  const reorder = useReorderProductImages();
  const [over, setOver] = useState(false);
  const [removing, setRemoving] = useState<number>();
  const views = product.images ?? [];
  const room = MAX_PRODUCT_IMAGES - views.length;
  const busy = add.isPending || remove.isPending || reorder.isPending;
  const id = product.id!;

  const send = async (files: File[]) => {
    for (const file of files.slice(0, room)) {
      const problem = imageProblem(file, PRODUCT_IMAGE_MAX_BYTES);
      if (problem) {
        notify.error(problem);
        continue;
      }
      try {
        await add.mutateAsync({ id, file });
      } catch {
        return; // the mutation cache has shown why
      }
    }
    if (files.length > room) notify.error(fr.errors.imageLimit);
  };
  const drop = (event: DragEvent) => {
    event.preventDefault();
    setOver(false);
    if (!busy) void send([...event.dataTransfer.files]);
  };
  const move = (index: number, offset: -1 | 1) => {
    const ids = views.map((view) => view.id!);
    [ids[index], ids[index + offset]] = [ids[index + offset]!, ids[index]!];
    reorder.mutate({ id, imageIds: ids });
  };

  return (
    <Box component="section" aria-labelledby="product-images" aria-busy={busy} sx={{ display: 'grid', gap: 1.5 }}>
      <Box>
        <Typography id="product-images" variant="subtitle1" component="h3" sx={{ fontWeight: 600 }}>
          {fr.admin.image.title}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {fr.admin.image.count(views.length, MAX_PRODUCT_IMAGES)} · {fr.admin.image.hint}
        </Typography>
      </Box>
      <Box component="ol" sx={{ m: 0, p: 0, listStyle: 'none', display: 'grid', gap: 1.5, gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', opacity: busy ? 0.6 : 1 }}>
        {views.map((view, index) => (
          <ViewTile
            key={view.id}
            view={view}
            index={index}
            count={views.length}
            busy={busy}
            onMove={(offset) => move(index, offset)}
            onRemove={() => setRemoving(view.id)}
          />
        ))}
        {room > 0 && (
          <Box
            component="li"
            onDragOver={(event: DragEvent) => {
              event.preventDefault();
              setOver(true);
            }}
            onDragLeave={() => setOver(false)}
            onDrop={drop}
            sx={{
              aspectRatio: '4 / 3', display: 'grid', placeContent: 'center', justifyItems: 'center', gap: 1, p: 1.5, textAlign: 'center',
              borderRadius: 3, border: '2px dashed', borderColor: over ? 'primary.main' : 'divider', bgcolor: over ? 'primary.light' : 'transparent',
              transition: 'background-color 150ms, border-color 150ms',
            }}
          >
            <PhotoOutlinedIcon color="primary" aria-hidden />
            <Typography variant="caption">{fr.admin.image.drop}</Typography>
            <FilePickerButton label={fr.admin.image.add} onFile={(file) => void send([file])} size="small" variant="outlined" loading={add.isPending} disabled={busy} />
          </Box>
        )}
      </Box>
      <ConfirmDialog
        open={removing !== undefined}
        title={fr.admin.image.removeTitle}
        hint={views.length === 1 ? fr.admin.image.removeLastHint : views[0]?.id === removing ? fr.admin.image.removeMainHint : fr.admin.image.removeHint}
        confirmLabel={fr.admin.image.confirmRemove}
        onConfirm={() => remove.mutate({ id, imageId: removing! })}
        onClose={() => setRemoving(undefined)}
      />
    </Box>
  );
}

function ViewTile({ view, index, count, busy, onMove, onRemove }: { view: View; index: number; count: number; busy: boolean; onMove: (offset: -1 | 1) => void; onRemove: () => void }) {
  const position = index + 1;
  return (
    <Box component="li" sx={{ position: 'relative', borderRadius: 3, overflow: 'hidden', border: 1, borderColor: 'divider', bgcolor: '#FFFFFF' }}>
      <Box component="img" src={view.url} alt={fr.admin.image.viewOf(position)} sx={{ aspectRatio: '4 / 3', width: '100%', objectFit: 'contain', display: 'block' }} />
      {index === 0 && (
        <Typography variant="caption" sx={{ position: 'absolute', top: 8, left: 8, px: 1, py: 0.25, borderRadius: 1, bgcolor: 'primary.main', color: 'primary.contrastText', fontWeight: 600 }}>
          {fr.admin.image.main}
        </Typography>
      )}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', borderTop: 1, borderColor: 'divider', px: 0.5 }}>
        <Box>
          <IconButton size="small" aria-label={fr.admin.image.moveBefore(position)} disabled={busy || index === 0} onClick={() => onMove(-1)}>
            <ArrowBackIcon fontSize="small" />
          </IconButton>
          <IconButton size="small" aria-label={fr.admin.image.moveAfter(position)} disabled={busy || index === count - 1} onClick={() => onMove(1)}>
            <ArrowForwardIcon fontSize="small" />
          </IconButton>
        </Box>
        <IconButton size="small" color="error" aria-label={fr.admin.image.remove(position)} disabled={busy} onClick={onRemove}>
          <DeleteOutlinedIcon fontSize="small" />
        </IconButton>
      </Box>
    </Box>
  );
}

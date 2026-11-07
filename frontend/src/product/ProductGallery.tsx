import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Paper from '@mui/material/Paper';
import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import type { Product } from '../api/catalog';
import { fr } from '../i18n/fr';
import { ProductImage } from './ProductImage';

/** A swipe: the finger moved this far sideways (px) between down and up. */
const SWIPE = 50;

/**
 * The views of a product on its page. With one view (or none), the image alone, as before. With several, the view on
 * display slides to the next one (a swipe on a phone) and thumbnails below show each view; with the keyboard, the
 * arrows go from thumbnail to thumbnail. No scrolling area: the strip moves by transform, so there is nothing for the
 * keyboard to scroll (the thumbnails are its way through the views). Only the view on display is read by screen
 * readers; every view loads with the page (4 at most), so none appears blank while sliding.
 */
export function ProductGallery({ product }: { product: Product }) {
  const views = product.images ?? [];
  const thumbnails = useRef<(HTMLButtonElement | null)[]>([]);
  const swipe = useRef(0);
  const [current, setCurrent] = useState(0);
  const name = product.name ?? '';

  if (views.length < 2) {
    return (
      <Paper variant="outlined" sx={{ borderRadius: 5, overflow: 'hidden' }}>
        <ProductImage product={product} eager />
      </Paper>
    );
  }

  const show = (index: number) => {
    if (index >= 0 && index < views.length) setCurrent(index);
  };
  const keys = (event: KeyboardEvent) => {
    const next = { ArrowRight: current + 1, ArrowLeft: current - 1, Home: 0, End: views.length - 1 }[event.key];
    if (next === undefined || next < 0 || next >= views.length) return;
    event.preventDefault();
    show(next);
    thumbnails.current[next]?.focus();
  };
  const down = (event: PointerEvent) => {
    swipe.current = event.clientX;
  };
  const up = (event: PointerEvent) => {
    const moved = event.clientX - swipe.current;
    if (Math.abs(moved) > SWIPE) show(moved < 0 ? current + 1 : current - 1);
  };

  return (
    <Box sx={{ display: 'grid', gap: 1.5, minWidth: 0 }}>
      <Paper variant="outlined" sx={{ borderRadius: 5, overflow: 'hidden' }}>
        <Box
          onPointerDown={down}
          onPointerUp={up}
          sx={{
            display: 'flex', touchAction: 'pan-y', transform: `translateX(-${current * 100}%)`,
            transition: 'transform 300ms ease', '@media (prefers-reduced-motion: reduce)': { transition: 'none' },
          }}
        >
          {views.map((view, index) => (
            <Box key={view.id} aria-hidden={index !== current || undefined} sx={{ flex: '0 0 100%' }}>
              <Box
                component="img"
                src={view.url}
                alt={fr.product.viewOf(name, index + 1, views.length)}
                width={1200}
                height={900}
                decoding="async"
                draggable={false}
                sx={{ aspectRatio: '4 / 3', width: '100%', height: 'auto', objectFit: 'contain', display: 'block', bgcolor: '#FFFFFF', userSelect: 'none' }}
              />
            </Box>
          ))}
        </Box>
      </Paper>
      <Box role="group" aria-label={fr.product.views} onKeyDown={keys} sx={{ display: 'flex', gap: 1.5 }}>
        {views.map((view, index) => (
          <ButtonBase
            key={view.id}
            ref={(element: HTMLButtonElement | null) => {
              thumbnails.current[index] = element;
            }}
            aria-label={fr.product.showView(index + 1, views.length)}
            aria-pressed={index === current}
            onClick={() => show(index)}
            sx={{
              width: 88, borderRadius: 2.5, overflow: 'hidden', bgcolor: '#FFFFFF', border: 2,
              borderColor: index === current ? 'primary.main' : 'divider',
              '&.Mui-focusVisible': { outline: '3px solid', outlineColor: 'primary.main', outlineOffset: 2 },
            }}
          >
            <Box component="img" src={view.url} alt="" sx={{ aspectRatio: '4 / 3', width: '100%', objectFit: 'contain', display: 'block' }} />
          </ButtonBase>
        ))}
      </Box>
    </Box>
  );
}

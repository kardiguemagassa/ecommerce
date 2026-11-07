import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import CloseIcon from '@mui/icons-material/Close';
import ZoomInIcon from '@mui/icons-material/ZoomIn';
import ZoomOutIcon from '@mui/icons-material/ZoomOut';
import Box from '@mui/material/Box';
import Dialog from '@mui/material/Dialog';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import { visuallyHidden } from '@mui/utils';
import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import { fr } from '../i18n/fr';

export interface ZoomView {
  url: string;
}

const ZOOM = 2;
/** A swipe: the finger moved this far sideways (px) between down and up. */
const SWIPE = 50;
/** Two taps this close (ms) make a double tap. */
const DOUBLE_TAP = 300;

/**
 * The views of a product full screen, on a dark background. One view at a time, whole; the arrows (buttons, keyboard,
 * a swipe on a phone) go from one to the next. A double click or double tap, the "+" and "−" keys or the button zoom
 * twice: the picture then follows the pointer. Escape, the close button or a click on the background close it; the
 * focus goes back where it was (MUI Dialog). The pictures are the ones of the page (1200 x 900 at most), already loaded.
 */
export function ImageZoom({ name, views, start, onClose }: { name: string; views: ZoomView[]; start: number; onClose: (index: number) => void }) {
  const [index, setIndex] = useState(start);
  const [zoomed, setZoomed] = useState(false);
  const [origin, setOrigin] = useState('50% 50%');
  const pointer = useRef<{ x: number; at: number; lastTap: number }>({ x: 0, at: 0, lastTap: 0 });
  const count = views.length;

  const go = (next: number) => {
    if (next < 0 || next >= count) return;
    setIndex(next);
    setZoomed(false);
    setOrigin('50% 50%');
  };
  const close = () => onClose(index);

  const keys = (event: KeyboardEvent) => {
    if (event.key === 'ArrowRight') go(index + 1);
    else if (event.key === 'ArrowLeft') go(index - 1);
    else if (event.key === '+' || event.key === '=') setZoomed(true);
    else if (event.key === '-') setZoomed(false);
    else return;
    event.preventDefault();
  };
  // The zoomed picture is moved by the pointer: the point under it stays under it.
  const follow = (event: PointerEvent<HTMLImageElement>) => {
    if (!zoomed) return;
    const box = event.currentTarget.getBoundingClientRect();
    if (!box.width || !box.height) return;
    setOrigin(`${((event.clientX - box.left) / box.width) * 100}% ${((event.clientY - box.top) / box.height) * 100}%`);
  };
  const down = (event: PointerEvent<HTMLImageElement>) => {
    pointer.current = { ...pointer.current, x: event.clientX, at: event.timeStamp };
  };
  const up = (event: PointerEvent<HTMLImageElement>) => {
    const moved = event.clientX - pointer.current.x;
    if (!zoomed && Math.abs(moved) > SWIPE) {
      go(moved < 0 ? index + 1 : index - 1);
      return;
    }
    if (event.timeStamp - pointer.current.lastTap < DOUBLE_TAP) {
      follow(event);
      setZoomed((value) => !value);
      pointer.current.lastTap = 0;
    } else {
      pointer.current.lastTap = event.timeStamp;
    }
  };

  const view = views[index]!;
  const title = fr.product.zoomTitle(name);
  return (
    <Dialog
      fullScreen
      open
      onClose={close}
      onKeyDown={keys}
      aria-labelledby="image-zoom-title"
      slotProps={{ paper: { sx: { bgcolor: '#151718', color: '#FFFFFF' } } }}
    >
      <Typography id="image-zoom-title" component="h2" sx={visuallyHidden}>
        {title}
      </Typography>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, px: { xs: 1, sm: 2 }, py: 1 }}>
        <Typography aria-live="polite" sx={{ fontVariantNumeric: 'tabular-nums', px: 1 }}>
          {count > 1 ? fr.product.zoomCounter(index + 1, count) : ''}
        </Typography>
        <Box>
          <IconButton aria-label={zoomed ? fr.product.zoomOut : fr.product.zoomIn} onClick={() => setZoomed((value) => !value)} sx={{ color: 'inherit' }}>
            {zoomed ? <ZoomOutIcon /> : <ZoomInIcon />}
          </IconButton>
          <IconButton aria-label={fr.product.closeZoom} onClick={close} sx={{ color: 'inherit' }}>
            <CloseIcon />
          </IconButton>
        </Box>
      </Box>
      <Box
        onClick={(event) => {
          if (event.target === event.currentTarget) close(); // the background, not the picture
        }}
        sx={{ position: 'relative', flex: 1, minHeight: 0, display: 'grid', placeItems: 'center', overflow: 'hidden', px: { xs: 0, sm: 8 } }}
      >
        <Box
          component="img"
          key={view.url}
          src={view.url}
          alt={count > 1 ? fr.product.viewOf(name, index + 1, count) : fr.product.photoOf(name)}
          draggable={false}
          onPointerDown={down}
          onPointerUp={up}
          onPointerMove={follow}
          sx={{
            maxWidth: '100%', maxHeight: '100%', objectFit: 'contain', display: 'block', userSelect: 'none',
            bgcolor: '#FFFFFF', touchAction: zoomed ? 'none' : 'pinch-zoom',
            cursor: zoomed ? 'zoom-out' : 'zoom-in',
            transform: zoomed ? `scale(${ZOOM})` : 'none', transformOrigin: origin,
            transition: 'transform 200ms ease',
            '@media (prefers-reduced-motion: reduce)': { transition: 'none' },
          }}
        />
        {count > 1 && (
          <>
            <IconButton
              aria-label={fr.product.previousView}
              disabled={index === 0}
              onClick={() => go(index - 1)}
              sx={{ position: 'absolute', left: { xs: 4, sm: 16 }, top: '50%', transform: 'translateY(-50%)', ...arrow }}
            >
              <ChevronLeftIcon fontSize="large" />
            </IconButton>
            <IconButton
              aria-label={fr.product.nextView}
              disabled={index === count - 1}
              onClick={() => go(index + 1)}
              sx={{ position: 'absolute', right: { xs: 4, sm: 16 }, top: '50%', transform: 'translateY(-50%)', ...arrow }}
            >
              <ChevronRightIcon fontSize="large" />
            </IconButton>
          </>
        )}
      </Box>
    </Dialog>
  );
}

const arrow = {
  color: '#FFFFFF', bgcolor: 'rgba(0, 0, 0, 0.45)',
  '&:hover': { bgcolor: 'rgba(0, 0, 0, 0.65)' },
  '&.Mui-disabled': { color: 'rgba(255, 255, 255, 0.3)', bgcolor: 'rgba(0, 0, 0, 0.2)' },
};

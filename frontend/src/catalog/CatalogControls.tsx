import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import TextField from '@mui/material/TextField';
import { useCallback, useEffect, useRef, useState } from 'react';
import { SORTS, type Category, type Sort } from '../api/catalog';
import { fr } from '../i18n/fr';

/** Width of the faded edge of the rail, and the margin kept between a revealed category and that edge. */
const FADE = 48;

/**
 * Category filter: toggle buttons (aria-pressed), "Toutes" first, on one line that scrolls sideways: its height stays
 * the same whatever the number of categories. Faded edges show there is more; on wider screens, arrows scroll by a
 * page for the mouse. The keyboard needs no arrow: Tab moves along the buttons and the rail follows the focus, and the
 * selected category is brought into view.
 */
export function CategoryFilter({ categories, selected, onSelect }: { categories: Category[]; selected?: number; onSelect: (id?: number) => void }) {
  const rail = useRef<HTMLDivElement>(null);
  const [edges, setEdges] = useState({ start: false, end: false });

  const measure = useCallback(() => {
    const element = rail.current;
    if (!element) return;
    const start = element.scrollLeft > 1;
    const end = element.scrollLeft + element.clientWidth < element.scrollWidth - 1;
    setEdges((previous) => (previous.start === start && previous.end === end ? previous : { start, end }));
  }, []);

  /** Scrolls the rail just enough for a category to sit clear of the faded edges. */
  const reveal = useCallback((chip: HTMLElement | null | undefined) => {
    const element = rail.current;
    if (!element || !chip) return;
    if (chip.offsetLeft - FADE < element.scrollLeft) element.scrollLeft = Math.max(0, chip.offsetLeft - FADE);
    else if (chip.offsetLeft + chip.offsetWidth + FADE > element.scrollLeft + element.clientWidth)
      element.scrollLeft = chip.offsetLeft + chip.offsetWidth + FADE - element.clientWidth;
  }, []);

  useEffect(() => {
    const element = rail.current;
    if (!element) return;
    measure();
    const observer = typeof ResizeObserver === 'undefined' ? undefined : new ResizeObserver(measure);
    observer?.observe(element);
    window.addEventListener('resize', measure);
    return () => {
      observer?.disconnect();
      window.removeEventListener('resize', measure);
    };
  }, [measure, categories]);

  useEffect(() => {
    reveal(rail.current?.querySelector<HTMLElement>('[aria-pressed="true"]'));
    measure();
  }, [reveal, measure, selected, categories]);

  const page = (direction: 1 | -1) => {
    const element = rail.current;
    if (!element) return;
    const left = direction * Math.max(element.clientWidth - 2 * FADE, FADE);
    const calm = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (typeof element.scrollBy === 'function') element.scrollBy({ left, behavior: calm ? 'auto' : 'smooth' });
    else element.scrollLeft += left;
  };

  const chip = (label: string, id: number | undefined) => {
    const pressed = selected === id;
    return (
      <Button
        key={id ?? 'all'}
        variant={pressed ? 'contained' : 'outlined'}
        color={pressed ? 'primary' : 'inherit'}
        aria-pressed={pressed}
        onClick={() => onSelect(id)}
        sx={{ flexShrink: 0, whiteSpace: 'nowrap', borderRadius: 5, height: 40, px: 2, borderColor: pressed ? undefined : '#CFCCC4', fontWeight: pressed ? 600 : 500 }}
      >
        {label}
      </Button>
    );
  };

  const fade = `linear-gradient(to right, ${edges.start ? `transparent, #000 ${FADE}px` : '#000'}, ${edges.end ? `#000 calc(100% - ${FADE}px), transparent` : '#000'})`;
  return (
    <Box sx={{ position: 'relative', minWidth: 0 }}>
      <Box
        ref={rail}
        role="group"
        aria-label={fr.catalog.categories}
        onScroll={measure}
        onFocus={(event) => event.target !== rail.current && reveal(event.target as HTMLElement)}
        sx={{
          position: 'relative', // offsetLeft of the categories is measured from the rail
          display: 'flex', gap: 1, overflowX: 'auto', p: 0.5, mx: -0.5, // room for the focus ring
          scrollbarWidth: 'none', '&::-webkit-scrollbar': { display: 'none' },
          maskImage: fade, WebkitMaskImage: fade,
        }}
      >
        {chip(fr.catalog.allCategories, undefined)}
        {categories.map((category) => chip(category.name ?? '', category.id))}
      </Box>
      {edges.start && <ScrollArrow side="start" onClick={() => page(-1)} />}
      {edges.end && <ScrollArrow side="end" onClick={() => page(1)} />}
    </Box>
  );
}

/**
 * Arrow over a faded edge of the rail, for the mouse only (hidden on phones, where the rail is swiped): out of the
 * tab order and hidden from screen readers, which reach every category with Tab.
 */
function ScrollArrow({ side, onClick }: { side: 'start' | 'end'; onClick: () => void }) {
  return (
    <ButtonBase
      component="span"
      tabIndex={-1}
      aria-hidden
      data-scroll={side}
      onClick={onClick}
      sx={{
        display: { xs: 'none', sm: 'inline-flex' },
        position: 'absolute', top: '50%', transform: 'translateY(-50%)', [side === 'start' ? 'left' : 'right']: -4,
        width: 36, height: 36, borderRadius: '50%', bgcolor: 'background.paper', border: 1, borderColor: 'divider',
        boxShadow: '0 1px 4px rgba(27, 29, 31, 0.12)', color: 'text.primary',
        '&:hover': { bgcolor: 'primary.light', color: 'primary.main' },
      }}
    >
      {side === 'start' ? <ChevronLeftIcon /> : <ChevronRightIcon />}
    </ButtonBase>
  );
}

/** Sort order: a native select (keyboard and screen readers handle it everywhere). */
export function SortSelect({ value, onChange }: { value: Sort; onChange: (sort: Sort) => void }) {
  return (
    <TextField
      select
      size="small"
      label={fr.catalog.sortLabel}
      value={value}
      onChange={(event) => onChange(event.target.value as Sort)}
      slotProps={{ select: { native: true } }}
      sx={{ minWidth: 200, bgcolor: 'background.paper' }}
    >
      {(Object.keys(SORTS) as Sort[]).map((sort) => (
        <option key={sort} value={sort}>
          {fr.catalog.sorts[sort]}
        </option>
      ))}
    </TextField>
  );
}

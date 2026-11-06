import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import { SORTS, type Category, type Sort } from '../api/catalog';
import { fr } from '../i18n/fr';

/** Category filter: toggle buttons (aria-pressed), "Toutes" first. */
export function CategoryFilter({ categories, selected, onSelect }: { categories: Category[]; selected?: number; onSelect: (id?: number) => void }) {
  const chip = (label: string, id: number | undefined) => {
    const pressed = selected === id;
    return (
      <Button
        key={id ?? 'all'}
        variant={pressed ? 'contained' : 'outlined'}
        color={pressed ? 'primary' : 'inherit'}
        aria-pressed={pressed}
        onClick={() => onSelect(id)}
        sx={{ borderRadius: 5, height: 40, px: 2, borderColor: pressed ? undefined : '#CFCCC4', fontWeight: pressed ? 600 : 500 }}
      >
        {label}
      </Button>
    );
  };
  return (
    <Box role="group" aria-label={fr.catalog.categories} sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
      {chip(fr.catalog.allCategories, undefined)}
      {categories.map((category) => chip(category.name ?? '', category.id))}
    </Box>
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

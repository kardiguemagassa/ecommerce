import SearchIcon from '@mui/icons-material/Search';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import TextField from '@mui/material/TextField';
import { useState, type FormEvent } from 'react';
import { fr } from '../i18n/fr';

/**
 * Search of an admin table: the term goes to the address on submit (back to page 1). Remount it with `key={q}` so it
 * follows the address (back button).
 */
export function SearchBox({ label, q, onSearch }: { label: string; q: string; onSearch: (q: string) => void }) {
  const [term, setTerm] = useState(q);
  const submit = (event: FormEvent) => {
    event.preventDefault();
    onSearch(term.trim());
  };
  return (
    <Box component="form" role="search" onSubmit={submit} sx={{ flex: '1 1 280px', maxWidth: 420 }}>
      <TextField
        type="search"
        size="small"
        fullWidth
        label={label}
        value={term}
        onChange={(event) => setTerm(event.target.value)}
        slotProps={{
          input: {
            endAdornment: (
              <InputAdornment position="end">
                <IconButton type="submit" edge="end" aria-label={fr.admin.search}>
                  <SearchIcon />
                </IconButton>
              </InputAdornment>
            ),
          },
        }}
      />
    </Box>
  );
}

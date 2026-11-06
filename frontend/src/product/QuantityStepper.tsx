import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import { fr } from '../i18n/fr';

/**
 * Quantity between 1 and the stock; the value is announced to screen readers when it changes. `label` tells apart
 * several steppers of one page (cart lines).
 */
export function QuantityStepper({ value, max, onChange, label = fr.product.quantity }: { value: number; max: number; onChange: (value: number) => void; label?: string }) {
  return (
    <Box role="group" aria-label={label} sx={{ display: 'inline-flex', alignItems: 'center', border: 1, borderColor: '#CFCCC4', borderRadius: 2.5, bgcolor: 'background.paper' }}>
      <IconButton aria-label={fr.product.decrease} disabled={value <= 1} onClick={() => onChange(value - 1)} sx={{ width: 44, height: 48 }}>
        <RemoveIcon />
      </IconButton>
      <Box component="output" aria-live="polite" sx={{ width: 40, textAlign: 'center', fontWeight: 600 }}>
        {value}
      </Box>
      <IconButton aria-label={fr.product.increase} disabled={value >= max} onClick={() => onChange(value + 1)} sx={{ width: 44, height: 48 }}>
        <AddIcon />
      </IconButton>
    </Box>
  );
}

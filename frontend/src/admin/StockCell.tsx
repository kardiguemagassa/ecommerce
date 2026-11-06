import Box from '@mui/material/Box';
import { fr } from '../i18n/fr';

export const LOW_STOCK = 5;

/** Stock of a product, in words when it needs attention (never the colour alone). */
export function StockCell({ stock }: { stock: number }) {
  if (stock === 0) return <Badge tone="danger">{fr.admin.products.outOfStock}</Badge>;
  if (stock < LOW_STOCK) return <Badge tone="warning">{fr.admin.products.lowStock(stock)}</Badge>;
  return <>{stock}</>;
}

const TONES = {
  danger: { bgcolor: '#FBE9E7', color: '#9A2B12' },
  warning: { bgcolor: '#FFF1DC', color: '#8A4B08' },
};

function Badge({ tone, children }: { tone: keyof typeof TONES; children: string }) {
  return (
    <Box component="span" sx={{ ...TONES[tone], px: 1.25, py: 0.5, borderRadius: 4, fontSize: 13, fontWeight: 600, whiteSpace: 'nowrap' }}>
      {children}
    </Box>
  );
}

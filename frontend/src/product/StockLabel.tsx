import Typography from '@mui/material/Typography';
import { fr } from '../i18n/fr';

export const LOW_STOCK = 3;

/** Availability: in stock, a few left (urgency without alarm), or out of stock. */
export function StockLabel({ stock, detailed = false }: { stock: number; detailed?: boolean }) {
  const [text, color] =
    stock <= 0
      ? [fr.product.outOfStock, 'error.main']
      : stock <= LOW_STOCK
        ? [fr.product.lowStock(stock), 'warning.main']
        : [detailed ? fr.product.inStock(stock) : fr.product.available, 'success.main'];
  return (
    <Typography variant="body2" sx={{ color, fontWeight: 500 }}>
      {text}
    </Typography>
  );
}

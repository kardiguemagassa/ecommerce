import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router';
import type { Product } from '../api/catalog';
import { formatPrice } from '../i18n/format';
import { AddToCartButton } from '../product/AddToCartButton';
import { ProductImage } from '../product/ProductImage';
import { StockLabel } from '../product/StockLabel';

export function ProductCard({ product }: { product: Product }) {
  const href = `/produits/${product.id}`;
  return (
    <Paper component="article" variant="outlined" sx={{ borderRadius: 4, overflow: 'hidden', display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* The image repeats the title link: hidden from assistive technologies and from the tab order. */}
      <RouterLink to={href} tabIndex={-1} aria-hidden>
        <ProductImage product={product} />
      </RouterLink>
      <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 0.75, flex: 1 }}>
        <Typography variant="caption" sx={{ fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase', color: 'text.secondary' }}>
          {product.categoryName}
        </Typography>
        <Typography variant="h3" component="h3">
          <Link component={RouterLink} to={href} color="inherit" underline="hover">
            {product.name}
          </Link>
        </Typography>
        <StockLabel stock={product.stock ?? 0} />
        <Box sx={{ mt: 'auto', pt: 1.5, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1.5 }}>
          <Typography sx={{ fontFamily: 'Manrope, sans-serif', fontSize: 20, fontWeight: 800 }}>{formatPrice(product.price)}</Typography>
          <AddToCartButton product={product} sx={{ height: 44 }} />
        </Box>
      </Box>
    </Paper>
  );
}

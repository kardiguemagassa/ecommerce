import Box from '@mui/material/Box';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import Link from '@mui/material/Link';
import Skeleton from '@mui/material/Skeleton';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router';
import { DEFAULT_SORT, productQuery } from '../api/catalog';
import { catalogHref } from '../catalog/filters';
import { fr } from '../i18n/fr';
import { formatPrice } from '../i18n/format';
import { AddToCartButton } from '../product/AddToCartButton';
import { ProductGallery } from '../product/ProductGallery';
import { QuantityStepper } from '../product/QuantityStepper';
import { StockLabel } from '../product/StockLabel';

/** Product page; the route loader has fetched the product (or answered 404, shown by the route error boundary). */
export function ProductPage() {
  const id = Number(useParams().id);
  const { data: product } = useQuery(productQuery(id));
  const [quantity, setQuantity] = useState(1);

  if (!product) return <Skeleton variant="rounded" height={480} sx={{ borderRadius: 5 }} />;

  const stock = product.stock ?? 0;
  return (
    <Box sx={{ display: 'grid', gap: 3 }}>
      <Breadcrumbs aria-label={fr.product.breadcrumb}>
        <Link component={RouterLink} to="/" underline="hover">
          {fr.pages.catalog}
        </Link>
        {product.categoryId && (
          <Link component={RouterLink} to={catalogHref({ q: '', categoryId: product.categoryId, sort: DEFAULT_SORT, page: 1 })} underline="hover">
            {product.categoryName}
          </Link>
        )}
        <Typography color="text.primary" aria-current="page">
          {product.name}
        </Typography>
      </Breadcrumbs>

      <Box sx={{ display: 'grid', gap: { xs: 3, md: 5 }, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 1fr))' }, alignItems: 'start' }}>
        <ProductGallery product={product} />

        <Box sx={{ display: 'grid', gap: 2.25 }}>
          <Typography variant="overline" sx={{ justifySelf: 'start', lineHeight: 1, px: 1.25, py: 0.75, borderRadius: 1.5, bgcolor: 'primary.light', color: 'primary.main', fontWeight: 600 }}>
            {product.categoryName}
          </Typography>
          <Typography variant="h1" component="h1" sx={{ fontSize: { xs: '1.75rem', md: '2.25rem' } }}>
            {product.name}
          </Typography>
          {product.description && (
            <Typography sx={{ fontSize: 16, lineHeight: 1.6, color: '#3D403F', maxWidth: '56ch', whiteSpace: 'pre-line' }}>{product.description}</Typography>
          )}
          <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 2, flexWrap: 'wrap' }}>
            <Typography sx={{ fontFamily: 'Manrope, sans-serif', fontSize: 34, fontWeight: 800 }}>{formatPrice(product.price)}</Typography>
            <StockLabel stock={stock} detailed />
          </Box>
          {stock > 0 && (
            <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.5, pt: 1 }}>
              <QuantityStepper value={Math.min(quantity, stock)} max={stock} onChange={setQuantity} />
              <AddToCartButton product={product} quantity={Math.min(quantity, stock)} label={fr.cart.addToCart} size="large" sx={{ height: 50, px: 3.5 }} />
            </Box>
          )}
          <Box component="dl" sx={{ m: 0, mt: 1, pt: 2.25, borderTop: 1, borderColor: 'divider', display: 'grid', gridTemplateColumns: 'max-content 1fr', gap: '10px 24px', fontSize: 14 }}>
            <Box component="dt" sx={{ color: 'text.secondary' }}>{fr.product.reference}</Box>
            <Box component="dd" sx={{ m: 0 }}>PRD-{String(product.id).padStart(4, '0')}</Box>
            <Box component="dt" sx={{ color: 'text.secondary' }}>{fr.product.delivery}</Box>
            <Box component="dd" sx={{ m: 0 }}>{fr.product.deliveryValue}</Box>
            <Box component="dt" sx={{ color: 'text.secondary' }}>{fr.product.returns}</Box>
            <Box component="dd" sx={{ m: 0 }}>{fr.product.returnsValue}</Box>
          </Box>
        </Box>
      </Box>
    </Box>
  );
}

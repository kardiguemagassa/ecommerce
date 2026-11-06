import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router';
import { bestSellersQuery } from '../api/catalog';
import { fr } from '../i18n/fr';

/** The 4 best sellers (ranking cached 5 minutes by the API); nothing when no order was placed yet. */
export function BestSellers() {
  const { data, isPending } = useQuery(bestSellersQuery());
  if (!isPending && !data?.length) return null;

  return (
    <Paper component="section" variant="outlined" aria-labelledby="best-sellers" sx={{ p: 3, borderRadius: 4, display: 'grid', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
        <Typography id="best-sellers" variant="h2" component="h2">
          {fr.catalog.bestSellers}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {fr.catalog.bestSellersHint}
        </Typography>
      </Box>
      <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 1.5 }}>
        {isPending
          ? Array.from({ length: 4 }, (_, index) => (
              <li key={index}>
                <Skeleton variant="rounded" height={52} />
              </li>
            ))
          : data!.map((item, index) => (
              <Box component="li" key={item.productId} sx={{ display: 'flex', alignItems: 'center', gap: 1.5, p: 1.25, borderRadius: 3, bgcolor: 'background.default' }}>
                <Box aria-hidden sx={{ width: 32, height: 32, flex: 'none', borderRadius: '50%', bgcolor: 'primary.main', color: 'primary.contrastText', display: 'grid', placeItems: 'center', fontFamily: 'Manrope, sans-serif', fontWeight: 800 }}>
                  {index + 1}
                </Box>
                <Box sx={{ minWidth: 0, display: 'grid' }}>
                  <Link component={RouterLink} to={`/produits/${item.productId}`} color="inherit" underline="hover" noWrap sx={{ fontWeight: 600 }}>
                    {item.productName}
                  </Link>
                  <Typography variant="body2" color="text.secondary">
                    {fr.catalog.sold(item.totalQuantity ?? 0)}
                  </Typography>
                </Box>
              </Box>
            ))}
      </Box>
    </Paper>
  );
}

import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router';
import { BEST_SELLERS_LIMIT } from '../../api/admin';
import { bestSellersQuery } from '../../api/catalog';
import { fr } from '../../i18n/fr';

/** The ten best sellers, read only. */
export function AdminBestSellersPage() {
  const { data: ranking } = useQuery(bestSellersQuery(BEST_SELLERS_LIMIT));
  return (
    <Box sx={{ display: 'grid', gap: 2, maxWidth: 720 }}>
      <Typography color="text.secondary">{fr.admin.bestSellers.note}</Typography>
      {!ranking ? (
        <Skeleton variant="rounded" height={320} sx={{ borderRadius: 5 }} />
      ) : ranking.length === 0 ? (
        <Typography>{fr.admin.bestSellers.empty}</Typography>
      ) : (
        <Paper variant="outlined" sx={{ borderRadius: 5, overflow: 'hidden' }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 80 }}>{fr.admin.bestSellers.rank}</TableCell>
                <TableCell>{fr.admin.bestSellers.product}</TableCell>
                <TableCell align="right">{fr.admin.bestSellers.sold}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {ranking.map((entry, index) => (
                <TableRow key={entry.productId}>
                  <TableCell sx={{ fontWeight: 700 }}>{index + 1}</TableCell>
                  <TableCell component="th" scope="row">
                    <Link component={RouterLink} to={`/produits/${entry.productId}`} underline="hover" color="text.primary">
                      {entry.productName}
                    </Link>
                  </TableCell>
                  <TableCell align="right">{entry.totalQuantity}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
    </Box>
  );
}

import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Pagination from '@mui/material/Pagination';
import PaginationItem from '@mui/material/PaginationItem';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink, useSearchParams } from 'react-router';
import { orderPageFrom, ordersQuery } from '../api/orders';
import { OrderStatusChip } from '../components/OrderStatusChip';
import { Notice } from '../components/RequireRole';
import { fr } from '../i18n/fr';
import { formatDate, formatPrice } from '../i18n/format';

const pageHref = (page: number) => (page > 1 ? `/commandes?page=${page}` : '/commandes');

/** The customer's orders, most recent first; the page number lives in the address (shareable, back button). */
export function OrdersPage() {
  const [params] = useSearchParams();
  const page = orderPageFrom(params);
  const { data, isPlaceholderData } = useQuery(ordersQuery(page));

  const orders = data?.content ?? [];
  const pages = data?.page.totalPages ?? 0;
  return (
    <Box sx={{ display: 'grid', gap: 3 }}>
      <Typography variant="h1" component="h1">
        {fr.pages.orders}
      </Typography>
      {!data ? (
        <Skeleton variant="rounded" height={320} sx={{ borderRadius: 5 }} />
      ) : data.page.totalElements === 0 ? (
        <Notice heading="h2" title={fr.orders.empty} hint={fr.orders.emptyHint} icon={<ReceiptLongOutlinedIcon color="primary" fontSize="large" />}>
          <Button component={RouterLink} to="/" variant="contained">
            {fr.cart.browse}
          </Button>
        </Notice>
      ) : (
        <>
          <Paper variant="outlined" sx={{ borderRadius: 5, overflow: 'hidden', opacity: isPlaceholderData ? 0.6 : 1, transition: 'opacity 150ms' }}>
            <TableContainer>
              <Table sx={{ minWidth: 560 }} aria-busy={isPlaceholderData}>
                <TableHead>
                  <TableRow>
                    <TableCell>{fr.orders.number}</TableCell>
                    <TableCell>{fr.orders.date}</TableCell>
                    <TableCell>{fr.orders.status}</TableCell>
                    <TableCell align="right">{fr.orders.total}</TableCell>
                    <TableCell align="right">{fr.orders.action}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {orders.map((order) => {
                    const pending = order.status === 'PENDING';
                    return (
                      <TableRow key={order.id} hover>
                        <TableCell component="th" scope="row" sx={{ fontWeight: 600 }}>
                          {order.id}
                        </TableCell>
                        <TableCell>{formatDate(order.createdAt)}</TableCell>
                        <TableCell>
                          <OrderStatusChip status={order.status ?? 'PENDING'} />
                        </TableCell>
                        <TableCell align="right">{formatPrice(order.totalAmount)}</TableCell>
                        <TableCell align="right">
                          <Button
                            component={RouterLink}
                            to={`/commandes/${order.id}`}
                            size="small"
                            variant={pending ? 'contained' : 'outlined'}
                            aria-label={pending ? fr.orders.trackLabel(order.id!) : fr.orders.detailsLabel(order.id!)}
                          >
                            {pending ? fr.orders.track : fr.orders.details}
                          </Button>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
            <Typography color="text.secondary">{fr.orders.summary(data.page.totalElements, page, pages)}</Typography>
            {pages > 1 && (
              <Pagination
                page={Math.min(page, pages)}
                count={pages}
                shape="rounded"
                color="primary"
                aria-label={fr.orders.pagination}
                getItemAriaLabel={(type, number, selected) =>
                  type === 'page' ? fr.catalog.pageLabel(number ?? 1, selected) : type === 'previous' ? fr.catalog.previous : fr.catalog.next
                }
                renderItem={(item) => <PaginationItem component={RouterLink} to={pageHref(item.page ?? 1)} {...item} />}
              />
            )}
          </Box>
        </>
      )}
    </Box>
  );
}

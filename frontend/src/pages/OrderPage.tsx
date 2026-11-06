import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Step from '@mui/material/Step';
import StepLabel from '@mui/material/StepLabel';
import Stepper from '@mui/material/Stepper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableFooter from '@mui/material/TableFooter';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { Link as RouterLink, useParams } from 'react-router';
import { orderQuery, type Order, type OrderStatus } from '../api/orders';
import { notify } from '../app/notifications';
import { OrderStatusChip } from '../components/OrderStatusChip';
import { fr } from '../i18n/fr';
import { formatDateTime, formatPrice, formatTime } from '../i18n/format';

/**
 * Live tracking of an order; the route loader has fetched it (or answered 404). The query polls the API while the
 * order is PENDING: the status, the steps and the hint follow without reloading.
 */
export function OrderPage() {
  const id = Number(useParams().id);
  const { data: order } = useQuery(orderQuery(id));
  useConfirmationToast(order);

  if (!order) return <Skeleton variant="rounded" height={480} sx={{ borderRadius: 5 }} />;

  const status = order.status ?? 'PENDING';
  const confirmed = status === 'CONFIRMED';
  return (
    <Box sx={{ display: 'grid', gap: 3 }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Box>
          <Typography variant="h1" component="h1">
            {fr.order.title(id)}
          </Typography>
          <Typography color="text.secondary">{fr.order.placedOn(formatDateTime(order.createdAt))}</Typography>
        </Box>
        {/* Announced by screen readers when it changes, without moving the focus. */}
        <Box role="status" aria-live="polite">
          <OrderStatusChip status={status} long />
        </Box>
      </Box>

      <Paper variant="outlined" sx={{ borderRadius: 5, p: { xs: 2.5, sm: 3.5 }, display: 'grid', gap: 2.5 }}>
        <Stepper activeStep={confirmed ? 3 : 1} alternativeLabel aria-label={fr.order.progress}>
          <Step>
            <StepLabel optional={<Caption>{formatTime(order.createdAt)}</Caption>}>{fr.order.received}</StepLabel>
          </Step>
          <Step>
            <StepLabel optional={confirmed ? undefined : <Caption>{fr.order.confirmingHint}</Caption>}>{fr.order.confirming}</StepLabel>
          </Step>
          <Step>
            <StepLabel>{fr.order.confirmedStep}</StepLabel>
          </Step>
        </Stepper>
        <Typography color="text.secondary" sx={{ textAlign: 'center' }}>
          {confirmed ? fr.order.confirmedHint : fr.order.pendingHint}
        </Typography>
      </Paper>

      <Paper variant="outlined" component="section" aria-labelledby="order-items" sx={{ borderRadius: 5, overflow: 'hidden' }}>
        <Typography id="order-items" variant="h2" sx={{ fontSize: 20, px: 3, pt: 2.5, pb: 1 }}>
          {fr.order.items}
        </Typography>
        <TableContainer>
          <Table sx={{ minWidth: 520 }}>
            <TableHead>
              <TableRow>
                <TableCell>{fr.order.product}</TableCell>
                <TableCell align="right">{fr.order.quantity}</TableCell>
                <TableCell align="right">{fr.order.unitPrice}</TableCell>
                <TableCell align="right">{fr.order.subtotal}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {order.items?.map((item) => (
                <TableRow key={item.productId}>
                  <TableCell>
                    <Link component={RouterLink} to={`/produits/${item.productId}`} underline="hover" color="text.primary">
                      {item.productName}
                    </Link>
                  </TableCell>
                  <TableCell align="right">{item.quantity}</TableCell>
                  <TableCell align="right">{formatPrice(item.unitPrice)}</TableCell>
                  <TableCell align="right">{formatPrice(item.subtotal)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
            <TableFooter>
              <TableRow>
                <TableCell component="th" scope="row" colSpan={3} sx={{ fontSize: 16, fontWeight: 700, color: 'text.primary' }}>
                  {fr.order.total}
                </TableCell>
                <TableCell align="right" sx={{ fontFamily: 'Manrope, sans-serif', fontSize: 20, fontWeight: 800, color: 'text.primary' }}>
                  {formatPrice(order.totalAmount)}
                </TableCell>
              </TableRow>
            </TableFooter>
          </Table>
        </TableContainer>
      </Paper>

      <Button component={RouterLink} to="/commandes" startIcon={<ArrowBackIcon />} sx={{ justifySelf: 'start' }}>
        {fr.order.allOrders}
      </Button>
    </Box>
  );
}

const Caption = ({ children }: { children: string }) => (
  <Typography variant="caption" color="text.secondary">
    {children}
  </Typography>
);

/** A toast when the confirmation arrives while the page is open (not for an order already confirmed). */
function useConfirmationToast(order: Order | undefined) {
  const seen = useRef<OrderStatus | undefined>(undefined);
  useEffect(() => {
    const status = order?.status;
    if (seen.current === 'PENDING' && status === 'CONFIRMED') notify.success(fr.order.confirmed(order!.id!));
    seen.current = status;
  }, [order]);
}

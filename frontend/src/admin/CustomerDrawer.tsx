import CloseIcon from '@mui/icons-material/Close';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import Accordion from '@mui/material/Accordion';
import AccordionDetails from '@mui/material/AccordionDetails';
import AccordionSummary from '@mui/material/AccordionSummary';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import Skeleton from '@mui/material/Skeleton';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { customerOrderQuery, customerOrdersQuery, customerQuery } from '../api/admin';
import { OrderStatusChip } from '../components/OrderStatusChip';
import { UserAvatar } from '../components/UserAvatar';
import { fr } from '../i18n/fr';
import { formatDate, formatPrice } from '../i18n/format';

/** Customer support: identity and latest orders; an order unfolds to its items (fetched when unfolded). */
export function CustomerDrawer({ customerId, onClose }: { customerId?: number; onClose: () => void }) {
  return (
    <Drawer
      anchor="right"
      open={customerId !== undefined}
      onClose={onClose}
      slotProps={{ paper: { sx: { width: { xs: '100%', sm: 520 }, p: { xs: 2.5, sm: 3.5 } }, role: 'dialog', 'aria-labelledby': 'customer-drawer-title' } as object }}
    >
      {customerId !== undefined && <CustomerDetail key={customerId} id={customerId} onClose={onClose} />}
    </Drawer>
  );
}

function CustomerDetail({ id, onClose }: { id: number; onClose: () => void }) {
  const { data: customer } = useQuery(customerQuery(id));
  const { data: orders } = useQuery(customerOrdersQuery(id));

  return (
    <Box sx={{ display: 'grid', gap: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <UserAvatar name={customer?.fullName} photo={customer?.avatarUrl} size={56} />
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography id="customer-drawer-title" variant="h2" sx={{ fontSize: 22 }}>
            {customer?.fullName ?? <Skeleton width={180} />}
          </Typography>
          <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
            {customer?.email}
          </Typography>
        </Box>
        <IconButton aria-label={fr.admin.customers.close} onClick={onClose} edge="end">
          <CloseIcon />
        </IconButton>
      </Box>

      <Box component="section" aria-labelledby="customer-orders" sx={{ display: 'grid', gap: 1.5 }}>
        <Typography id="customer-orders" variant="subtitle1" component="h3" sx={{ fontWeight: 600 }}>
          {fr.admin.customers.orders}
        </Typography>
        {!orders ? (
          <Skeleton variant="rounded" height={160} />
        ) : orders.content.length === 0 ? (
          <Typography color="text.secondary">{fr.admin.customers.noOrder}</Typography>
        ) : (
          <Box>
            {orders.content.map((order) => (
              <Accordion key={order.id} disableGutters variant="outlined" slotProps={{ transition: { unmountOnExit: true } }}>
                <AccordionSummary expandIcon={<ExpandMoreIcon />} aria-label={fr.admin.customers.orderLabel(order.id!)}>
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.5, width: '100%', pr: 1 }}>
                    <Typography sx={{ fontWeight: 600 }}>n° {order.id}</Typography>
                    <Typography color="text.secondary">{formatDate(order.createdAt)}</Typography>
                    <OrderStatusChip status={order.status ?? 'PENDING'} />
                    <Typography sx={{ ml: 'auto', fontWeight: 600 }}>{formatPrice(order.totalAmount)}</Typography>
                  </Box>
                </AccordionSummary>
                <AccordionDetails>
                  <OrderItems customerId={id} orderId={order.id!} />
                </AccordionDetails>
              </Accordion>
            ))}
          </Box>
        )}
      </Box>
    </Box>
  );
}

function OrderItems({ customerId, orderId }: { customerId: number; orderId: number }) {
  const { data: order } = useQuery(customerOrderQuery(customerId, orderId));
  if (!order) return <Skeleton variant="rounded" height={80} />;
  return (
    <Table size="small">
      <TableBody>
        {order.items?.map((item) => (
          <TableRow key={item.productId}>
            <TableCell component="th" scope="row">
              {item.productName}
            </TableCell>
            <TableCell align="right">× {item.quantity}</TableCell>
            <TableCell align="right">{formatPrice(item.subtotal)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

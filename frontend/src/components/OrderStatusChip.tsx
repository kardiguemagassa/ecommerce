import Box from '@mui/material/Box';
import type { OrderStatus } from '../api/orders';
import { fr } from '../i18n/fr';

const COLOURS: Record<OrderStatus, { bgcolor: string; color: string }> = {
  PENDING: { bgcolor: '#FFF1DC', color: '#8A4B08' },
  CONFIRMED: { bgcolor: '#E8F3EA', color: '#1F4D29' },
};

/** Status of an order, in words (never the colour alone); `long` in the tracking page, short in the list. */
export function OrderStatusChip({ status, long = false }: { status: OrderStatus; long?: boolean }) {
  return (
    <Box component="span" sx={{ ...COLOURS[status], display: 'inline-block', px: long ? 1.75 : 1.25, py: long ? 1 : 0.5, borderRadius: 4, fontWeight: 600, fontSize: long ? 14 : 13, whiteSpace: 'nowrap' }}>
      {long ? fr.order.status[status] : fr.order.shortStatus[status]}
    </Box>
  );
}

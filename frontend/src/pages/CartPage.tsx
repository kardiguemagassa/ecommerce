import CloseIcon from '@mui/icons-material/Close';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Typography from '@mui/material/Typography';
import { useIsMutating, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { cartMutationKey, cartQuery, useChangeQuantity, useClearCart, useRemoveFromCart, type CartItem } from '../api/cart';
import { usePlaceOrder } from '../api/orders';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Notice } from '../components/RequireRole';
import { fr } from '../i18n/fr';
import { formatPrice } from '../i18n/format';
import { ProductImage } from '../product/ProductImage';
import { QuantityStepper } from '../product/QuantityStepper';

/** The stock is checked by the API (409 shown as a toast): the cart does not know it, the stepper only caps typing. */
const MAX_QUANTITY = 99;

/** Cart page; the route loader has fetched the cart. */
export function CartPage() {
  const { data: cart } = useQuery(cartQuery());

  return (
    <Box sx={{ display: 'grid', gap: 3 }}>
      <Typography variant="h1" component="h1">
        {fr.pages.cart}
      </Typography>
      {!cart ? (
        <Skeleton variant="rounded" height={320} sx={{ borderRadius: 5 }} />
      ) : cart.items?.length ? (
        <FilledCart items={cart.items} totalQuantity={cart.totalQuantity ?? 0} total={cart.total ?? 0} />
      ) : (
        <Notice heading="h2" title={fr.cart.empty} hint={fr.cart.emptyHint} icon={<ShoppingCartOutlinedIcon color="primary" fontSize="large" />}>
          <Button component={RouterLink} to="/" variant="contained">
            {fr.cart.browse}
          </Button>
        </Notice>
      )}
    </Box>
  );
}

function FilledCart({ items, totalQuantity, total }: { items: CartItem[]; totalQuantity: number; total: number }) {
  const placeOrder = usePlaceOrder();
  const clear = useClearCart();
  const [confirming, setConfirming] = useState(false);
  // An order is placed from the cart as the API knows it: not while a change is on its way.
  const changing = useIsMutating({ mutationKey: cartMutationKey }) > 0;

  return (
    <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: 'minmax(0, 1fr) 340px' }, alignItems: 'start' }}>
      <Paper variant="outlined" component="ul" aria-label={fr.cart.lines} sx={{ borderRadius: 5, m: 0, p: 0, listStyle: 'none' }}>
        {items.map((item, index) => (
          <Box component="li" key={item.productId}>
            {index > 0 && <Divider />}
            <CartLineView item={item} />
          </Box>
        ))}
      </Paper>

      <Paper variant="outlined" component="section" aria-labelledby="cart-summary" sx={{ borderRadius: 5, p: 3, display: 'grid', gap: 1.5, position: { md: 'sticky' }, top: { md: 88 } }}>
        <Typography id="cart-summary" variant="h2" sx={{ fontSize: 20 }}>
          {fr.cart.summary}
        </Typography>
        <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: '1fr auto', rowGap: 1, '& dd': { m: 0, textAlign: 'right' } }}>
          <Box component="dt" sx={{ color: 'text.secondary' }}>{fr.cart.items(totalQuantity)}</Box>
          <Box component="dd">{formatPrice(total)}</Box>
          <Box component="dt" sx={{ color: 'text.secondary' }}>{fr.cart.delivery}</Box>
          <Box component="dd">{fr.cart.free}</Box>
          <Box component="dt" sx={{ pt: 1.5, borderTop: 1, borderColor: 'divider', fontWeight: 700 }}>{fr.cart.total}</Box>
          <Box component="dd" sx={{ pt: 1.5, borderTop: 1, borderColor: 'divider', fontFamily: 'Manrope, sans-serif', fontWeight: 800, fontSize: 22 }}>
            {formatPrice(total)}
          </Box>
        </Box>
        <Button variant="contained" size="large" loading={placeOrder.isPending} disabled={changing} onClick={() => placeOrder.mutate()} sx={{ height: 50 }}>
          {fr.cart.checkout}
        </Button>
        <Button color="error" disabled={placeOrder.isPending} loading={clear.isPending} onClick={() => setConfirming(true)}>
          {fr.cart.clear}
        </Button>
      </Paper>

      <ConfirmDialog
        open={confirming}
        title={fr.cart.clearTitle}
        hint={fr.cart.clearHint}
        confirmLabel={fr.cart.clear}
        onConfirm={() => clear.mutate()}
        onClose={() => setConfirming(false)}
      />
    </Box>
  );
}

function CartLineView({ item }: { item: CartItem }) {
  const changeQuantity = useChangeQuantity();
  const remove = useRemoveFromCart();
  const productId = item.productId!;
  const name = item.productName ?? '';
  const quantity = item.quantity ?? 1;

  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '72px 1fr auto', sm: '96px 1fr auto auto' }, gap: { xs: 1.5, sm: 2.5 }, alignItems: 'center', p: { xs: 2, sm: 2.5 } }}>
      <Box sx={{ borderRadius: 3, overflow: 'hidden', gridRow: { xs: 'span 2', sm: 'auto' } }}>
        <ProductImage product={{ id: productId, name, imageUrl: item.imageUrl }} decorative />
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Link component={RouterLink} to={`/produits/${productId}`} underline="hover" color="text.primary" sx={{ fontWeight: 600 }}>
          {name}
        </Link>
        <Typography variant="body2" color="text.secondary">
          {fr.cart.unitPrice(formatPrice(item.unitPrice))}
        </Typography>
      </Box>
      <IconButton
        aria-label={fr.cart.remove(name)}
        disabled={remove.isPending}
        onClick={() => remove.mutate({ productId, name, quantity })}
        sx={{ gridColumn: { sm: 4 }, gridRow: { sm: 1 }, justifySelf: 'end' }}
      >
        <CloseIcon />
      </IconButton>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, gridColumn: { xs: '2 / 4', sm: 3 }, gridRow: { sm: 1 } }}>
        <QuantityStepper
          label={fr.cart.quantityOf(name)}
          value={quantity}
          max={MAX_QUANTITY}
          onChange={(next) => changeQuantity.mutate({ productId, quantity: next })}
        />
        <Typography sx={{ fontWeight: 700, minWidth: 88, textAlign: 'right' }}>{formatPrice(item.subtotal)}</Typography>
      </Box>
    </Box>
  );
}

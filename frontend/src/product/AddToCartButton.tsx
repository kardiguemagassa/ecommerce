import AddIcon from '@mui/icons-material/Add';
import Button, { type ButtonProps } from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import { useAddToCart } from '../api/cart';
import type { Product } from '../api/catalog';
import { fr } from '../i18n/fr';

/** Adds the product (and the chosen quantity) to the cart; disabled when out of stock. */
export function AddToCartButton({
  product,
  quantity = 1,
  label = fr.cart.add,
  ...props
}: { product: Product; quantity?: number; label?: string } & Omit<ButtonProps, 'onClick'>) {
  const { add, pendingProductId } = useAddToCart();
  const pending = pendingProductId === product.id;
  const soldOut = (product.stock ?? 0) <= 0;

  return (
    <Button
      variant="contained"
      startIcon={pending ? <CircularProgress size={16} color="inherit" aria-hidden /> : <AddIcon />}
      disabled={soldOut || pending}
      aria-label={label === fr.cart.add ? fr.cart.addNamed(product.name ?? '') : undefined}
      onClick={() => add({ productId: product.id!, name: product.name ?? '', quantity })}
      {...props}
    >
      {label}
    </Button>
  );
}

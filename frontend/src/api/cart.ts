import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notify } from '../app/notifications';
import { fr } from '../i18n/fr';
import { api } from './client';
import { unwrap } from './problem';
import { hasRole, signIn, useSession } from './session';

export const cartKey = ['cart'] as const;

/** The cart of the signed-in customer; only fetched for customers (anonymous visitors have none). */
export function useCart(enabled: boolean) {
  return useQuery({
    queryKey: cartKey,
    queryFn: ({ signal }) => unwrap(api.GET('/api/me/cart', { signal })),
    enabled,
  });
}

export interface CartLine {
  productId: number;
  name: string;
  quantity: number;
}

/**
 * Adds a product to the cart. The API answers the whole cart: it replaces the cached one at once (badge of the header,
 * cart page), no extra request. Anonymous visitors are offered to sign in instead. Errors (409 insufficient stock...)
 * are toasted by the mutation cache, in French.
 */
export function useAddToCart() {
  const queryClient = useQueryClient();
  const { data: session } = useSession();
  const mutation = useMutation({
    mutationFn: (line: CartLine) =>
      unwrap(api.POST('/api/me/cart/items', { body: { productId: line.productId, quantity: line.quantity } })),
    onSuccess: (cart, line) => {
      queryClient.setQueryData(cartKey, cart);
      notify.success(fr.cart.added(line.name, line.quantity));
    },
  });

  const add = (line: CartLine) => {
    if (!hasRole(session, 'CUSTOMER')) {
      notify.info(fr.cart.signInToAdd, { label: fr.header.signIn, onClick: signIn });
      return;
    }
    mutation.mutate(line);
  };

  return { add, isPending: mutation.isPending, pendingProductId: mutation.isPending ? mutation.variables?.productId : undefined };
}

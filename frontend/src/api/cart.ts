import { queryOptions, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { notify } from '../app/notifications';
import { fr } from '../i18n/fr';
import { api } from './client';
import { unwrap } from './problem';
import type { components } from './schema';
import { hasRole, sessionQuery, signIn } from './session';

export type Cart = components['schemas']['CartResponse'];
export type CartItem = components['schemas']['CartItemResponse'];

export const cartKey = ['cart'] as const;

/** Key shared by every change of the cart: the page waits for them before ordering. */
export const cartMutationKey = ['cart', 'change'] as const;

export const cartQuery = () =>
  queryOptions({ queryKey: cartKey, queryFn: ({ signal }) => unwrap(api.GET('/api/me/cart', { signal })) });

/** The cart of the signed-in customer; only fetched for customers (anonymous visitors have none). */
export function useCart(enabled: boolean) {
  return useQuery({ ...cartQuery(), enabled });
}

export interface CartLine {
  productId: number;
  name: string;
  quantity: number;
}

/** The cart as the API answers it: it replaces the cached one at once (header badge, cart page), no extra request. */
const store = (queryClient: QueryClient, cart: Cart) => queryClient.setQueryData(cartKey, cart);

const addLine = (line: CartLine) =>
  unwrap(api.POST('/api/me/cart/items', { body: { productId: line.productId, quantity: line.quantity } }));

/**
 * Adds a product to the cart. Anonymous visitors are offered to sign in instead. Errors (409 insufficient stock...)
 * are toasted by the mutation cache, in French. The catalogue shows before the session is known: a click that early
 * waits for it, so that a signed-in customer is never asked to sign in.
 */
export function useAddToCart() {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationKey: cartMutationKey,
    mutationFn: addLine,
    onSuccess: (cart, line) => {
      store(queryClient, cart);
      notify.success(fr.cart.added(line.name, line.quantity));
    },
  });

  const add = (line: CartLine) => {
    queryClient.ensureQueryData(sessionQuery()).then(
      (session) => {
        if (hasRole(session, 'CUSTOMER')) mutation.mutate(line);
        else notify.info(fr.cart.signInToAdd, { label: fr.header.signIn, onClick: signIn });
      },
      () => undefined, // session unreachable: already toasted by the query cache
    );
  };

  return { add, isPending: mutation.isPending, pendingProductId: mutation.isPending ? mutation.variables?.productId : undefined };
}

/** The cart with one line at a new quantity, totals included: what the API will answer, shown before it does. */
export function withQuantity(cart: Cart, productId: number, quantity: number): Cart {
  const items = cart.items?.map((item) =>
    item.productId === productId ? { ...item, quantity, subtotal: round((item.unitPrice ?? 0) * quantity) } : item,
  );
  return {
    ...cart,
    items,
    totalQuantity: items?.reduce((sum, item) => sum + (item.quantity ?? 0), 0),
    total: round(items?.reduce((sum, item) => sum + (item.subtotal ?? 0), 0) ?? 0),
  };
}

const round = (amount: number) => Math.round(amount * 100) / 100;

/**
 * Changes the quantity of a line, optimistically: the page shows the new quantity and totals at once. Refused (409
 * insufficient stock...), the cart goes back to what it was and the mutation cache toasts why. Quick clicks send
 * several changes: only the answer to the last one is applied, so an older answer never overwrites a newer choice.
 */
export function useChangeQuantity() {
  const queryClient = useQueryClient();
  const isLastChange = () => queryClient.isMutating({ mutationKey: cartMutationKey }) === 1;

  return useMutation({
    mutationKey: cartMutationKey,
    mutationFn: ({ productId, quantity }: { productId: number; quantity: number }) =>
      unwrap(api.PUT('/api/me/cart/items/{productId}', { params: { path: { productId } }, body: { quantity } })),
    onMutate: async ({ productId, quantity }) => {
      await queryClient.cancelQueries({ queryKey: cartKey });
      const previous = queryClient.getQueryData<Cart>(cartKey);
      if (previous) store(queryClient, withQuantity(previous, productId, quantity));
      return { previous };
    },
    onSuccess: (cart) => {
      if (isLastChange()) store(queryClient, cart);
    },
    onError: (_error, _change, context) => {
      if (context?.previous && isLastChange()) store(queryClient, context.previous);
      void queryClient.invalidateQueries({ queryKey: cartKey }); // the server's cart settles any doubt
    },
  });
}

/** Removes a line; the toast offers to put it back (same quantity). */
export function useRemoveFromCart() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: cartMutationKey,
    mutationFn: (line: CartLine) =>
      unwrap(api.DELETE('/api/me/cart/items/{productId}', { params: { path: { productId: line.productId } } })),
    onSuccess: (cart, line) => {
      store(queryClient, cart);
      notify.success(fr.cart.removed(line.name), {
        label: fr.cart.undo,
        onClick: () => addLine(line).then((restored) => store(queryClient, restored), notify.error),
      });
    },
  });
}

export function useClearCart() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: cartMutationKey,
    mutationFn: () => unwrap(api.DELETE('/api/me/cart')),
    onSuccess: () => {
      emptyCart(queryClient);
      notify.success(fr.cart.cleared);
    },
  });
}

/** After an order or a clear: the cart is empty, no need to ask the API. */
export function emptyCart(queryClient: QueryClient): void {
  queryClient.setQueryData<Cart>(cartKey, (cart) => ({ ...cart, items: [], totalQuantity: 0, total: 0 }));
}

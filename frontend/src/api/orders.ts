import { keepPreviousData, queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { notify } from '../app/notifications';
import { fr } from '../i18n/fr';
import { emptyCart } from './cart';
import { api } from './client';
import { unwrap } from './problem';
import type { components } from './schema';

export type Order = components['schemas']['OrderResponse'];
export type OrderSummary = components['schemas']['OrderSummaryResponse'];
export type OrderStatus = NonNullable<Order['status']>;

export const ORDERS_PAGE_SIZE = 10;

/** While an order waits for its confirmation (stock reserved, e-mail sent), its page asks the API this often. */
export const ORDER_POLL_MS = 2_000;

export const orderKeys = {
  all: ['orders'] as const,
  lists: () => [...orderKeys.all, 'list'] as const,
  list: (page: number) => [...orderKeys.lists(), page] as const,
  detail: (id: number) => [...orderKeys.all, 'detail', id] as const,
};

/** `?page=2` → 2; anything else → 1. */
export function orderPageFrom(params: URLSearchParams): number {
  const page = Number(params.get('page'));
  return Number.isInteger(page) && page > 1 ? page : 1;
}

/** A page of the customer's orders, most recent first (pages from 1, as in the address bar). */
export const ordersQuery = (page: number) =>
  queryOptions({
    queryKey: orderKeys.list(page),
    queryFn: ({ signal }) =>
      unwrap(api.GET('/api/me/orders', { params: { query: { page: page - 1, size: ORDERS_PAGE_SIZE } }, signal })),
    placeholderData: keepPreviousData, // the table stays while the next page loads
  });

/**
 * One order. Polled every 2 s while PENDING, stopped once CONFIRMED (and while the tab is hidden). A missing order (or
 * another customer's) is a 404 shown in the page, not an error toast.
 */
export const orderQuery = (id: number) =>
  queryOptions({
    queryKey: orderKeys.detail(id),
    queryFn: ({ signal }) => unwrap(api.GET('/api/me/orders/{orderId}', { params: { path: { orderId: id } }, signal })),
    refetchInterval: (query) => (query.state.data?.status === 'PENDING' ? ORDER_POLL_MS : false),
    meta: { quietNotFound: true },
  });

/**
 * Turns the cart into an order, then shows its live tracking. The order is cached as answered (the tracking page
 * appears without waiting), the cart is empty, the list of orders is out of date.
 */
export function usePlaceOrder() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return useMutation({
    mutationFn: () => unwrap(api.POST('/api/me/orders')),
    onSuccess: (order) => {
      queryClient.setQueryData(orderKeys.detail(order.id!), order);
      emptyCart(queryClient);
      void queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
      notify.success(fr.order.placed(order.id!));
      void navigate(`/commandes/${order.id}`);
    },
  });
}

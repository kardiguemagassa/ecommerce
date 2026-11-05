import { useQuery } from '@tanstack/react-query';
import { api } from './client';
import { unwrap } from './problem';

export const cartKey = ['cart'] as const;

/** The cart of the signed-in customer; only fetched for customers (anonymous visitors have none). */
export function useCart(enabled: boolean) {
  return useQuery({
    queryKey: cartKey,
    queryFn: () => unwrap(api.GET('/api/me/cart')),
    enabled,
  });
}

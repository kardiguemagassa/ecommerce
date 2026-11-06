import { queryOptions } from '@tanstack/react-query';
import { api } from './client';
import { unwrap } from './problem';
import type { components } from './schema';

export type Product = components['schemas']['ProductResponse'];
export type Category = components['schemas']['CategoryResponse'];
export type BestSeller = components['schemas']['TopSellingProductResponse'];
export type ProductPage = components['schemas']['PagedModelProductResponse'];

/** Sort orders offered to the user, by their (French) URL value, mapped to the API's `sort` parameter. */
export const SORTS = {
  nom: 'name,asc',
  'prix-croissant': 'price,asc',
  'prix-decroissant': 'price,desc',
  nouveautes: 'id,desc',
} as const;
export type Sort = keyof typeof SORTS;
export const DEFAULT_SORT: Sort = 'nom';

export const PAGE_SIZE = 12;

/** A catalogue search, as the user sees it (pages from 1). */
export interface ProductFilters {
  q: string;
  categoryId?: number;
  sort: Sort;
  page: number;
}

/**
 * Query keys of the catalogue, from the most general to the most specific: invalidating `all` refreshes every list
 * and every product, `lists()` the searches only.
 */
export const productKeys = {
  all: ['products'] as const,
  lists: () => [...productKeys.all, 'list'] as const,
  list: (filters: ProductFilters) => [...productKeys.lists(), filters] as const,
  detail: (id: number) => [...productKeys.all, 'detail', id] as const,
  bestSellers: (limit: number) => [...productKeys.all, 'best-sellers', limit] as const,
};

export const categoryKeys = { all: ['categories'] as const };

export const productListQuery = (filters: ProductFilters) =>
  queryOptions({
    queryKey: productKeys.list(filters),
    queryFn: ({ signal }) =>
      unwrap(
        api.GET('/api/products', {
          params: {
            query: {
              name: filters.q || undefined,
              categoryId: filters.categoryId,
              sort: [SORTS[filters.sort]],
              page: filters.page - 1,
              size: PAGE_SIZE,
            },
          },
          signal,
        }),
      ),
    staleTime: 30_000,
  });

export const productQuery = (id: number) =>
  queryOptions({
    queryKey: productKeys.detail(id),
    queryFn: ({ signal }) => unwrap(api.GET('/api/products/{id}', { params: { path: { id } }, signal })),
    staleTime: 30_000,
    // A missing product is shown by the page itself ("n'existe plus"), not as an error toast.
    meta: { quietNotFound: true },
  });

export const categoriesQuery = () =>
  queryOptions({
    queryKey: categoryKeys.all,
    queryFn: ({ signal }) => unwrap(api.GET('/api/categories', { signal })),
    staleTime: 5 * 60_000,
  });

export const bestSellersQuery = (limit = 4) =>
  queryOptions({
    queryKey: productKeys.bestSellers(limit),
    queryFn: ({ signal }) => unwrap(api.GET('/api/products/top-selling', { params: { query: { limit } }, signal })),
    staleTime: 5 * 60_000, // the API caches the ranking for 5 minutes too
  });

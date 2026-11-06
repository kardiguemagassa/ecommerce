import { keepPreviousData, queryOptions, useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { notify } from '../app/notifications';
import { fr } from '../i18n/fr';
import { categoryKeys, productKeys, type Category, type Product } from './catalog';
import { api } from './client';
import { imageUpload } from './images';
import { unwrap } from './problem';
import type { components } from './schema';

export type ProductInput = components['schemas']['ProductRequest'];
export type Customer = components['schemas']['CustomerResponse'];

export const ADMIN_PAGE_SIZE = 20;
export const BEST_SELLERS_LIMIT = 10;

/** `?page=2` → 2; anything else → 1. */
export function pageParam(params: URLSearchParams): number {
  const page = Number(params.get('page'));
  return Number.isInteger(page) && page > 1 ? page : 1;
}

/** A search of an admin table, as in the address bar (pages from 1). */
export interface AdminSearch {
  q: string;
  page: number;
}

export const adminSearchFrom = (params: URLSearchParams): AdminSearch => ({
  q: params.get('q')?.trim() ?? '',
  page: pageParam(params),
});

// Products ---------------------------------------------------------------------------------------------------------

/** Products of the admin table: by name, 20 a page. Same cache family as the catalogue: one invalidation refreshes both. */
export const adminProductsQuery = ({ q, page }: AdminSearch) =>
  queryOptions({
    queryKey: [...productKeys.lists(), 'admin', { q, page }] as const,
    queryFn: ({ signal }) =>
      unwrap(
        api.GET('/api/products', {
          params: { query: { name: q || undefined, sort: ['name,asc'], page: page - 1, size: ADMIN_PAGE_SIZE } },
          signal,
        }),
      ),
    placeholderData: keepPreviousData,
  });

/**
 * After a change of the catalogue: the product as answered (its page shows it at once), every list and the best sellers
 * (names, prices, categories) are out of date, for the admin tables and the shop alike.
 */
function catalogueChanged(queryClient: QueryClient, product?: Product) {
  if (product?.id) queryClient.setQueryData(productKeys.detail(product.id), product);
  void queryClient.invalidateQueries({ queryKey: productKeys.lists() });
  void queryClient.invalidateQueries({ queryKey: [...productKeys.all, 'best-sellers'] });
}

/** Creates the product (no id) or updates it. */
export function useSaveProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: number; input: ProductInput }) =>
      id
        ? unwrap(api.PUT('/api/products/{id}', { params: { path: { id } }, body: input }))
        : unwrap(api.POST('/api/products', { body: input })),
    onSuccess: (product, { id }) => {
      catalogueChanged(queryClient, product);
      notify.success(id ? fr.admin.products.saved(product.name ?? '') : fr.admin.products.created(product.name ?? ''));
    },
  });
}

/** A product already ordered cannot be deleted (409): the mutation cache toasts why. */
export function useDeleteProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (product: Product) => unwrap(api.DELETE('/api/products/{id}', { params: { path: { id: product.id! } } })),
    onSuccess: (_nothing, product) => {
      queryClient.removeQueries({ queryKey: productKeys.detail(product.id!) });
      catalogueChanged(queryClient);
      notify.success(fr.admin.products.deleted(product.name ?? ''));
    },
  });
}

export function useReplaceProductImage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: number; file: File }) =>
      unwrap(api.PUT('/api/products/{id}/image', { params: { path: { id } }, ...imageUpload(file) })),
    onSuccess: (product) => {
      catalogueChanged(queryClient, product);
      notify.success(fr.admin.image.saved);
    },
  });
}

export function useDeleteProductImage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => unwrap(api.DELETE('/api/products/{id}/image', { params: { path: { id } } })),
    onSuccess: (_nothing, id) => {
      queryClient.setQueryData<Product>(productKeys.detail(id), (product) => product && { ...product, imageUrl: undefined });
      catalogueChanged(queryClient);
      notify.success(fr.admin.image.removed);
    },
  });
}

// Categories -------------------------------------------------------------------------------------------------------

/** A category changed: the list, and the products that show its name. */
function categoriesChanged(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: categoryKeys.all });
  void queryClient.invalidateQueries({ queryKey: productKeys.all });
}

export function useCreateCategory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => unwrap(api.POST('/api/categories', { body: { name } })),
    onSuccess: (category) => {
      categoriesChanged(queryClient);
      notify.success(fr.admin.categories.created(category.name ?? ''));
    },
  });
}

export function useRenameCategory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, name }: { id: number; name: string }) =>
      unwrap(api.PUT('/api/categories/{id}', { params: { path: { id } }, body: { name } })),
    onSuccess: (category) => {
      categoriesChanged(queryClient);
      notify.success(fr.admin.categories.renamed(category.name ?? ''));
    },
  });
}

/** A category that still holds products cannot be deleted (409). */
export function useDeleteCategory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (category: Category) =>
      unwrap(api.DELETE('/api/categories/{id}', { params: { path: { id: category.id! } } })),
    onSuccess: (_nothing, category) => {
      categoriesChanged(queryClient);
      notify.success(fr.admin.categories.deleted(category.name ?? ''));
    },
  });
}

// Customers (support) ----------------------------------------------------------------------------------------------

export const customerKeys = {
  all: ['customers'] as const,
  search: (search: AdminSearch) => [...customerKeys.all, 'search', search] as const,
  detail: (id: number) => [...customerKeys.all, 'detail', id] as const,
  orders: (id: number) => [...customerKeys.detail(id), 'orders'] as const,
  order: (id: number, orderId: number) => [...customerKeys.detail(id), 'order', orderId] as const,
};

export const customersQuery = (search: AdminSearch) =>
  queryOptions({
    queryKey: customerKeys.search(search),
    queryFn: ({ signal }) =>
      unwrap(
        api.GET('/api/admin/customers', {
          params: { query: { q: search.q || undefined, page: search.page - 1, size: ADMIN_PAGE_SIZE } },
          signal,
        }),
      ),
    placeholderData: keepPreviousData,
  });

export const customerQuery = (id: number) =>
  queryOptions({
    queryKey: customerKeys.detail(id),
    queryFn: ({ signal }) => unwrap(api.GET('/api/admin/customers/{id}', { params: { path: { id } }, signal })),
  });

/** The latest orders of a customer (support looks at the recent ones). */
export const customerOrdersQuery = (id: number) =>
  queryOptions({
    queryKey: customerKeys.orders(id),
    queryFn: ({ signal }) =>
      unwrap(api.GET('/api/admin/customers/{id}/orders', { params: { path: { id }, query: { size: ADMIN_PAGE_SIZE } }, signal })),
  });

export const customerOrderQuery = (id: number, orderId: number) =>
  queryOptions({
    queryKey: customerKeys.order(id, orderId),
    queryFn: ({ signal }) =>
      unwrap(api.GET('/api/admin/customers/{id}/orders/{orderId}', { params: { path: { id, orderId } }, signal })),
  });

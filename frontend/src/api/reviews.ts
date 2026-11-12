import { infiniteQueryOptions, queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';
import { notify } from '../app/notifications';
import { fr } from '../i18n/fr';
import { productKeys } from './catalog';
import { api } from './client';
import { unwrap } from './problem';
import type { components } from './schema';

export type Review = components['schemas']['ReviewResponse'];
export type ReviewInput = components['schemas']['ReviewRequest'];
export type Rating = components['schemas']['ProductRatingResponse'];

export const REVIEWS_PAGE_SIZE = 10;

export const reviewKeys = {
  all: (productId: number) => ['reviews', productId] as const,
  list: (productId: number) => [...reviewKeys.all(productId), 'list'] as const,
  summary: (productId: number) => [...reviewKeys.all(productId), 'summary'] as const,
  mine: (productId: number) => [...reviewKeys.all(productId), 'mine'] as const,
};

/** The visible reviews of a product, newest first, 10 a page: "Voir plus d'avis" loads the next page. */
export const reviewsQuery = (productId: number) =>
  infiniteQueryOptions({
    queryKey: reviewKeys.list(productId),
    queryFn: ({ pageParam, signal }) =>
      unwrap(
        api.GET('/api/products/{id}/reviews', {
          params: { path: { id: productId }, query: { page: pageParam, size: REVIEWS_PAGE_SIZE } },
          signal,
        }),
      ),
    initialPageParam: 0,
    getNextPageParam: (last) => {
      const next = (last.page?.number ?? 0) + 1;
      return next < (last.page?.totalPages ?? 0) ? next : undefined;
    },
  });

export const reviewSummaryQuery = (productId: number) =>
  queryOptions({
    queryKey: reviewKeys.summary(productId),
    queryFn: ({ signal }) => unwrap(api.GET('/api/products/{id}/reviews/summary', { params: { path: { id: productId } }, signal })),
  });

/** Whether the signed-in customer may review the product (a confirmed order of it), and their review. */
export const myReviewQuery = (productId: number) =>
  queryOptions({
    queryKey: reviewKeys.mine(productId),
    queryFn: ({ signal }) => unwrap(api.GET('/api/me/products/{id}/review', { params: { path: { id: productId } }, signal })),
  });

/** A review changed: its list, the summary, the product (its rating) and the catalogue cards (their stars). */
function reviewsChanged(queryClient: ReturnType<typeof useQueryClient>, productId: number) {
  void queryClient.invalidateQueries({ queryKey: reviewKeys.all(productId) });
  void queryClient.invalidateQueries({ queryKey: productKeys.detail(productId) });
  void queryClient.invalidateQueries({ queryKey: productKeys.lists() });
}

export function useSaveReview(productId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ input }: { input: ReviewInput; editing: boolean }) =>
      unwrap(api.PUT('/api/me/products/{id}/review', { params: { path: { id: productId } }, body: input })),
    onSuccess: (_review, { editing }) => {
      reviewsChanged(queryClient, productId);
      notify.success(editing ? fr.reviews.updated : fr.reviews.published);
    },
  });
}

export function useDeleteReview(productId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => unwrap(api.DELETE('/api/me/products/{id}/review', { params: { path: { id: productId } } })),
    onSuccess: () => {
      reviewsChanged(queryClient, productId);
      notify.success(fr.reviews.deleted);
    },
  });
}

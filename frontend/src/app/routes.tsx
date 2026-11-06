import type { QueryClient } from '@tanstack/react-query';
import { data, type LoaderFunctionArgs, type RouteObject } from 'react-router';
import { bestSellersQuery, categoriesQuery, productListQuery, productQuery, type Product } from '../api/catalog';
import { ApiError } from '../api/problem';
import { filtersFrom } from '../catalog/filters';
import { Layout, type RouteHandle } from '../components/Layout';
import { PageFallback } from '../components/PageFallback';
import { RequireRole } from '../components/RequireRole';
import { RouteError } from '../components/RouteError';
import { fr } from '../i18n/fr';

const titled = (title: RouteHandle['title']): RouteHandle => ({ title });

/** What a 404 thrown by a loader is about: the error boundary words it accordingly. */
export interface NotFound {
  kind: 'product';
}

/**
 * Catalogue: the page of products and the categories are fetched while the page module loads (render as you fetch),
 * the best sellers in the background. Cached results are shown at once and refreshed if stale.
 */
const catalogLoader =
  (queryClient: QueryClient) =>
  async ({ request }: LoaderFunctionArgs) => {
    const filters = filtersFrom(new URL(request.url).searchParams);
    void queryClient.prefetchQuery(bestSellersQuery());
    await Promise.all([
      queryClient.ensureQueryData({ ...productListQuery(filters), revalidateIfStale: true }),
      queryClient.ensureQueryData({ ...categoriesQuery(), revalidateIfStale: true }),
    ]);
    return null;
  };

/** Product page: an unknown or malformed id is a 404 of the route (shown in the page, header kept). */
const productLoader =
  (queryClient: QueryClient) =>
  async ({ params }: LoaderFunctionArgs): Promise<Product> => {
    const id = Number(params.id);
    if (!Number.isInteger(id) || id < 1) throw data<NotFound>({ kind: 'product' }, { status: 404 });
    try {
      return await queryClient.ensureQueryData({ ...productQuery(id), revalidateIfStale: true });
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) throw data<NotFound>({ kind: 'product' }, { status: 404 });
      throw error;
    }
  };

/**
 * Route map of the shop, in French as the user sees it. Every page is its own chunk, fetched on the first visit
 * (route `lazy`): the catalogue does not download the admin space. The layout, header and guards are loaded once.
 * Loaders get the data through the query cache, shared with the components.
 */
export const createRoutes = (queryClient: QueryClient): RouteObject[] => [
  {
    element: <Layout />,
    hydrateFallbackElement: <PageFallback />,
    errorElement: <RouteError />,
    children: [
      {
        // Errors of a page (or of its chunk) stay inside the page area: header and navigation keep working.
        errorElement: <RouteError />,
        children: [
          {
            index: true,
            handle: titled(fr.pages.catalog),
            loader: catalogLoader(queryClient),
            lazy: () => import('../pages/CatalogPage').then((module) => ({ Component: module.CatalogPage })),
          },
          {
            path: 'produits/:id',
            handle: titled((product) => (product as Product | undefined)?.name ?? fr.pages.product),
            loader: productLoader(queryClient),
            lazy: () => import('../pages/ProductPage').then((module) => ({ Component: module.ProductPage })),
          },
          {
            element: <RequireRole role="CUSTOMER" />,
            children: [
              {
                path: 'panier',
                handle: titled(fr.pages.cart),
                lazy: () => import('../pages/CartPage').then((module) => ({ Component: module.CartPage })),
              },
              {
                path: 'commandes',
                handle: titled(fr.pages.orders),
                lazy: () => import('../pages/OrdersPage').then((module) => ({ Component: module.OrdersPage })),
              },
              {
                path: 'commandes/:id',
                handle: titled(fr.pages.order),
                lazy: () => import('../pages/OrderPage').then((module) => ({ Component: module.OrderPage })),
              },
              {
                path: 'profil',
                handle: titled(fr.pages.profile),
                lazy: () => import('../pages/ProfilePage').then((module) => ({ Component: module.ProfilePage })),
              },
            ],
          },
          {
            element: <RequireRole role="ADMIN" />,
            children: [
              {
                path: 'admin/*',
                handle: titled(fr.pages.admin),
                lazy: () => import('../pages/AdminPage').then((module) => ({ Component: module.AdminPage })),
              },
            ],
          },
          {
            path: '*',
            handle: titled(fr.pages.notFound),
            lazy: () => import('../pages/NotFoundPage').then((module) => ({ Component: module.NotFoundPage })),
          },
        ],
      },
    ],
  },
];

import type { QueryClient } from '@tanstack/react-query';
import { data, Navigate, type LoaderFunctionArgs, type RouteObject } from 'react-router';
import { BEST_SELLERS_LIMIT, adminProductsQuery, adminSearchFrom, customersQuery } from '../api/admin';
import { cartQuery } from '../api/cart';
import { bestSellersQuery, categoriesQuery, productListQuery, productQuery, type Product } from '../api/catalog';
import { orderPageFrom, orderQuery, ordersQuery, type Order } from '../api/orders';
import { ApiError } from '../api/problem';
import { hasRole, sessionQuery, type Role } from '../api/session';
import { filtersFrom } from '../catalog/filters';
import { Layout, type RouteHandle } from '../components/Layout';
import { PageFallback } from '../components/PageFallback';
import { RequireRole } from '../components/RequireRole';
import { RouteError } from '../components/RouteError';
import { fr } from '../i18n/fr';

const titled = (title: RouteHandle['title']): RouteHandle => ({ title });

/** What a 404 thrown by a loader is about: the error boundary words it accordingly. */
export interface NotFound {
  kind: 'product' | 'order';
}

const notFound = (kind: NotFound['kind']) => data<NotFound>({ kind }, { status: 404 });

/** A positive integer from the address, or a 404 of the route. */
function idParam(value: string | undefined, kind: NotFound['kind']): number {
  const id = Number(value);
  if (!Number.isInteger(id) || id < 1) throw notFound(kind);
  return id;
}

/** Gets a resource through the query cache; its 404 becomes a 404 of the route (shown in the page, header kept). */
async function orNotFound<T>(load: Promise<T>, kind: NotFound['kind']): Promise<T> {
  try {
    return await load;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) throw notFound(kind);
    throw error;
  }
}

/**
 * Loader of a guarded page: fetches its data only when the session has the role. Otherwise it returns nothing and the
 * route guard asks to sign in (or says the space is reserved), without a failed call nor an error toast.
 */
const forRole =
  <T,>(role: Role, queryClient: QueryClient, load: (args: LoaderFunctionArgs) => Promise<T>) =>
  async (args: LoaderFunctionArgs): Promise<T | null> => {
    const session = await queryClient.ensureQueryData(sessionQuery());
    return hasRole(session, role) ? load(args) : null;
  };

const forCustomer = <T,>(queryClient: QueryClient, load: (args: LoaderFunctionArgs) => Promise<T>) =>
  forRole('CUSTOMER', queryClient, load);

const forAdmin = <T,>(queryClient: QueryClient, load: (args: LoaderFunctionArgs) => Promise<T>) =>
  forRole('ADMIN', queryClient, load);

const searchOf = (request: Request) => adminSearchFrom(new URL(request.url).searchParams);

/** Admin products: the page of the table, and the categories the form offers. */
const adminProductsLoader = (queryClient: QueryClient) =>
  forAdmin(queryClient, async ({ request }) => {
    await Promise.all([
      queryClient.ensureQueryData({ ...adminProductsQuery(searchOf(request)), revalidateIfStale: true }),
      queryClient.ensureQueryData({ ...categoriesQuery(), revalidateIfStale: true }),
    ]);
    return null;
  });

const adminCategoriesLoader = (queryClient: QueryClient) =>
  forAdmin(queryClient, () => queryClient.ensureQueryData({ ...categoriesQuery(), revalidateIfStale: true }));

const adminBestSellersLoader = (queryClient: QueryClient) =>
  forAdmin(queryClient, () => queryClient.ensureQueryData({ ...bestSellersQuery(BEST_SELLERS_LIMIT), revalidateIfStale: true }));

const adminCustomersLoader = (queryClient: QueryClient) =>
  forAdmin(queryClient, ({ request }) => queryClient.ensureQueryData({ ...customersQuery(searchOf(request)), revalidateIfStale: true }));

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
    const id = idParam(params.id, 'product');
    return orNotFound(queryClient.ensureQueryData({ ...productQuery(id), revalidateIfStale: true }), 'product');
  };

const cartLoader = (queryClient: QueryClient) =>
  forCustomer(queryClient, () => queryClient.ensureQueryData({ ...cartQuery(), revalidateIfStale: true }));

const ordersLoader = (queryClient: QueryClient) =>
  forCustomer(queryClient, ({ request }) =>
    queryClient.ensureQueryData({ ...ordersQuery(orderPageFrom(new URL(request.url).searchParams)), revalidateIfStale: true }),
  );

/** Order tracking: an unknown order, or another customer's (the API answers 404 alike), is a 404 of the route. */
const orderLoader = (queryClient: QueryClient) =>
  forCustomer(queryClient, ({ params }) => {
    const id = idParam(params.id, 'order');
    return orNotFound(queryClient.ensureQueryData({ ...orderQuery(id), revalidateIfStale: true }), 'order');
  });

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
                loader: cartLoader(queryClient),
                lazy: () => import('../pages/CartPage').then((module) => ({ Component: module.CartPage })),
              },
              {
                path: 'commandes',
                handle: titled(fr.pages.orders),
                loader: ordersLoader(queryClient),
                lazy: () => import('../pages/OrdersPage').then((module) => ({ Component: module.OrdersPage })),
              },
              {
                path: 'commandes/:id',
                handle: titled((order) => ((order as Order | null)?.id ? fr.order.title((order as Order).id!) : fr.pages.order)),
                loader: orderLoader(queryClient),
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
                path: 'admin',
                handle: titled(fr.pages.admin),
                lazy: () => import('../pages/admin/AdminLayout').then((module) => ({ Component: module.AdminLayout })),
                children: [
                  // The admin space opens on the products (replace: the back button does not land on /admin again).
                  { index: true, element: <Navigate to="/admin/produits" replace /> },
                  {
                    path: 'produits',
                    handle: titled(fr.admin.titles.products),
                    loader: adminProductsLoader(queryClient),
                    lazy: () => import('../pages/admin/AdminProductsPage').then((module) => ({ Component: module.AdminProductsPage })),
                  },
                  {
                    path: 'categories',
                    handle: titled(fr.admin.titles.categories),
                    loader: adminCategoriesLoader(queryClient),
                    lazy: () => import('../pages/admin/AdminCategoriesPage').then((module) => ({ Component: module.AdminCategoriesPage })),
                  },
                  {
                    path: 'ventes',
                    handle: titled(fr.admin.titles.bestSellers),
                    loader: adminBestSellersLoader(queryClient),
                    lazy: () => import('../pages/admin/AdminBestSellersPage').then((module) => ({ Component: module.AdminBestSellersPage })),
                  },
                  {
                    path: 'clients',
                    handle: titled(fr.admin.titles.customers),
                    loader: adminCustomersLoader(queryClient),
                    lazy: () => import('../pages/admin/AdminCustomersPage').then((module) => ({ Component: module.AdminCustomersPage })),
                  },
                ],
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

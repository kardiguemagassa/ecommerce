import type { RouteObject } from 'react-router';
import { Layout, type RouteHandle } from '../components/Layout';
import { PageFallback } from '../components/PageFallback';
import { RequireRole } from '../components/RequireRole';
import { RouteError } from '../components/RouteError';
import { fr } from '../i18n/fr';

const titled = (title: string): RouteHandle => ({ title });

/**
 * Route map of the shop, in French as the user sees it. Every page is its own chunk, fetched on the first visit
 * (route `lazy`): the catalogue does not download the admin space. The layout, header and guards are loaded once.
 */
export const routes: RouteObject[] = [
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
            lazy: () => import('../pages/CatalogPage').then((module) => ({ Component: module.CatalogPage })),
          },
          {
            path: 'produits/:id',
            handle: titled(fr.pages.product),
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

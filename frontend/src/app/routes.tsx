import type { RouteObject } from 'react-router';
import { Layout } from '../components/Layout';
import { RequireRole } from '../components/RequireRole';
import { NotFoundPage } from '../pages/NotFoundPage';
import { PagePlaceholder } from '../pages/PagePlaceholder';
import { fr } from '../i18n/fr';

/** Route map of the shop, in French as the user sees it. Pages are filled in by the next pull requests. */
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { index: true, element: <PagePlaceholder title={fr.pages.catalog} /> },
      { path: 'produits/:id', element: <PagePlaceholder title={fr.pages.product} /> },
      {
        path: 'panier',
        element: (
          <RequireRole role="CUSTOMER">
            <PagePlaceholder title={fr.pages.cart} />
          </RequireRole>
        ),
      },
      {
        path: 'commandes',
        element: (
          <RequireRole role="CUSTOMER">
            <PagePlaceholder title={fr.pages.orders} />
          </RequireRole>
        ),
      },
      {
        path: 'commandes/:id',
        element: (
          <RequireRole role="CUSTOMER">
            <PagePlaceholder title={fr.pages.order} />
          </RequireRole>
        ),
      },
      {
        path: 'profil',
        element: (
          <RequireRole role="CUSTOMER">
            <PagePlaceholder title={fr.pages.profile} />
          </RequireRole>
        ),
      },
      {
        path: 'admin/*',
        element: (
          <RequireRole role="ADMIN">
            <PagePlaceholder title={fr.pages.admin} />
          </RequireRole>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

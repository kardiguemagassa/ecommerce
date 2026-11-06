import { render } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { createRoutes } from '../app/routes';

/** The whole application at a URL, against the mocked API. */
export function renderApp(path = '/') {
  const queryClient = createQueryClient();
  const router = createMemoryRouter(createRoutes(queryClient), { initialEntries: [path] });
  return { router, queryClient, ...render(<App router={router} queryClient={queryClient} />) };
}

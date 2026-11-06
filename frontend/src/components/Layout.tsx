import Box from '@mui/material/Box';
import LinearProgress from '@mui/material/LinearProgress';
import Link from '@mui/material/Link';
import { useEffect, useRef } from 'react';
import { Outlet, ScrollRestoration, useLocation, useMatches, useNavigation } from 'react-router';
import { fr } from '../i18n/fr';
import { Header } from './Header';
import { Toaster } from './Toaster';

/** What a route declares for the layout (route `handle`). */
export interface RouteHandle {
  /** Title of the page for the browser tab: fixed, or from the data of the route loader (a product's name). */
  title: string | ((loaderData: unknown) => string);
}

export function Layout() {
  const navigation = useNavigation();
  const loading = navigation.state !== 'idle';
  const main = useMainFocusOnNavigation();
  useDocumentTitle();

  return (
    <>
      <Link
        href="#contenu"
        sx={{ position: 'absolute', left: 8, top: -48, zIndex: 'tooltip', bgcolor: 'background.paper', p: 1, borderRadius: 1, '&:focus': { top: 8 } }}
      >
        {fr.pages.skipToContent}
      </Link>
      {/* A lazy page is being fetched: visible, without blanking the current page. */}
      {loading && (
        <LinearProgress aria-label={fr.pages.loading} sx={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 'appBar', height: 3 }} />
      )}
      <Header />
      <Box
        component="main"
        id="contenu"
        ref={main}
        tabIndex={-1}
        aria-busy={loading}
        sx={{ maxWidth: 1240, mx: 'auto', px: { xs: 2, sm: 3 }, py: 4, outline: 'none' }}
      >
        <Outlet />
      </Box>
      <Toaster />
      <ScrollRestoration />
    </>
  );
}

/** Browser tab: "<page> · Comptoir", from the deepest route that declares a title. */
function useDocumentTitle() {
  const matches = useMatches();
  const title = [...matches]
    .reverse()
    .map((match) => {
      const handle = match.handle as RouteHandle | undefined;
      return typeof handle?.title === 'function' ? handle.title(match.loaderData) : handle?.title;
    })
    .find(Boolean);
  useEffect(() => {
    document.title = title ? `${title} · ${fr.appName}` : fr.appName;
  }, [title]);
}

/**
 * After a client-side navigation, keyboard and screen-reader users start from the new content, as after a full page
 * load, not from the link they activated. Not on the first render: the page has just loaded.
 */
function useMainFocusOnNavigation() {
  const { pathname } = useLocation();
  const main = useRef<HTMLElement>(null);
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    main.current?.focus({ preventScroll: true });
  }, [pathname]);
  return main;
}

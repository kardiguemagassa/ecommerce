import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { Outlet } from 'react-router';
import { hasRole, signIn, useSession, type Role } from '../api/session';
import { fr } from '../i18n/fr';

/**
 * Layout route around the pages of a role: renders them only for users with the role. Convenience only: the API
 * checks the role on every call, this guard spares the user a page full of errors.
 */
export function RequireRole({ role }: { role: Role }) {
  const { data: session, isPending } = useSession();

  if (isPending) {
    return <CircularProgress aria-label={fr.access.loading} />;
  }
  if (!session?.authenticated) {
    return (
      <Notice title={fr.access.signInRequired} hint={fr.access.signInHint}>
        <Button variant="contained" onClick={signIn}>
          {fr.header.signIn}
        </Button>
      </Notice>
    );
  }
  if (!hasRole(session, role)) {
    return <Notice title={fr.access.denied} hint={fr.access.deniedHint} />;
  }
  return <Outlet />;
}

/** A centred message with its way out: sign-in required, page not found, empty cart... `heading`: h2 inside a titled page. */
export function Notice({ title, hint, icon, heading = 'h1', children }: { title: string; hint: string; icon?: ReactNode; heading?: 'h1' | 'h2'; children?: ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 4, maxWidth: 520, mx: 'auto', textAlign: 'center', display: 'grid', gap: 1.5, justifyItems: 'center' }}>
      {icon ?? <LockOutlinedIcon color="primary" fontSize="large" />}
      <Typography variant="h1" component={heading} sx={{ fontSize: '1.5rem' }}>
        {title}
      </Typography>
      <Typography color="text.secondary">{hint}</Typography>
      {children}
    </Paper>
  );
}

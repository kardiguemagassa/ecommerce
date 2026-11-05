import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Snackbar from '@mui/material/Snackbar';
import { useState } from 'react';
import { dismissNotification, useNotification } from '../app/notifications';
import { fr } from '../i18n/fr';

/**
 * The last error, in French, with the trace id of the request: quoted to the support, it leads straight to the
 * request, its SQL queries and its logs in Grafana.
 */
export function ErrorSnackbar() {
  const notification = useNotification();
  const [copied, setCopied] = useState<number | null>(null);

  const copy = async (traceId: string, id: number) => {
    await navigator.clipboard.writeText(traceId);
    setCopied(id);
  };

  return (
    <Snackbar
      key={notification?.id}
      open={notification !== null}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      onClose={(_, reason) => reason !== 'clickaway' && dismissNotification()}
      autoHideDuration={notification?.traceId ? null : 8000}
    >
      <Alert severity="error" variant="filled" onClose={dismissNotification} slotProps={{ closeButton: { 'aria-label': fr.errors.close } }} sx={{ maxWidth: 480 }}>
        {notification?.message}
        {notification?.traceId && (
          <Box sx={{ mt: 0.75, display: 'flex', alignItems: 'center', gap: 1, fontSize: 12, flexWrap: 'wrap' }}>
            {fr.errors.supportReference} :
            <Box component="code" sx={{ fontFamily: 'ui-monospace, monospace', bgcolor: 'rgba(255,255,255,0.15)', px: 0.75, borderRadius: 0.5 }}>
              {notification.traceId}
            </Box>
            <Button size="small" color="inherit" onClick={() => void copy(notification.traceId!, notification.id)} sx={{ textDecoration: 'underline', minWidth: 0, p: 0 }}>
              {copied === notification.id ? fr.errors.copied : fr.errors.copy}
            </Button>
          </Box>
        )}
      </Alert>
    </Snackbar>
  );
}

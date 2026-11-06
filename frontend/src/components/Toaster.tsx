import CloseIcon from '@mui/icons-material/Close';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Snackbar, { type SnackbarCloseReason } from '@mui/material/Snackbar';
import { useState } from 'react';
import { dismissToast, durationOf, useCurrentToast, type Toast } from '../app/notifications';
import { fr } from '../i18n/fr';

/**
 * The toast at the head of the queue. Closing it plays the exit transition first, then the next one enters: two
 * messages never overlap. Errors are announced at once by screen readers (role alert), the others politely (status).
 */
export function Toaster() {
  const toast = useCurrentToast();
  // Keyed by toast: a new toast starts open, with its own copy state.
  return toast ? <ToastView key={toast.id} toast={toast} /> : null;
}

function ToastView({ toast }: { toast: Toast }) {
  const [open, setOpen] = useState(true);
  const [copied, setCopied] = useState(false);

  const close = (_: unknown, reason?: SnackbarCloseReason) => {
    if (reason !== 'clickaway') setOpen(false); // a click elsewhere on the page does not discard a message
  };
  const copy = async (traceId: string) => {
    await navigator.clipboard.writeText(traceId);
    setCopied(true);
  };

  return (
    <Snackbar
      open={open}
      onClose={close}
      autoHideDuration={durationOf(toast)}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      slotProps={{ transition: { onExited: () => dismissToast(toast.id) } }}
    >
      <Alert
        severity={toast.severity}
        variant="filled"
        role={toast.severity === 'error' ? 'alert' : 'status'}
        onClose={() => setOpen(false)}
        action={
          toast.action ? (
            <>
              <Button
                color="inherit"
                size="small"
                onClick={() => {
                  setOpen(false);
                  toast.action!.onClick();
                }}
                sx={{ fontWeight: 700, textDecoration: 'underline', alignSelf: 'center' }}
              >
                {toast.action.label}
              </Button>
              <IconButton aria-label={fr.errors.close} color="inherit" size="small" onClick={() => setOpen(false)}>
                <CloseIcon fontSize="small" />
              </IconButton>
            </>
          ) : undefined
        }
        slotProps={{ closeButton: { 'aria-label': fr.errors.close } }}
        sx={{ maxWidth: 480, alignItems: 'flex-start' }}
      >
        {toast.message}
        {toast.traceId && (
          <Box sx={{ mt: 0.75, display: 'flex', alignItems: 'center', gap: 1, fontSize: 12, flexWrap: 'wrap' }}>
            {fr.errors.supportReference} :
            <Box component="code" sx={{ fontFamily: 'ui-monospace, monospace', bgcolor: 'rgba(255,255,255,0.15)', px: 0.75, borderRadius: 0.5 }}>
              {toast.traceId}
            </Box>
            <Button size="small" color="inherit" onClick={() => void copy(toast.traceId!)} sx={{ textDecoration: 'underline', minWidth: 0, p: 0 }}>
              {copied ? fr.errors.copied : fr.errors.copy}
            </Button>
          </Box>
        )}
      </Alert>
    </Snackbar>
  );
}

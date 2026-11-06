import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import { useId } from 'react';
import { fr } from '../i18n/fr';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  hint: string;
  confirmLabel: string;
  onConfirm: () => void;
  onClose: () => void;
}

/**
 * Asks before an action that cannot be undone (empty the cart, delete the photo). The focus starts on "Annuler": a
 * reflex Enter does not destroy anything; Escape closes it.
 */
export function ConfirmDialog({ open, title, hint, confirmLabel, onConfirm, onClose }: ConfirmDialogProps) {
  const id = useId();
  return (
    <Dialog open={open} onClose={onClose} aria-labelledby={`${id}-title`} aria-describedby={`${id}-hint`} maxWidth="xs" fullWidth>
      <DialogTitle id={`${id}-title`}>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText id={`${id}-hint`}>{hint}</DialogContentText>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} autoFocus>
          {fr.cart.cancel}
        </Button>
        <Button
          color="error"
          variant="contained"
          onClick={() => {
            onClose();
            onConfirm();
          }}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

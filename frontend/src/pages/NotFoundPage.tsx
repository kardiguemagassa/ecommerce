import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router';
import { fr } from '../i18n/fr';

export function NotFoundPage() {
  return (
    <>
      <Typography variant="h1" component="h1" gutterBottom>
        {fr.pages.notFound}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {fr.pages.notFoundHint}
      </Typography>
      <Button component={RouterLink} to="/" variant="contained">
        {fr.pages.backToCatalog}
      </Button>
    </>
  );
}

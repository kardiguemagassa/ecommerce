import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import { fr } from '../i18n/fr';

/** First load of the application, while the module of the requested page arrives. */
export function PageFallback() {
  return (
    <Box sx={{ minHeight: '60vh', display: 'grid', placeItems: 'center' }}>
      <CircularProgress aria-label={fr.pages.loading} />
    </Box>
  );
}

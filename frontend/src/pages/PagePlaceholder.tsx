import Typography from '@mui/material/Typography';
import { fr } from '../i18n/fr';

/** Page of the route map whose content comes in a later pull request of v2.0.0. */
export function PagePlaceholder({ title }: { title: string }) {
  return (
    <>
      <Typography variant="h1" component="h1" gutterBottom>
        {title}
      </Typography>
      <Typography color="text.secondary">{fr.pages.comingSoon}</Typography>
    </>
  );
}

import RefreshIcon from '@mui/icons-material/Refresh';
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import { Link as RouterLink, useNavigate, useRouteError } from 'react-router';
import { fr } from '../i18n/fr';
import { Notice } from './RequireRole';
import { isStaleChunkError } from './staleChunk';

/**
 * Error boundary of the pages: the header and the navigation stay usable, only the page area shows the problem.
 */
export function RouteError() {
  const error = useRouteError();
  const navigate = useNavigate();

  if (isStaleChunkError(error)) {
    return (
      <Notice title={fr.routeError.newVersion} hint={fr.routeError.newVersionHint} icon={<RefreshIcon color="primary" fontSize="large" />}>
        <Button variant="contained" onClick={() => window.location.reload()}>
          {fr.routeError.reload}
        </Button>
      </Notice>
    );
  }
  return (
    <Notice title={fr.routeError.failed} hint={fr.routeError.failedHint} icon={<ReportProblemOutlinedIcon color="error" fontSize="large" />}>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', justifyContent: 'center' }}>
        <Button variant="contained" onClick={() => navigate(0)}>
          {fr.routeError.retry}
        </Button>
        <Button component={RouterLink} to="/" variant="outlined">
          {fr.pages.backToCatalog}
        </Button>
      </Box>
    </Notice>
  );
}

import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { NavLink, Outlet } from 'react-router';
import { fr } from '../../i18n/fr';

const SECTIONS = [
  { to: '/admin/produits', label: fr.admin.tabs.products },
  { to: '/admin/categories', label: fr.admin.tabs.categories },
  { to: '/admin/ventes', label: fr.admin.tabs.bestSellers },
  { to: '/admin/clients', label: fr.admin.tabs.customers },
  { to: '/admin/avis', label: fr.admin.tabs.reviews },
];

/**
 * Admin space: its sections are real links (address per section, back button, open in a new tab); the current one is
 * marked `aria-current="page"` by NavLink. Each section is its own lazily loaded route.
 */
export function AdminLayout() {
  return (
    <Box sx={{ display: 'grid', gap: 3 }}>
      <Typography variant="h1" component="h1">
        {fr.pages.admin}
      </Typography>
      <Box component="nav" aria-label={fr.admin.sections} sx={{ borderBottom: 1, borderColor: 'divider', overflowX: 'auto' }}>
        <Box component="ul" sx={{ display: 'flex', gap: 0.5, m: 0, p: 0, listStyle: 'none' }}>
          {SECTIONS.map((section) => (
            <li key={section.to}>
              <Box
                component={NavLink}
                to={section.to}
                sx={{
                  display: 'block', px: 2, py: 1.25, whiteSpace: 'nowrap', textDecoration: 'none', color: 'text.secondary',
                  fontWeight: 600, borderBottom: '3px solid transparent', mb: '-1px',
                  '&:hover': { color: 'text.primary' },
                  '&[aria-current="page"]': { color: 'primary.main', borderBottomColor: 'primary.main' },
                  '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: -2 },
                }}
              >
                {section.label}
              </Box>
            </li>
          ))}
        </Box>
      </Box>
      <Outlet />
    </Box>
  );
}

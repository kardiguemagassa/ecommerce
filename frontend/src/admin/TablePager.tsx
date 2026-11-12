import Box from '@mui/material/Box';
import Pagination from '@mui/material/Pagination';
import PaginationItem from '@mui/material/PaginationItem';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router';
import { fr } from '../i18n/fr';

/**
 * Results count and page links of an admin table; the links keep the search term and the filters (`base`: path of
 * the table, `filters`: other parameters of the address).
 */
export function TablePager({ base, q, page, pages, total, filters = {} }: { base: string; q: string; page: number; pages: number; total: number; filters?: Record<string, string> }) {
  const href = (target: number) => {
    const params = new URLSearchParams(filters);
    if (q) params.set('q', q);
    if (target > 1) params.set('page', String(target));
    const query = params.toString();
    return query ? `${base}?${query}` : base;
  };
  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
      <Typography color="text.secondary">{fr.admin.results(total, page, pages)}</Typography>
      {pages > 1 && (
        <Pagination
          page={Math.min(page, pages)}
          count={pages}
          shape="rounded"
          color="primary"
          aria-label={fr.admin.pagination}
          getItemAriaLabel={(type, number, selected) =>
            type === 'page' ? fr.catalog.pageLabel(number ?? 1, selected) : type === 'previous' ? fr.catalog.previous : fr.catalog.next
          }
          renderItem={(item) => <PaginationItem component={RouterLink} to={href(item.page ?? 1)} {...item} />}
        />
      )}
    </Box>
  );
}

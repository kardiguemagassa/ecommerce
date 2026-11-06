import SearchOffIcon from '@mui/icons-material/SearchOff';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Pagination from '@mui/material/Pagination';
import PaginationItem from '@mui/material/PaginationItem';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Typography from '@mui/material/Typography';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo } from 'react';
import { Link as RouterLink, useNavigation, useSearchParams } from 'react-router';
import { categoriesQuery, PAGE_SIZE, productListQuery, type ProductFilters } from '../api/catalog';
import { BestSellers } from '../catalog/BestSellers';
import { CategoryFilter, SortSelect } from '../catalog/CatalogControls';
import { catalogHref, filtersFrom, isFiltered, searchParamsOf, withChange } from '../catalog/filters';
import { ProductCard } from '../catalog/ProductCard';
import { fr } from '../i18n/fr';

/**
 * Catalogue: best sellers, then the products of the search held in the URL. The route loader has fetched the page
 * and the categories already; a change of filter is a navigation, so the previous results stay on screen (dimmed)
 * until the next ones are there.
 */
export function CatalogPage() {
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => filtersFrom(params), [params]);
  const { data: page } = useQuery(productListQuery(filters));
  const { data: categories = [] } = useQuery(categoriesQuery());
  const navigation = useNavigation();
  const searching = navigation.state === 'loading' && navigation.location.pathname === '/';
  usePrefetchNextPage(filters, page?.page.totalPages ?? 0);

  // Filters keep the scroll position; a new page of results starts at the top (Pagination links).
  const apply = (next: ProductFilters) => setParams(searchParamsOf(next), { preventScrollReset: true });
  const total = page?.page.totalElements ?? 0;
  const pages = page?.page.totalPages ?? 0;
  const category = categories.find((item) => item.id === filters.categoryId);

  return (
    <Box sx={{ display: 'grid', gap: 4 }}>
      {!isFiltered(filters) && filters.page === 1 && <BestSellers />}

      <Box component="section" aria-labelledby="catalog" sx={{ display: 'grid', gap: 2.5 }}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-end', justifyContent: 'space-between', gap: 2 }}>
          <div>
            <Typography id="catalog" variant="h1" component="h1">
              {filters.q ? fr.catalog.searchFor(filters.q) : (category?.name ?? fr.pages.catalog)}
            </Typography>
            <Typography color="text.secondary" role="status">
              {fr.catalog.results(total)}
              {pages > 1 && ` · ${fr.catalog.pageOf(filters.page, pages)}`}
            </Typography>
          </div>
          <SortSelect value={filters.sort} onChange={(sort) => apply(withChange(filters, { sort }))} />
        </Box>

        <CategoryFilter categories={categories} selected={filters.categoryId} onSelect={(categoryId) => apply(withChange(filters, { categoryId }))} />

        {!page ? (
          <ProductGridSkeleton />
        ) : page.content.length === 0 ? (
          <EmptyResults onClear={() => apply({ ...filters, q: '', categoryId: undefined, page: 1 })} />
        ) : (
          <Box
            component="ul"
            aria-busy={searching}
            sx={{
              listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 2.5,
              gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
              opacity: searching ? 0.6 : 1, transition: 'opacity 150ms',
            }}
          >
            {page.content.map((product) => (
              <li key={product.id}>
                <ProductCard product={product} />
              </li>
            ))}
          </Box>
        )}

        {pages > 1 && (
          <Pagination
            page={Math.min(filters.page, pages)}
            count={pages}
            shape="rounded"
            color="primary"
            sx={{ display: 'flex', justifyContent: 'center', pt: 1 }}
            getItemAriaLabel={(type, number, selected) =>
              type === 'page' ? fr.catalog.pageLabel(number ?? 1, selected) : type === 'previous' ? fr.catalog.previous : fr.catalog.next
            }
            // Real links: open in a new tab, share, and the next page is prefetched before the click.
            renderItem={(item) => (
              <PaginationItem component={RouterLink} to={catalogHref({ ...filters, page: item.page ?? 1 })} {...item} />
            )}
          />
        )}
      </Box>
    </Box>
  );
}

/** The next page of results is fetched in the background: "Suivant" then shows at once. */
function usePrefetchNextPage(filters: ProductFilters, totalPages: number) {
  const queryClient = useQueryClient();
  useEffect(() => {
    if (filters.page < totalPages) {
      void queryClient.prefetchQuery(productListQuery({ ...filters, page: filters.page + 1 }));
    }
  }, [queryClient, filters, totalPages]);
}

function EmptyResults({ onClear }: { onClear: () => void }) {
  return (
    <Paper variant="outlined" sx={{ p: 5, borderRadius: 4, textAlign: 'center', display: 'grid', gap: 1.5, justifyItems: 'center' }}>
      <SearchOffIcon color="primary" fontSize="large" aria-hidden />
      <Typography variant="h2" component="h2">
        {fr.catalog.empty}
      </Typography>
      <Typography color="text.secondary">{fr.catalog.emptyHint}</Typography>
      <Button variant="outlined" onClick={onClear}>
        {fr.catalog.clearFilters}
      </Button>
    </Paper>
  );
}

function ProductGridSkeleton() {
  return (
    <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))' }} aria-hidden>
      {Array.from({ length: PAGE_SIZE }, (_, index) => (
        <Skeleton key={index} variant="rounded" sx={{ borderRadius: 4, height: 340 }} />
      ))}
    </Box>
  );
}

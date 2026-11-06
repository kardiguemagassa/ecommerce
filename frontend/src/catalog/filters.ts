import { DEFAULT_SORT, SORTS, type ProductFilters, type Sort } from '../api/catalog';

/**
 * The catalogue state lives in the URL (?q=&categorie=&tri=&page=): a search can be bookmarked, shared, reloaded,
 * and the browser's back button goes back to the previous one. Invalid values fall back to the defaults.
 */
export function filtersFrom(params: URLSearchParams): ProductFilters {
  const category = Number(params.get('categorie'));
  const page = Number(params.get('page'));
  const sort = params.get('tri');
  return {
    q: (params.get('q') ?? '').trim(),
    categoryId: Number.isInteger(category) && category > 0 ? category : undefined,
    sort: sort && sort in SORTS ? (sort as Sort) : DEFAULT_SORT,
    page: Number.isInteger(page) && page > 1 ? page : 1,
  };
}

/** The URL parameters of a search; defaults are left out, so the plain catalogue stays at "/". */
export function searchParamsOf(filters: ProductFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.q) params.set('q', filters.q);
  if (filters.categoryId) params.set('categorie', String(filters.categoryId));
  if (filters.sort !== DEFAULT_SORT) params.set('tri', filters.sort);
  if (filters.page > 1) params.set('page', String(filters.page));
  return params;
}

/** Changing the search, the category or the order starts again from the first page. */
export function withChange(filters: ProductFilters, change: Partial<Omit<ProductFilters, 'page'>>): ProductFilters {
  return { ...filters, ...change, page: 1 };
}

export const isFiltered = (filters: ProductFilters) => Boolean(filters.q || filters.categoryId);

/** Link to a search of the catalogue. */
export function catalogHref(filters: ProductFilters): string {
  const query = searchParamsOf(filters).toString();
  return query ? `/?${query}` : '/';
}

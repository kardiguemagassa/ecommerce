import { describe, expect, it } from 'vitest';
import { catalogHref, filtersFrom, searchParamsOf, withChange } from './filters';

describe('Catalogue URL', () => {
  it('reads the search, the category, the order and the page', () => {
    expect(filtersFrom(new URLSearchParams('q=%20clavier%20&categorie=2&tri=prix-croissant&page=3'))).toEqual({
      q: 'clavier', categoryId: 2, sort: 'prix-croissant', page: 3,
    });
  });

  it('falls back to the defaults on invalid values', () => {
    expect(filtersFrom(new URLSearchParams('categorie=abc&tri=hack&page=-4'))).toEqual({ q: '', categoryId: undefined, sort: 'nom', page: 1 });
  });

  it('leaves the defaults out, so the plain catalogue stays at /', () => {
    expect(catalogHref({ q: '', sort: 'nom', page: 1 })).toBe('/');
    expect(searchParamsOf({ q: 'souris', categoryId: 1, sort: 'nouveautes', page: 2 }).toString()).toBe('q=souris&categorie=1&tri=nouveautes&page=2');
  });

  it('starts again from the first page when the search changes', () => {
    expect(withChange({ q: '', sort: 'nom', page: 4 }, { categoryId: 2 })).toEqual({ q: '', categoryId: 2, sort: 'nom', page: 1 });
  });
});

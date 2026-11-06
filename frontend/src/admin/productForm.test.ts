import { describe, expect, it } from 'vitest';
import { emptyProduct, parsePrice, serverErrors, toInput, validate, valuesOf } from './productForm';

const valid = { name: ' Souris sans fil ', description: '', categoryId: '1', price: '39,90', stock: '3' };

describe('Product form', () => {
  it('reads French and English decimals, with thousands separators', () => {
    expect(parsePrice('24,90')).toBe(24.9);
    expect(parsePrice('24.9')).toBe(24.9);
    expect(parsePrice('1 299')).toBe(1299);
    expect(parsePrice('12,345')).toBeUndefined();
    expect(parsePrice('douze')).toBeUndefined();
  });

  it('applies the constraints of the API, in French', () => {
    expect(validate(valid)).toEqual({});
    expect(validate(emptyProduct)).toEqual({
      name: 'Ce champ est obligatoire.',
      categoryId: 'Ce champ est obligatoire.',
      price: 'Ce champ est obligatoire.',
    });
    expect(validate({ ...valid, name: 'x'.repeat(151), price: '0', stock: '-1' })).toEqual({
      name: '150 caractères maximum.',
      price: "Le prix doit être d'au moins 0,01 €.",
      stock: 'Le stock doit être positif ou nul.',
    });
    expect(validate({ ...valid, stock: '2,5' }).stock).toBe('Le stock est un nombre entier.');
  });

  it('sends trimmed values as numbers, and shows a product with a decimal comma', () => {
    expect(toInput(valid)).toEqual({ name: 'Souris sans fil', description: undefined, categoryId: 1, price: 39.9, stock: 3 });
    expect(valuesOf({ id: 11, name: 'Souris', price: 39.9, stock: 3, categoryId: 1 })).toMatchObject({ price: '39,90', categoryId: '1' });
  });

  it('marks the fields the API refused', () => {
    expect(serverErrors({ price: 'must be greater than or equal to 0.01', other: 'x' })).toEqual({ price: 'Valeur refusée par le serveur.' });
  });
});

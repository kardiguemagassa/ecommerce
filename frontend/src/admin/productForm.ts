import type { ProductInput } from '../api/admin';
import type { Product } from '../api/catalog';
import { fr } from '../i18n/fr';

/** The product form as typed: text fields, French decimal comma accepted for the price. */
export interface ProductValues {
  name: string;
  description: string;
  categoryId: string;
  price: string;
  stock: string;
}

export type ProductErrors = Partial<Record<keyof ProductValues, string>>;

export const NAME_MAX = 150;
export const DESCRIPTION_MAX = 2000;

export const emptyProduct: ProductValues = { name: '', description: '', categoryId: '', price: '', stock: '0' };

export const valuesOf = (product: Product): ProductValues => ({
  name: product.name ?? '',
  description: product.description ?? '',
  categoryId: product.categoryId ? String(product.categoryId) : '',
  price: product.price === undefined ? '' : product.price.toFixed(2).replace('.', ','),
  stock: String(product.stock ?? 0),
});

/** "24,90", "24.9", "1 299" → number; anything else (letters, three decimals...) → undefined. */
export function parsePrice(text: string): number | undefined {
  const compact = text.replace(/\s/g, ''); // \s includes the non-breaking spaces of French numbers
  if (!/^\d+([.,]\d{1,2})?$/.test(compact)) return undefined;
  return Number(compact.replace(',', '.'));
}

/** The constraints of the API (ProductRequest), checked before sending, in French. */
export function validate(values: ProductValues): ProductErrors {
  const errors: ProductErrors = {};
  const name = values.name.trim();
  if (!name) errors.name = fr.admin.form.required;
  else if (name.length > NAME_MAX) errors.name = fr.admin.form.nameTooLong;
  if (values.description.trim().length > DESCRIPTION_MAX) errors.description = fr.admin.form.descriptionTooLong;
  if (!values.categoryId) errors.categoryId = fr.admin.form.required;

  if (!values.price.trim()) errors.price = fr.admin.form.required;
  else {
    const price = parsePrice(values.price);
    if (price === undefined) errors.price = fr.admin.form.priceFormat;
    else if (price < 0.01) errors.price = fr.admin.form.pricePositive;
  }

  const stock = values.stock.trim();
  if (!stock) errors.stock = fr.admin.form.required;
  else if (!/^-?\d+$/.test(stock)) errors.stock = fr.admin.form.stockInteger;
  else if (Number(stock) < 0) errors.stock = fr.admin.form.stockPositive;
  return errors;
}

/** Valid values (see validate) as the API takes them. */
export const toInput = (values: ProductValues): ProductInput => ({
  name: values.name.trim(),
  description: values.description.trim() || undefined,
  categoryId: Number(values.categoryId),
  price: parsePrice(values.price)!,
  stock: Number(values.stock.trim()),
});

/** Field errors of a 400 from the API (its own validation): shown under the fields concerned. */
export function serverErrors(errors: Record<string, string> | undefined): ProductErrors {
  const fields: (keyof ProductValues)[] = ['name', 'description', 'categoryId', 'price', 'stock'];
  return Object.fromEntries(
    fields.filter((field) => errors?.[field]).map((field) => [field, fr.admin.form.rejected]),
  ) as ProductErrors;
}

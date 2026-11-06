const price = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });

/** 89.9 → "89,90 €" (narrow no-break space before the symbol, as French typography wants). */
export const formatPrice = (amount: number | undefined) => price.format(amount ?? 0);

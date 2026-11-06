const price = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });

/** 89.9 → "89,90 €" (narrow no-break space before the symbol, as French typography wants). */
export const formatPrice = (amount: number | undefined) => price.format(amount ?? 0);

const date = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium' });
const dateTime = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeStyle: 'short' });
const time = new Intl.DateTimeFormat('fr-FR', { timeStyle: 'medium' });

/** "2026-11-05T15:42:07Z" → "5 nov. 2026" (local time zone of the browser). */
export const formatDate = (iso: string | undefined) => (iso ? date.format(new Date(iso)) : '');

/** → "5 novembre 2026 à 16:42". */
export const formatDateTime = (iso: string | undefined) => (iso ? dateTime.format(new Date(iso)) : '');

/** → "16:42:07". */
export const formatTime = (iso: string | undefined) => (iso ? time.format(new Date(iso)) : '');

import { CUSTOMER } from '../support/env';
import { expect, test } from '../support/fixtures';
import { pngFile } from '../support/png';

test.use({ storageState: CUSTOMER.state });

test('a customer sets a profile photo, sees it in the header, then deletes it', async ({ page }) => {
  await page.goto('/profil');
  await expect(page.getByText(CUSTOMER.email)).toBeVisible();
  await expect(page.getByRole('link', { name: /Modifier mon nom, e-mail ou mot de passe/ })).toHaveAttribute('href', /\/realms\/ecommerce\/account$/);

  const accountMenu = page.getByRole('button', { name: `Menu du compte : ${CUSTOMER.name}` });
  await page.getByLabel('Changer la photo').setInputFiles(pngFile('moi.png', 320, 320));
  await expect(page.getByRole('img', { name: 'Aperçu de la nouvelle photo' })).toBeVisible();
  await page.getByRole('button', { name: 'Enregistrer la photo' }).click();

  await expect(page.getByText('Photo de profil mise à jour')).toBeVisible();
  const photo = accountMenu.locator('img');
  await expect(photo).toHaveAttribute('src', /\/media\/avatars\/.+\.jpg$/);
  // Served by nginx from the media storage, re-encoded by the API.
  expect((await page.request.get((await photo.getAttribute('src'))!)).headers()['content-type']).toBe('image/jpeg');

  await page.getByRole('button', { name: 'Supprimer la photo' }).click();
  await page.getByRole('dialog', { name: 'Supprimer la photo de profil ?' }).getByRole('button', { name: 'Supprimer la photo' }).click();
  await expect(page.getByText('Photo de profil supprimée')).toBeVisible();
  await expect(accountMenu.locator('img')).toHaveCount(0);
  await expect(accountMenu).toContainText('AM');
});

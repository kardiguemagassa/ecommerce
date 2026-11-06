import { BASE_URL, RUN, setting } from '../support/env';
import { expect, expectAccessible, test } from '../support/fixtures';

/** The Keycloak pages in the look of the shop (theme "comptoir"), and a new customer signing up through them. */

test('the login page has the look and wording of the shop, and leads back to it', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Se connecter' }).first().click();
  await page.waitForURL(/\/realms\/ecommerce\//);

  await expect(page).toHaveTitle('Connexion · Comptoir');
  await expect(page.locator('#kc-header-wrapper')).toHaveText('Comptoir');
  await expect(page.getByRole('heading', { name: 'Connexion', level: 1 })).toBeVisible();
  await expect(page.getByLabel('Adresse e-mail')).toBeVisible();
  await expect(page.getByRole('checkbox', { name: 'Rester connecté' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Créer un compte' })).toBeVisible();
  // The shop's colours and type, not Keycloak's.
  await expect(page.getByRole('button', { name: 'Se connecter' })).toHaveCSS('background-color', 'rgb(14, 90, 97)');
  await expect(page.locator('#kc-page-title')).toHaveCSS('font-family', /Manrope/);
  await expectAccessible(page, 'the login page');

  await page.getByRole('link', { name: 'Retour à la boutique' }).click();
  await expect(page).toHaveURL(`${BASE_URL}/`);
});

test('a wrong password is explained in French', async ({ page }) => {
  await page.goto('/oauth2/authorization/keycloak');
  await page.getByLabel('Adresse e-mail').fill('alice@example.com');
  await page.getByLabel('Mot de passe', { exact: true }).fill('not-the-password');
  await page.getByRole('button', { name: 'Se connecter' }).click();

  await expect(page.getByText('Adresse e-mail ou mot de passe incorrect.')).toBeVisible();
});

test('a new customer signs up (password rules in French), and comes back signed in to the shop', async ({ page }) => {
  const email = `e2e-${RUN}@example.com`;
  await page.goto('/oauth2/authorization/keycloak');
  await page.getByRole('link', { name: 'Créer un compte' }).click();

  await expect(page.getByRole('heading', { name: 'Créer un compte', level: 1 })).toBeVisible();
  // Required fields carry an asterisk in their label ("Nom *"): matched by the start of the label.
  const field = (label: RegExp) => page.getByRole('textbox', { name: label });
  // Password inputs have no role: their Keycloak ids (the label text carries the template's line breaks).
  const password = page.locator('#password');
  const confirmation = page.locator('#password-confirm');
  await field(/^Prénom/).fill('Camille');
  await field(/^Nom\b/).fill('Durand');
  await field(/^Adresse e-mail/).fill(email);
  await password.fill('court');
  await confirmation.fill('court');
  await page.getByRole('button', { name: 'Créer un compte' }).click();

  await expect(page.getByText('Le mot de passe doit contenir au moins 12 caractères.')).toBeVisible();
  await expectAccessible(page, 'the sign-up page with an error');

  // Same rules as the demo accounts' password (length 12, not the e-mail): a long one, unique to the run.
  const strong = `${setting('DEMO_USER_PASSWORD')}-${RUN}-Comptoir`;
  await password.fill(strong);
  await confirmation.fill(strong);
  await page.getByRole('button', { name: 'Créer un compte' }).click();

  await expect(page).toHaveURL(`${BASE_URL}/`);
  await expect(page.getByRole('button', { name: 'Menu du compte : Camille Durand' })).toBeVisible();
});

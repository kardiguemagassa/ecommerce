import AxeBuilder from '@axe-core/playwright';
import { expect, test as base, type Page } from '@playwright/test';

/**
 * Every test fails on a Content Security Policy violation or an uncaught script error in the page: nginx serves the
 * real headers, and a blocked script or style would otherwise go unnoticed.
 */
export const test = base.extend<{ browserErrors: string[] }>({
  browserErrors: [
    async ({ page }, use) => {
      const errors: string[] = [];
      page.on('console', (message) => {
        if (message.type() === 'error' && /Content Security Policy/i.test(message.text())) errors.push(`CSP: ${message.text()}`);
      });
      page.on('pageerror', (error) => errors.push(`script error: ${error.message}`));
      await use(errors);
      expect(errors, 'CSP violations and script errors in the page').toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };

/**
 * Status of the order on its tracking page. Other live regions share the role "status" (toasts, the quantity of a
 * cart line): the badge is the one that reads a status.
 */
export const orderStatus = (page: Page) => page.getByRole('status').filter({ hasText: /^(En attente de confirmation…|Confirmée)$/ });

/**
 * Accessibility of the page as displayed (WCAG 2.1 A and AA rules of axe): no violation of impact "serious" or
 * "critical". The message lists each one with the elements concerned.
 */
export async function expectAccessible(page: Page, what: string): Promise<void> {
  // Measured once still: during the fade-in of a dialog its text is half transparent (false contrast failures).
  await page.waitForFunction(() => document.getAnimations().every((animation) => animation.playState !== 'running'));
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const blocking = results.violations
    .filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
    .map((violation) => `${violation.id} (${violation.impact}): ${violation.help} → ${violation.nodes.map((node) => node.target.join(' ')).join(', ')}`);
  expect(blocking, `accessibility of ${what}`).toEqual([]);
}

import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CategoryFilter } from './CatalogControls';

const categories = Array.from({ length: 12 }, (_, index) => ({ id: index + 1, name: `Catégorie ${index + 1}` }));

/**
 * jsdom lays nothing out: each button is 100 px wide, side by side, the rail shows 300 px of them and remembers where
 * it was scrolled to. Enough to check the arrows and the selected category kept in view.
 */
function layOut() {
  const scrolled = new WeakMap<Element, number>();
  const spies = [
    vi.spyOn(HTMLElement.prototype, 'offsetLeft', 'get').mockImplementation(function (this: HTMLElement) {
      return this.parentElement ? [...this.parentElement.children].indexOf(this) * 100 : 0;
    }),
    vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(100),
  ];
  const rail = () => screen.getByRole('group');
  Object.defineProperties(HTMLElement.prototype, {
    clientWidth: { configurable: true, get: () => 300 },
    scrollWidth: { configurable: true, get() { return this.children.length * 100; } },
    scrollLeft: { configurable: true, get() { return scrolled.get(this) ?? 0; }, set(value: number) { scrolled.set(this, value); } },
  });
  return { rail, restore: () => {
    spies.forEach((spy) => spy.mockRestore());
    for (const name of ['clientWidth', 'scrollWidth', 'scrollLeft']) delete (HTMLElement.prototype as unknown as Record<string, unknown>)[name];
  } };
}

const arrow = (side: 'start' | 'end') => document.querySelector(`[data-scroll="${side}"]`);

describe('CategoryFilter', () => {
  it('presses "Toutes" without a selection and selects a category', async () => {
    const onSelect = vi.fn();
    render(<CategoryFilter categories={categories.slice(0, 2)} onSelect={onSelect} />);

    expect(screen.getByRole('button', { name: 'Toutes' })).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(screen.getByRole('button', { name: 'Catégorie 2' }));
    expect(onSelect).toHaveBeenCalledWith(2);
    expect(arrow('start')).toBeNull();
    expect(arrow('end')).toBeNull(); // everything fits: no arrow
  });

  describe('more categories than the width', () => {
    let layout: ReturnType<typeof layOut>;
    beforeEach(() => {
      layout = layOut();
    });
    afterEach(() => layout.restore());

    it('shows the arrow towards the hidden categories, and scrolls by a page', () => {
      const scrollBy = vi.fn();
      render(<CategoryFilter categories={categories} onSelect={() => {}} />);
      layout.rail().scrollBy = scrollBy;

      expect(arrow('start')).toBeNull();
      fireEvent.click(arrow('end')!);
      expect(scrollBy).toHaveBeenCalledWith(expect.objectContaining({ left: 300 - 2 * 48 }));

      layout.rail().scrollLeft = 1000; // 13 buttons: 1 300 px, the rail at the end
      fireEvent.scroll(layout.rail());
      expect(arrow('start')).not.toBeNull();
      expect(arrow('end')).toBeNull();
    });

    it('brings the selected category into view, clear of the faded edge', () => {
      render(<CategoryFilter categories={categories} selected={9} onSelect={() => {}} />);

      // "Catégorie 9" is the 10th button: 900 to 1 000 px, shown with 48 px to spare in a 300 px rail.
      expect(layout.rail().scrollLeft).toBe(1000 + 48 - 300);
      expect(arrow('start')).not.toBeNull();
      expect(arrow('end')).not.toBeNull();
    });

    it('keeps the arrows out of the keyboard and screen readers', () => {
      render(<CategoryFilter categories={categories} onSelect={() => {}} />);

      expect(arrow('end')).toHaveAttribute('aria-hidden', 'true');
      expect(arrow('end')).toHaveAttribute('tabindex', '-1');
      expect(screen.getAllByRole('button')).toHaveLength(13);
    });
  });
});

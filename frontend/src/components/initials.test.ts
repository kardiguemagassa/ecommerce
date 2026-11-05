import { describe, expect, it } from 'vitest';
import { initials } from './initials';

describe('initials', () => {
  it.each([
    ['Alice Martin', 'AM'],
    ['Kardigué MAGASSA', 'KM'],
    ['alice', 'A'],
    ['  Jean  Pierre  Durand ', 'JD'],
    ['', '?'],
    [undefined, '?'],
  ])('of %j is %s', (name, expected) => {
    expect(initials(name)).toBe(expected);
  });
});

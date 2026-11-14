/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment jsdom

import { cleanup, render } from '@testing-library/react';
import { verdictWords } from '@semantifyr/editor-common/terms';
import { afterEach, describe, expect, it } from 'vitest';

import CaseVerdict from '../components/CaseVerdict';
import type { ServedText } from '../components/LiveEmbed';

// Goal: a page shows a case's verdict as its manifest expects it, in live's word and mark, and a case the manifest lacks fails the page

const EXAMPLE: ServedText = {
  language: 'oxsts',
  id: 'lights',
  text: '',
  verdicts: {
    NeverBothGreen: 'Passed',
    GreenOnlyWhileRed: 'Failed',
  },
};

afterEach(() => {
  cleanup();
});

describe('CaseVerdict', () => {
  it('shows the expected verdict of a case in live\'s word and mark', () => {
    const { container } = render(<CaseVerdict example={EXAMPLE} name="GreenOnlyWhileRed" />);

    expect(container.textContent).toBe(`GreenOnlyWhileRed${verdictWords.failed}`);
    expect(container.querySelector('.verdict-mark.status-failed')).not.toBeNull();
  });

  it('refuses a case its manifest does not have', () => {
    expect(() => {
      render(<CaseVerdict example={EXAMPLE} name="NeverBothRed" />);
    }).toThrow('The manifest expects no verdict of the case NeverBothRed of /oxsts/lights');
  });
});

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment jsdom

import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import DecisionCases from '../components/DecisionCases';

// Goal: each decision links to its section and shows the cases that name it with their model, and a decision no case names says so

const DECISIONS = [
  {
    id: 'item-drop',
    title: 'The Dropped Item',
    cases: [
      { model: 'semanticstest.sysml', name: 'UnacceptedItemIsDroppedTestCase' },
    ],
  },
  {
    id: 'lock-step',
    title: 'Lock-Step Composition',
    cases: [],
  },
];

afterEach(() => {
  cleanup();
});

describe('the decision cases', () => {
  it('link each decision to its section and show its cases or that it has none', () => {
    const { container } = render(<DecisionCases decisions={DECISIONS} />);

    const rows = [...container.querySelectorAll('tbody tr')].map((row) => {
      const cells = row.querySelectorAll('td');
      return [cells[0]?.querySelector('a')?.getAttribute('href'), cells[1]?.textContent];
    });
    expect(rows).toEqual([
      ['#item-drop', 'UnacceptedItemIsDroppedTestCase in semanticstest.sysml'],
      ['#lock-step', 'No case'],
    ]);
  });
});

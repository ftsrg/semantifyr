/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment jsdom

import { cleanup, render } from '@testing-library/react';
import { outcomeTerms } from '@semantifyr/editor-common/terms';
import { afterEach, describe, expect, it } from 'vitest';

import GammaConstructTable from '../components/GammaConstructs';
import SysmlConstructTable from '../components/SysmlConstructs';

// Goal: both construct tables show an outcome by the terms file's word with its warning beside it, under a legend of the terms file's meanings, and a row in a word they do not know fails the page

const SYSML = [
  'construct\toutcome\twarning\tdetail',
  'Item definition specializing another\tdeviating\tyes\tAn accept does not accept its specializations',
  'Do action\tplanned\tno\tSemantifyr does not support do actions',
].join('\n');

const GAMMA = [
  'area\tconstruct\toutcome\twarning\tfixture\tmarker\tdetail',
  'Time\tTime passing\tabstracted\tno\tmodels/semantics/Timeouts.gamma\tTheShorterTimeoutIsUpFirst\tEvery step advances time by one second.',
].join('\n');

const SIX = Object.keys(outcomeTerms).join(', ');

afterEach(() => {
  cleanup();
});

describe('the construct tables', () => {
  it('show an outcome by its word and its warning beside it', () => {
    const { container } = render(<SysmlConstructTable table={SYSML} />);

    const cells = [...container.querySelectorAll('tbody tr')].map((row) => {
      return row.querySelectorAll('td')[1]?.textContent;
    });
    expect(cells).toEqual([`${outcomeTerms.deviating.term}, with a warning`, outcomeTerms.planned.term]);
  });

  it('say what each of the six outcomes means', () => {
    const { container } = render(<GammaConstructTable table={GAMMA} />);

    const legend = [...container.querySelectorAll('dl > div')].map((entry) => {
      return [entry.querySelector('dt')?.textContent, entry.querySelector('dd')?.textContent];
    });
    expect(legend).toEqual(Object.values(outcomeTerms).map((term) => {
      return [term.term, term.meaning];
    }));
  });

  it('refuse a row in a word that is no outcome, naming the row and the six', () => {
    const old = GAMMA.replace('abstracted', 'departure');

    expect(() => {
      render(<GammaConstructTable table={old} />);
    }).toThrow(new RegExp(`outcome departure in the row Time\\tTime passing.*where the outcomes are ${SIX}`));
    expect(() => {
      render(<SysmlConstructTable table={SYSML.replace('planned', 'refused')} />);
    }).toThrow(`where the outcomes are ${SIX}`);
  });

  it('refuse a table whose header they do not know', () => {
    expect(() => {
      render(<SysmlConstructTable table={SYSML.replace('outcome\twarning', 'status')} />);
    }).toThrow('must start with the header');
  });
});

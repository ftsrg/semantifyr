/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment jsdom

import { cleanup, render } from '@testing-library/react';
import { outcomeTerms, outcomeWarning } from '@semantifyr/editor-common/terms';
import { afterEach, describe, expect, it } from 'vitest';

import GammaConstructTable, { GammaDifferences } from '../components/GammaConstructs';
import SysmlConstructTable, { SysmlConstructsOf } from '../components/SysmlConstructs';

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
    expect(cells).toEqual([`${outcomeTerms.deviating.term}, ${outcomeWarning}`, outcomeTerms.planned.term]);
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

  it('list as Gamma\'s differences the rows whose outcome the terms file calls a difference', () => {
    const table = `${GAMMA}\nTime\tTimeouts in seconds\tconformant\tno\tmodels/semantics/Timeouts.gamma\twhen timeout short\tIn seconds.`;
    const { container } = render(<GammaDifferences table={table} />);

    expect(outcomeTerms.abstracted.difference).toBe(true);
    expect(outcomeTerms.conformant.difference).toBe(false);
    expect([...container.querySelectorAll('li strong')].map((name) => {
      return name.textContent;
    })).toEqual(['Time passing']);
  });

  it('list the rows of one outcome', () => {
    const gamma = `${GAMMA}\nVariation points\tA written priority under priority off\tdeviating\tyes\tmodels/semantics/TransitionPriority.gamma\t(1) from Idle to Low\tThe priority is ignored.`;
    const names = (container: HTMLElement): (string | null)[] => {
      return [...container.querySelectorAll('li strong')].map((name) => {
        return name.textContent;
      });
    };

    expect(names(render(<SysmlConstructsOf table={SYSML} outcome="deviating" />).container)).toEqual(['Item definition specializing another']);
    cleanup();
    expect(names(render(<GammaDifferences table={gamma} outcome="deviating" />).container)).toEqual(['A written priority under priority off']);
  });

  it('refuse a table whose header they do not know', () => {
    expect(() => {
      render(<SysmlConstructTable table={SYSML.replace('outcome\twarning', 'status')} />);
    }).toThrow('must start with the header');
  });
});

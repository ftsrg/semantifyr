/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { useEffect, useMemo, useRef, useState } from 'react';
import type React from 'react';
import clsx from 'clsx';
import { highlight, styleOf, type Token } from '@site/src/lib/highlight';
import { withCode } from '@site/src/lib/inlineCode';
import { lineDiff } from '@site/src/lib/lineDiff';
import type { WalkStep } from './walks';
import styles from './walk.module.css';

type View = 'side-by-side' | 'one-pane';

export const STEP_MILLISECONDS = 3000;

// Docusaurus' own breakpoint, below which the sidebar folds away
const NARROW_SCREEN = '(max-width: 996px)';
const LESS_MOTION = '(prefers-reduced-motion: reduce)';

interface Props {
  steps: WalkStep[];
  label: string;
  // off where the after is no edit of the before, which a line's marks would claim
  marks?: boolean;
}

type Mark = 'same' | 'added' | 'removed';

interface ShownLine {
  tokens: Token[];
  mark: Mark;
}

function matches(query: string): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function' && window.matchMedia(query).matches;
}

function tokensOf(text: string): Token[][] {
  return highlight(text, 'oxsts') ?? text.split('\n').map((line) => {
    return [{ text: line, scopes: [] }];
  });
}

// the before and the after of a step and their difference, each line of each in its tokens
function linesOf(step: WalkStep): { before: ShownLine[], after: ShownLine[], unified: ShownLine[] } {
  const beforeTokens = tokensOf(step.before.text);
  const afterTokens = tokensOf(step.after.text);
  const before: ShownLine[] = [];
  const after: ShownLine[] = [];
  const unified: ShownLine[] = [];
  for (const line of lineDiff(step.before.text, step.after.text)) {
    if (line.kind !== 'added') {
      const shown = { tokens: beforeTokens[before.length] ?? [], mark: line.kind };
      before.push(shown);
      unified.push(shown);
    }
    if (line.kind !== 'removed') {
      const shown = { tokens: afterTokens[after.length] ?? [], mark: line.kind };
      after.push(shown);
      if (line.kind === 'added') {
        unified.push(shown);
      }
    }
  }
  return { before, after, unified };
}

function unmarkedLinesOf(step: WalkStep): { before: ShownLine[], after: ShownLine[], unified: ShownLine[] } {
  const unmarked = (text: string): ShownLine[] => {
    return tokensOf(text).map((tokens) => {
      return { tokens, mark: 'same' };
    });
  };
  const after = unmarked(step.after.text);
  return { before: unmarked(step.before.text), after, unified: after };
}

const SIGNS: Readonly<Record<Mark, string>> = {
  same: ' ',
  added: '+',
  removed: '-',
};

const SPOKEN: Readonly<Record<Mark, string>> = {
  same: '',
  added: 'Added: ',
  removed: 'Removed: ',
};

function Line({ line, appearing }: { line: ShownLine, appearing: boolean }): React.JSX.Element {
  // the indentation stays outside the mark, so that a strike runs through the code alone
  const indentation = /^\s*/.exec(line.tokens.map((token) => {
    return token.text;
  }).join(''))?.[0] ?? '';
  let unindented = indentation.length;
  const content = line.tokens.map((token, index) => {
    const cut = Math.min(unindented, token.text.length);
    unindented -= cut;
    return <span key={index} style={styleOf(token.scopes)}>{token.text.slice(cut)}</span>;
  });
  const marked = line.mark === 'added' ? <ins>{content}</ins> : line.mark === 'removed' ? <del>{content}</del> : content;
  return (
    <span className={clsx(styles.line, line.mark !== 'same' && styles[line.mark], appearing && line.mark === 'added' && styles.appearing)}>
      <span className={styles.sign} aria-hidden="true">{SIGNS[line.mark]}</span>
      {indentation}
      {SPOKEN[line.mark] === '' ? null : <span className={styles.spoken}>{SPOKEN[line.mark]}</span>}
      {marked}
      {'\n'}
    </span>
  );
}

function Pane({ title, lines, appearing }: { title: string, lines: ShownLine[], appearing: boolean }): React.JSX.Element {
  return (
    <figure className={styles.pane}>
      <figcaption className={styles.paneTitle}>{title}</figcaption>
      <pre className={styles.code} tabIndex={0}>
        <code>
          {lines.map((line, index) => {
            return <Line key={index} line={line} appearing={appearing} />;
          })}
        </code>
      </pre>
    </figure>
  );
}

// the compiler's output for one model, a step at a time: side by side, or one pane that rewrites itself
export default function PhaseWalk({ steps, label, marks = true }: Props): React.JSX.Element {
  const [index, setIndex] = useState(0);
  const [view, setView] = useState<View>('side-by-side');
  const [playing, setPlaying] = useState(false);
  const [lessMotion, setLessMotion] = useState(false);
  const chosen = useRef(false);
  const last = steps.length - 1;
  const step = steps[Math.min(index, last)];
  if (step === undefined) {
    throw new Error(`The walk ${label} has no step`);
  }
  const lines = useMemo(() => {
    return marks ? linesOf(step) : unmarkedLinesOf(step);
  }, [step, marks]);

  useEffect(() => {
    const follow = (): void => {
      setLessMotion(matches(LESS_MOTION));
      if (!chosen.current) {
        setView(matches(NARROW_SCREEN) ? 'one-pane' : 'side-by-side');
      }
    };
    follow();
    if (typeof window.matchMedia !== 'function') {
      return undefined;
    }
    const queries = [window.matchMedia(NARROW_SCREEN), window.matchMedia(LESS_MOTION)];
    for (const query of queries) {
      query.addEventListener('change', follow);
    }
    return () => {
      for (const query of queries) {
        query.removeEventListener('change', follow);
      }
    };
  }, []);

  useEffect(() => {
    if (!playing) {
      return undefined;
    }
    if (index >= last) {
      setPlaying(false);
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setIndex(index + 1);
    }, STEP_MILLISECONDS);
    return () => {
      window.clearTimeout(timer);
    };
  }, [playing, index, last]);

  const go = (target: number): void => {
    setPlaying(false);
    setIndex(target);
  };
  const choose = (chosenView: View): void => {
    chosen.current = true;
    setView(chosenView);
  };
  const play = (): void => {
    if (playing) {
      setPlaying(false);
      return;
    }
    if (index >= last) {
      setIndex(0);
    }
    setPlaying(true);
  };

  return (
    <section className={styles.walk} aria-label={label}>
      <div className={styles.toolbar}>
        <div className={styles.controls}>
          <button type="button" className={styles.button} onClick={() => {
            go(index - 1);
          }} disabled={index === 0}>Back</button>
          {steps.length > 1 ? <button type="button" className={styles.button} onClick={play} aria-pressed={playing}>{playing ? 'Pause' : 'Play'}</button> : null}
          <button type="button" className={styles.button} onClick={() => {
            go(index + 1);
          }} disabled={index === last}>Next</button>
        </div>
        <div className={styles.views} role="group" aria-label="View">
          <button type="button" className={styles.button} aria-pressed={view === 'side-by-side'} onClick={() => {
            choose('side-by-side');
          }}>Side by side</button>
          <button type="button" className={styles.button} aria-pressed={view === 'one-pane'} onClick={() => {
            choose('one-pane');
          }}>One pane</button>
        </div>
      </div>
      <p className={styles.caption} aria-live="polite">
        <strong>{`Step ${index + 1} of ${steps.length}. `}</strong>
        {withCode(step.caption)}
      </p>
      {view === 'side-by-side'
        ? (
            <div className={styles.sideBySide}>
              <Pane title={step.before.file} lines={lines.before} appearing={false} />
              <Pane title={step.after.file} lines={lines.after} appearing={false} />
            </div>
          )
        : <Pane key={index} title={step.after.file} lines={lines.unified} appearing={!lessMotion} />}
    </section>
  );
}

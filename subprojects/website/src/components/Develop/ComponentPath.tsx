/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Link from '@docusaurus/Link';
import clsx from 'clsx';

import styles from './develop.module.css';

interface PathBox {
  id: string;
  title: string;
  line: string;
  section: string;
  kind: 'artifact' | 'component' | 'external';
}

const PATH: PathBox[] = [
  {
    id: 'model',
    title: 'Model',
    line: 'in its own language',
    section: 'frontends',
    kind: 'artifact',
  },
  {
    id: 'frontend',
    title: 'Frontend',
    line: 'translates it onto a semantic library',
    section: 'frontends',
    kind: 'component',
  },
  {
    id: 'oxsts-model',
    title: 'Semantifyr model',
    line: 'on the semantic library',
    section: 'compiler',
    kind: 'artifact',
  },
  {
    id: 'compiler',
    title: 'Compiler',
    line: 'instantiation, inlining, flattening',
    section: 'compiler',
    kind: 'component',
  },
  {
    id: 'transition-system',
    title: 'Transition system',
    line: 'variables, two transitions, a property',
    section: 'compiler',
    kind: 'artifact',
  },
  {
    id: 'portfolio',
    title: 'Portfolio',
    line: 'backends side by side, one verdict',
    section: 'portfolio',
    kind: 'component',
  },
  {
    id: 'verdict',
    title: 'Verdict and evidence',
    line: 'the states a backend found',
    section: 'portfolio',
    kind: 'artifact',
  },
  {
    id: 'verifier',
    title: 'Verifier',
    line: 'makes the evidence a report and a witness',
    section: 'verifier',
    kind: 'component',
  },
  {
    id: 'trace',
    title: 'Trace',
    line: 'made by the frontend, in the model\'s language',
    section: 'frontends',
    kind: 'artifact',
  },
];

const BACKENDS: PathBox = {
  id: 'backends',
  title: 'Backends',
  line: 'one per model checker',
  section: 'backends',
  kind: 'component',
};

const MODEL_CHECKER: PathBox = {
  id: 'model-checker',
  title: 'Model checker',
  line: 'outside Semantifyr',
  section: 'backends',
  kind: 'external',
};

const AROUND: PathBox[] = [
  {
    id: 'launchers',
    title: 'Launchers',
    line: 'the command line',
    section: 'what-runs-the-path',
    kind: 'component',
  },
  {
    id: 'editors',
    title: 'Language servers',
    line: 'and an editor extension',
    section: 'what-runs-the-path',
    kind: 'component',
  },
  {
    id: 'live',
    title: 'Live',
    line: 'a server and a browser application',
    section: 'what-runs-the-path',
    kind: 'component',
  },
];

function Arrow({ direction }: { direction: 'right' | 'left' | 'down' }): React.ReactNode {
  return (
    <svg className={clsx(styles.arrow, styles[direction])} viewBox="0 0 24 12" aria-hidden="true">
      <path d="M 1 6 H 21 M 15 1 L 22 6 L 15 11" />
    </svg>
  );
}

function Exchange(): React.ReactNode {
  return (
    <svg className={styles.exchange} viewBox="0 0 24 12" aria-hidden="true">
      <path d="M 3 6 H 21 M 7 1 L 2 6 L 7 11 M 17 1 L 22 6 L 17 11" />
    </svg>
  );
}

function Box({ box, arrow, down, placed }: { box: PathBox; arrow?: 'right' | 'left' | undefined; down: boolean; placed: boolean }): React.ReactNode {
  return (
    <Link to={`#${box.section}`} className={clsx(styles.box, styles[box.kind])} {...(placed ? { style: { gridArea: box.id } } : {})}>
      <strong className={styles.title}>{box.title}</strong>
      <span className={styles.line}>{box.line}</span>
      {arrow === undefined ? null : <Arrow direction={arrow} />}
      {down ? <Arrow direction="down" /> : null}
    </Link>
  );
}

function Station({ portfolio }: { portfolio: PathBox }): React.ReactNode {
  return (
    <div className={styles.station} style={{ gridArea: portfolio.id }}>
      <div className={styles.frame} />
      <Link to={`#${portfolio.section}`} className={clsx(styles.frameTitle)}>
        <strong className={styles.title}>{portfolio.title}</strong>
        <span className={styles.line}>{portfolio.line}</span>
      </Link>
      <Link to={`#${BACKENDS.section}`} className={clsx(styles.box, styles[BACKENDS.kind], styles.stack)}>
        <strong className={styles.title}>{BACKENDS.title}</strong>
        <span className={styles.line}>{BACKENDS.line}</span>
      </Link>
      <Exchange />
      <Link to={`#${MODEL_CHECKER.section}`} className={clsx(styles.box, styles[MODEL_CHECKER.kind], styles.checker)}>
        <strong className={styles.title}>{MODEL_CHECKER.title}</strong>
        <span className={styles.line}>{MODEL_CHECKER.line}</span>
      </Link>
      <Arrow direction="down" />
    </div>
  );
}

function arrowOf(index: number, turn: number): 'right' | 'left' | undefined {
  if (index < turn) {
    return 'right';
  }
  if (index > turn) {
    return 'left';
  }
  return undefined;
}

// the boxes stand in the path's order, the order a phone and a screen reader take
export default function ComponentPath(): React.JSX.Element {
  const turn = PATH.findIndex((box) => {
    return box.id === 'portfolio';
  });
  return (
    <figure className={styles.path} aria-label="The path of a model through Semantifyr">
      <div className={styles.grid}>
        {PATH.map((box, index) => {
          return [
            box.id === 'oxsts-model'
              ? (
                  <div key="entry" className={styles.entry} style={{ gridArea: 'entry' }}>
                    <span>or a Semantifyr model, written directly</span>
                    <Arrow direction="down" />
                  </div>
                )
              : null,
            box.id === 'portfolio'
              ? <Station key={box.id} portfolio={box} />
              : <Box key={box.id} box={box} arrow={arrowOf(index, turn)} down={index < PATH.length - 1} placed />,
          ];
        })}
      </div>
      <div className={styles.around}>
        <span className={styles.aroundTitle}>What runs the path</span>
        <div className={styles.aroundBoxes}>
          {AROUND.map((box) => {
            return <Box key={box.id} box={box} down={false} placed={false} />;
          })}
        </div>
      </div>
    </figure>
  );
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import clsx from 'clsx';

import styles from './develop.module.css';

export interface PathBox {
  id: string;
  title: string;
  line: string;
  // the section under the picture that describes the box
  section: string;
  kind: 'artifact' | 'component';
}

// in the order a model takes through them; the picture turns at the portfolio and comes back below
export const PATH: PathBox[] = [
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
    title: 'OXSTS model',
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
    line: 'backends side by side',
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
    line: 'makes the evidence a witness in OXSTS',
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

// what runs the path
export const AROUND: PathBox[] = [
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

function Box({ box, arrow, down, placed }: { box: PathBox; arrow?: 'right' | 'left' | undefined; down: boolean; placed: boolean }): React.ReactNode {
  return (
    <a href={`#${box.section}`} className={clsx(styles.box, styles[box.kind])} style={placed ? { gridArea: box.id } : undefined}>
      <strong className={styles.title}>{box.title}</strong>
      <span className={styles.line}>{box.line}</span>
      {arrow === undefined ? null : <Arrow direction={arrow} />}
      {down ? <Arrow direction="down" /> : null}
    </a>
  );
}

// the upper row runs to the portfolio, the lower one back towards the model
function arrowOf(index: number, turn: number): 'right' | 'left' | undefined {
  if (index < turn) {
    return 'right';
  }
  if (index > turn) {
    return 'left';
  }
  return undefined;
}

// the boxes stand in the path's order, which is how a phone shows them and a reader without the picture reads them
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
                    <span>or an OXSTS model, written directly</span>
                    <Arrow direction="down" />
                  </div>
                )
              : null,
            <Box key={box.id} box={box} arrow={arrowOf(index, turn)} down={index < PATH.length - 1} placed />,
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

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import clsx from 'clsx';

import styles from './develop.module.css';

export interface PathBox {
  // the anchor of the box's section under the picture
  id: string;
  title: string;
  line: string;
  modules: string[];
  kind: 'artifact' | 'component';
}

// in the order a model takes through them; the picture turns at the portfolio and comes back below
export const PATH: PathBox[] = [
  {
    id: 'model',
    title: 'Model',
    line: 'in SysML v2 or Gamma',
    modules: [
      ':sysml-wrapper',
      ':gamma.lang',
    ],
    kind: 'artifact',
  },
  {
    id: 'frontend',
    title: 'Frontend',
    line: 'translates it onto its semantic library',
    modules: [
      ':sysml-frontend',
      ':sysml-wrapper',
      ':gamma-frontend',
    ],
    kind: 'component',
  },
  {
    id: 'oxsts-model',
    title: 'OXSTS model',
    line: 'on the semantic library',
    modules: [
      ':oxsts.model',
      ':oxsts.lang',
    ],
    kind: 'artifact',
  },
  {
    id: 'compiler',
    title: 'Compiler',
    line: 'instantiation, inlining, flattening',
    modules: [
      ':compiler',
    ],
    kind: 'component',
  },
  {
    id: 'transition-system',
    title: 'Transition system',
    line: 'variables, two transitions, a property',
    modules: [
      ':oxsts.model',
    ],
    kind: 'artifact',
  },
  {
    id: 'portfolio',
    title: 'Portfolio of backends',
    line: 'Theta, Spin, nuXmv, Uppaal',
    modules: [
      ':portfolios',
      ':backend',
      ':theta-backend',
      ':theta-executor',
      ':xsts.lang',
      ':cex.lang',
      ':spin-backend',
      ':spin-executor',
      ':nuxmv-backend',
      ':nuxmv-executor',
      ':uppaal-backend',
      ':uppaal-executor',
    ],
    kind: 'component',
  },
  {
    id: 'verdict',
    title: 'Verdict and evidence',
    line: 'the states a backend found',
    modules: [
      ':backend',
    ],
    kind: 'artifact',
  },
  {
    id: 'verifier',
    title: 'Verifier',
    line: 'makes the witness in OXSTS and validates it',
    modules: [
      ':verifier',
    ],
    kind: 'component',
  },
  {
    id: 'trace-generator',
    title: 'Trace generator',
    line: 'of the frontend',
    modules: [
      ':gamma-frontend',
      ':sysml-wrapper',
    ],
    kind: 'component',
  },
  {
    id: 'trace',
    title: 'Trace',
    line: 'in the model\'s language',
    modules: [
      ':gamma.lang',
      ':sysml-wrapper',
    ],
    kind: 'artifact',
  },
];

// what runs the path
export const AROUND: PathBox[] = [
  {
    id: 'launchers',
    title: 'Launchers',
    line: 'semantifyr, semantifyr-sysml, semantifyr-gamma',
    modules: [
      ':cli-common',
      ':semantifyr-cli',
      ':sysml-cli',
      ':gamma-cli',
    ],
    kind: 'component',
  },
  {
    id: 'editors',
    title: 'Language servers and VS Code',
    line: 'for OXSTS and Gamma',
    modules: [
      ':lang-ide-common',
      ':oxsts.lang.ide',
      ':gamma.lang.ide',
      ':semantifyr-editor-common',
      ':semantifyr-vscode',
      ':semantifyr-vscode-server',
    ],
    kind: 'component',
  },
  {
    id: 'live',
    title: 'Live',
    line: 'a server and a browser frontend',
    modules: [
      ':semantifyr-live-backend',
      ':semantifyr-live-frontend',
    ],
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
    <a href={`#${box.id}`} className={clsx(styles.box, styles[box.kind])} style={placed ? { gridArea: box.id } : undefined}>
      <strong className={styles.title}>{box.title}</strong>
      <span className={styles.line}>{box.line}</span>
      <span className={styles.modules}>
        {box.modules.map((module) => {
          return <code key={module}>{module}</code>;
        })}
      </span>
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

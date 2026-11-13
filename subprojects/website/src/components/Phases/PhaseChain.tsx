/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './pictures.module.css';

interface Box {
  title: string;
  lines: string[];
  page?: string;
}

const BOXES: Box[] = [
  {
    title: 'The model',
    lines: [
      'its classes and',
      'its case',
    ],
  },
  {
    title: 'Instantiation',
    lines: [
      'builds the',
      'instance tree',
    ],
    page: 'instantiation',
  },
  {
    title: 'Inlining',
    lines: [
      'expands every call',
      'into one transition',
    ],
    page: 'inlining',
  },
  {
    title: 'Flattening',
    lines: [
      'one variable per',
      'instance and variable',
    ],
    page: 'flattening',
  },
  {
    title: 'A transition system',
    lines: [
      'what a backend',
      'checks',
    ],
  },
];

const BOX_WIDTH = 168;
const BOX_HEIGHT = 92;
const GAP = 34;

function BoxShape({ box, x, y }: { box: Box, x: number, y: number }): React.JSX.Element {
  const phase = box.page !== undefined;
  return (
    <g className={phase ? styles.phase : styles.end}>
      <rect x={x} y={y} width={BOX_WIDTH} height={BOX_HEIGHT} rx={8} />
      <text x={x + BOX_WIDTH / 2} y={y + 32} className={styles.boxTitle}>{box.title}</text>
      {box.lines.map((line, index) => {
        return <text key={index} x={x + BOX_WIDTH / 2} y={y + 56 + index * 18} className={styles.boxLine}>{line}</text>;
      })}
    </g>
  );
}

function Linked({ box, base, children }: { box: Box, base: string, children: React.ReactNode }): React.JSX.Element {
  if (box.page === undefined) {
    return <>{children}</>;
  }
  return <a href={`${base}${box.page}/`} aria-label={`${box.title}, its page`}>{children}</a>;
}

function Drawing({ vertical, base }: { vertical: boolean, base: string }): React.JSX.Element {
  const step = (vertical ? BOX_HEIGHT : BOX_WIDTH) + GAP;
  const width = vertical ? BOX_WIDTH + 8 : BOXES.length * step - GAP + 8;
  const height = vertical ? BOXES.length * step - GAP + 8 : BOX_HEIGHT + 8;
  return (
    <svg viewBox={`0 0 ${width} ${height}`} className={vertical ? styles.vertical : styles.horizontal} role="group" aria-label="The compilation: instantiation, inlining and flattening, in order">
      <defs>
        <marker id={vertical ? 'chain-arrow-vertical' : 'chain-arrow'} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <path d="M0,0 L10,5 L0,10 z" className={styles.arrowHead} />
        </marker>
      </defs>
      {BOXES.map((box, index) => {
        const x = vertical ? 4 : 4 + index * step;
        const y = vertical ? 4 + index * step : 4;
        return (
          <g key={box.title}>
            {index > 0
              ? (
                  <line
                    x1={vertical ? x + BOX_WIDTH / 2 : x - GAP + 2}
                    y1={vertical ? y - GAP + 2 : y + BOX_HEIGHT / 2}
                    x2={vertical ? x + BOX_WIDTH / 2 : x - 3}
                    y2={vertical ? y - 3 : y + BOX_HEIGHT / 2}
                    className={styles.arrow}
                    markerEnd={`url(#${vertical ? 'chain-arrow-vertical' : 'chain-arrow'})`}
                  />
                )
              : null}
            <Linked box={box} base={base}>
              <BoxShape box={box} x={x} y={y} />
            </Linked>
          </g>
        );
      })}
    </svg>
  );
}

// the three phases in order, each leading to its page; drawn across a wide column and down a narrow one
export default function PhaseChain(): React.JSX.Element {
  const base = useBaseUrl('/learn/how-it-works/');
  return (
    <figure className={styles.chain}>
      <Drawing vertical={false} base={base} />
      <Drawing vertical base={base} />
    </figure>
  );
}

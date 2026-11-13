/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import styles from './pictures.module.css';

interface Node {
  order: number;
  path: string;
  type: string;
  variable?: string;
  x: number;
  y: number;
}

// the instance tree of oxsts-test-models/phases/room.oxsts, drawn by hand since the compiler keeps the tree in memory; the numbers are the order in which instantiation reaches the instances
const NODES: Node[] = [
  {
    order: 1,
    path: 'the model',
    type: 'inlined oxsts',
    x: 150,
    y: 8,
  },
  {
    order: 2,
    path: 'root',
    type: 'BothLampsCanBeOn',
    x: 150,
    y: 118,
  },
  {
    order: 3,
    path: 'root.left',
    type: 'Lamp',
    variable: 'var on: bool',
    x: 20,
    y: 248,
  },
  {
    order: 4,
    path: 'root.right',
    type: 'Lamp',
    variable: 'var on: bool',
    x: 280,
    y: 248,
  },
];

const EDGES: { from: number, to: number, feature: string }[] = [
  {
    from: 0,
    to: 1,
    feature: 'contains root',
  },
  {
    from: 1,
    to: 2,
    feature: 'contains left',
  },
  {
    from: 1,
    to: 3,
    feature: 'contains right',
  },
];

const WIDTH = 200;
const HEIGHT = 74;
const TALL = 96;

function heightOf(node: Node): number {
  return node.variable === undefined ? HEIGHT : TALL;
}

export default function InstanceTree(): React.JSX.Element {
  return (
    <figure className={styles.tree}>
      <svg viewBox="0 0 500 356" role="img" aria-label="The instance tree: the model contains root, an instance of BothLampsCanBeOn, which contains root.left and root.right, two instances of Lamp, each with its variable on">
        {EDGES.map((edge) => {
          const from = NODES[edge.from];
          const to = NODES[edge.to];
          if (from === undefined || to === undefined) {
            throw new Error('An edge of the instance tree names no node');
          }
          const x1 = from.x + WIDTH / 2;
          const y1 = from.y + heightOf(from);
          const x2 = to.x + WIDTH / 2;
          const y2 = to.y;
          return (
            <g key={edge.feature}>
              <line x1={x1} y1={y1} x2={x2} y2={y2} className={styles.edge} />
              <text x={x2 + (x1 - x2) * 0.3 + (x2 < x1 ? -10 : 10)} y={y2 + (y1 - y2) * 0.3 + 4} className={x2 < x1 ? styles.edgeLabelLeft : styles.edgeLabelRight}>{edge.feature}</text>
            </g>
          );
        })}
        {NODES.map((node) => {
          return (
            <g key={node.path} className={node.order === 1 ? styles.end : styles.instance}>
              <rect x={node.x} y={node.y} width={WIDTH} height={heightOf(node)} rx={8} />
              <circle cx={node.x + 18} cy={node.y + 18} r={11} className={styles.orderDisc} />
              <text x={node.x + 18} y={node.y + 23} className={styles.order}>{node.order}</text>
              <text x={node.x + WIDTH / 2} y={node.y + 30} className={styles.boxTitle}>{node.path}</text>
              <text x={node.x + WIDTH / 2} y={node.y + 54} className={styles.boxLine}>{node.type}</text>
              {node.variable === undefined ? null : <text x={node.x + WIDTH / 2} y={node.y + 80} className={styles.variable}>{node.variable}</text>}
            </g>
          );
        })}
      </svg>
    </figure>
  );
}

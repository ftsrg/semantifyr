/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import styles from './pictures.module.css';

export interface Node {
  order: number;
  path: string;
  type: string;
  x: number;
  y: number;
}

export interface Edge {
  from: number;
  to: number;
  feature: string;
  // the class whose redefinition of the containment chose the type of the instance
  redefinedIn?: string;
}

// the instance tree of oxsts-test-models/phases/room.oxsts, drawn by hand since the compiler keeps the tree in memory; the numbers are the order in which instantiation reaches the instances
export const NODES: Node[] = [
  {
    order: 1,
    path: 'the model',
    type: 'inlined oxsts',
    x: 135,
    y: 8,
  },
  {
    order: 2,
    path: 'root',
    type: 'BothLampsCanBeOn',
    x: 135,
    y: 118,
  },
  {
    order: 3,
    path: 'root.hall',
    type: 'Hall',
    x: 135,
    y: 228,
  },
  {
    order: 4,
    path: 'root.hall.top',
    type: 'Lamp',
    x: 5,
    y: 392,
  },
  {
    order: 5,
    path: 'root.hall.bottom',
    type: 'Spot',
    x: 265,
    y: 392,
  },
  {
    order: 6,
    path: 'root.hall.top.fuse',
    type: 'Fuse',
    x: 135,
    y: 556,
  },
];

// in the order instantiation places the instances
export const EDGES: Edge[] = [
  {
    from: 0,
    to: 1,
    feature: 'contains root',
  },
  {
    from: 1,
    to: 2,
    feature: 'contains hall',
  },
  {
    from: 2,
    to: 3,
    feature: 'contains top',
  },
  {
    from: 2,
    to: 4,
    feature: 'contains bottom',
    redefinedIn: 'Hall',
  },
  {
    from: 3,
    to: 5,
    feature: 'contains fuse',
  },
  {
    from: 4,
    to: 5,
    feature: 'contains fuse',
  },
];

const WIDTH = 230;
const HEIGHT = 74;
const CENTER = 250;
// the middle of a box right of its order's disc, so that a long path keeps clear of it
const TEXT_CENTER = 15 + WIDTH / 2;

export function nodeAt(index: number): Node {
  const node = NODES[index];
  if (node === undefined) {
    throw new Error('An edge of the instance tree names no node');
  }
  return node;
}

export default function InstanceTree(): React.JSX.Element {
  return (
    <figure className={styles.tree}>
      <svg viewBox="0 0 500 640" role="img" aria-label="The instance tree: the model contains root, an instance of BothLampsCanBeOn, which contains root.hall, a Hall; root.hall contains root.hall.top, a Lamp, and root.hall.bottom, a Spot; both lamps hold one shared fuse, root.hall.top.fuse, a Fuse">
        {EDGES.map((edge) => {
          const from = nodeAt(edge.from);
          const to = nodeAt(edge.to);
          const x1 = from.x + WIDTH / 2;
          const y1 = from.y + HEIGHT;
          const x2 = to.x + WIDTH / 2;
          const y2 = to.y;
          const labelX = x2 + (x1 - x2) * 0.3;
          // a label stands on the side of the edge away from the middle, so that the two edges into the shared fuse keep theirs apart
          const left = labelX < CENTER;
          return (
            <g key={`${edge.from} ${edge.to}`}>
              <line x1={x1} y1={y1} x2={x2} y2={y2} className={styles.edge} />
              <text x={labelX + (left ? -14 : 14)} y={y2 + (y1 - y2) * 0.3 + 4} className={left ? styles.edgeLabelLeft : styles.edgeLabelRight}>{edge.feature}</text>
            </g>
          );
        })}
        {NODES.map((node) => {
          return (
            <g key={node.path} className={node.order === 1 ? styles.end : styles.instance}>
              <rect x={node.x} y={node.y} width={WIDTH} height={HEIGHT} rx={8} />
              <circle cx={node.x + 18} cy={node.y + 18} r={11} className={styles.orderDisc} />
              <text x={node.x + 18} y={node.y + 23} className={styles.order}>{node.order}</text>
              <text x={node.x + TEXT_CENTER} y={node.y + 30} className={styles.boxTitle}>{node.path}</text>
              <text x={node.x + TEXT_CENTER} y={node.y + 54} className={styles.boxLine}>{node.type}</text>
            </g>
          );
        })}
      </svg>
    </figure>
  );
}

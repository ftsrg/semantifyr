/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useState } from 'react';
import clsx from 'clsx';

import styles from './develop.module.css';

export interface BuildModule {
  path: string;
  directory: string;
  dependencies: string[];
}

// written by the moduleGraph Gradle task
export interface ModuleGraphFile {
  modules: BuildModule[];
}

export interface PlacedModule {
  module: BuildModule;
  group: string;
  layer: number;
  x: number;
  y: number;
  width: number;
}

export interface GroupFrame {
  label: string;
  x: number;
  y: number;
  width: number;
}

export interface ModuleLayout {
  modules: PlacedModule[];
  frames: GroupFrame[];
  width: number;
  height: number;
}

const CHARACTER_WIDTH = 7.9;
const NODE_PADDING = 18;
const NODE_HEIGHT = 28;
const LABEL_HEIGHT = 16;
const ROW_HEIGHT = 92;
const GAP = 14;
const GROUP_GAP = 30;
const FRAME_PADDING = 6;
const MARGIN = 10;
const LABEL_CHARACTER_WIDTH = 6.8;

// the folder that holds the module's folder, which is how the repository groups its modules
export function groupOf(module: BuildModule): string {
  return module.directory.split('/').slice(0, -1).join('/');
}

function depth(group: string): number {
  return group.split('/').length;
}

function layersOf(modules: BuildModule[]): Map<string, number> {
  const byPath = new Map(modules.map((module) => {
    return [module.path, module];
  }));
  const layers = new Map<string, number>();
  const visiting = new Set<string>();
  const layerOf = (path: string): number => {
    const known = layers.get(path);
    if (known !== undefined) {
      return known;
    }
    const module = byPath.get(path);
    if (module === undefined) {
      throw new Error(`The module graph names ${path}, which is no module of it`);
    }
    if (visiting.has(path)) {
      throw new Error(`The module graph has a cycle through ${path}`);
    }
    visiting.add(path);
    const layer = module.dependencies.reduce((highest, dependency) => {
      return Math.max(highest, layerOf(dependency) + 1);
    }, 0);
    visiting.delete(path);
    layers.set(path, layer);
    return layer;
  };
  for (const module of modules) {
    layerOf(module.path);
  }
  return layers;
}

function mean(values: number[]): number {
  return values.reduce((sum, value) => {
    return sum + value;
  }, 0) / values.length;
}

// a module stands a row above the highest module it depends on; a row is ordered by where its modules' dependencies stand, a group's modules side by side
export function layoutModules(graph: ModuleGraphFile): ModuleLayout {
  const layers = layersOf(graph.modules);
  const top = Math.max(...layers.values());
  const shallowest = Math.min(...graph.modules.map((module) => {
    return depth(groupOf(module));
  }));
  const centers = new Map<string, number>();
  const placed: PlacedModule[] = [];
  const frames: GroupFrame[] = [];
  for (let layer = 0; layer <= top; layer++) {
    const row = graph.modules.filter((module) => {
      return layers.get(module.path) === layer;
    });
    const barycenters = new Map(row.map((module, index) => {
      const below = module.dependencies.map((dependency) => {
        return centers.get(dependency) ?? 0;
      });
      return [module.path, below.length === 0 ? index : mean(below)];
    }));
    const groups = [...new Set(row.map(groupOf))].map((group) => {
      const members = row.filter((module) => {
        return groupOf(module) === group;
      }).sort((left, right) => {
        return (barycenters.get(left.path) ?? 0) - (barycenters.get(right.path) ?? 0) || left.path.localeCompare(right.path);
      });
      return {
        group,
        members,
        key: mean(members.map((module) => {
          return barycenters.get(module.path) ?? 0;
        })),
      };
    }).sort((left, right) => {
      return left.key - right.key || left.group.localeCompare(right.group);
    });
    const y = (top - layer) * ROW_HEIGHT + LABEL_HEIGHT;
    let x = 0;
    const rowPlaced: PlacedModule[] = [];
    const rowFrames: GroupFrame[] = [];
    groups.forEach(({ group, members }, index) => {
      if (index > 0) {
        x += GROUP_GAP - GAP;
      }
      const framed = depth(group) > shallowest;
      // a frame's label is its folder below the shallowest one, and a frame is at least as wide as its label
      const label = `${group.split('/').slice(shallowest).join('/')}/`;
      const widths = members.map((module) => {
        return module.path.length * CHARACTER_WIDTH + NODE_PADDING;
      });
      const membersWidth = widths.reduce((sum, width) => {
        return sum + width + GAP;
      }, -GAP);
      const frameWidth = framed ? Math.max(membersWidth, label.length * LABEL_CHARACTER_WIDTH + 6) : membersWidth;
      const start = x;
      x += (frameWidth - membersWidth) / 2;
      members.forEach((module, memberIndex) => {
        const width = widths[memberIndex] ?? 0;
        rowPlaced.push({ module, group, layer, x, y, width });
        x += width + GAP;
      });
      x = start + frameWidth + GAP;
      if (framed) {
        rowFrames.push({ label, x: start, y, width: frameWidth });
      }
    });
    const shift = -(x - GAP) / 2;
    for (const module of rowPlaced) {
      module.x += shift;
      centers.set(module.module.path, module.x + module.width / 2);
      placed.push(module);
    }
    for (const frame of rowFrames) {
      frame.x += shift;
      frames.push(frame);
    }
  }
  const left = Math.min(...placed.map((module) => {
    return module.x;
  })) - FRAME_PADDING - MARGIN;
  const right = Math.max(...placed.map((module) => {
    return module.x + module.width;
  })) + FRAME_PADDING + MARGIN;
  for (const module of placed) {
    module.x -= left;
  }
  for (const frame of frames) {
    frame.x -= left;
  }
  return {
    modules: placed,
    frames,
    width: right - left,
    height: top * ROW_HEIGHT + LABEL_HEIGHT + NODE_HEIGHT + FRAME_PADDING + MARGIN,
  };
}

interface Edge {
  from: string;
  to: string;
  d: string;
}

// an edge leaves its module's bottom and enters its dependency's top at a point spread by where the other end stands, so that edges do not meet in one point
function edgesOf(layout: ModuleLayout): Edge[] {
  const byPath = new Map(layout.modules.map((module) => {
    return [module.module.path, module];
  }));
  const centerOf = (path: string): number => {
    const module = byPath.get(path);
    return module === undefined ? 0 : module.x + module.width / 2;
  };
  const spread = (module: PlacedModule, others: string[], other: string): number => {
    const sorted = [...others].sort((left, right) => {
      return centerOf(left) - centerOf(right);
    });
    const usable = module.width - 2 * NODE_PADDING / 3;
    return module.x + NODE_PADDING / 3 + usable * (sorted.indexOf(other) + 1) / (sorted.length + 1);
  };
  const dependents = new Map<string, string[]>();
  for (const module of layout.modules) {
    for (const dependency of module.module.dependencies) {
      dependents.set(dependency, [...dependents.get(dependency) ?? [], module.module.path]);
    }
  }
  return layout.modules.flatMap((from) => {
    return from.module.dependencies.map((dependency) => {
      const to = byPath.get(dependency);
      if (to === undefined) {
        throw new Error(`The module graph names ${dependency}, which is no module of it`);
      }
      const x1 = spread(from, from.module.dependencies, dependency);
      const y1 = from.y + NODE_HEIGHT;
      const x2 = spread(to, dependents.get(dependency) ?? [], from.module.path);
      const y2 = to.y;
      const bend = Math.min(ROW_HEIGHT * 0.8, (y2 - y1) / 2);
      return {
        from: from.module.path,
        to: dependency,
        d: `M ${x1.toFixed(1)} ${y1} C ${x1.toFixed(1)} ${y1 + bend} ${x2.toFixed(1)} ${y2 - bend} ${x2.toFixed(1)} ${y2}`,
      };
    });
  });
}

export default function ModuleGraph({ graph }: { graph: ModuleGraphFile }): React.JSX.Element {
  const [lit, setLit] = useState<string | null>(null);
  const layout = layoutModules(graph);
  const edges = edgesOf(layout);
  const neighbours = new Set(edges.filter((edge) => {
    return edge.from === lit || edge.to === lit;
  }).flatMap((edge) => {
    return [edge.from, edge.to];
  }));
  return (
    <figure className={styles.graph}>
      <svg
        className={clsx(lit !== null && styles.faded)}
        width={layout.width}
        height={layout.height}
        viewBox={`0 0 ${layout.width.toFixed(1)} ${String(layout.height)}`}
        role="img"
        aria-label="The modules of the build, each above the modules it depends on; the list under the picture says the same in words"
      >
        {layout.frames.map((frame) => {
          return (
            <g key={`${frame.label} ${String(frame.y)} ${frame.x.toFixed(1)}`}>
              <rect className={styles.groupFrame} x={frame.x - FRAME_PADDING} y={frame.y - LABEL_HEIGHT} width={frame.width + 2 * FRAME_PADDING} height={NODE_HEIGHT + LABEL_HEIGHT + FRAME_PADDING} rx={6} />
              <text className={styles.groupLabel} x={frame.x - FRAME_PADDING + 6} y={frame.y - 5}>{frame.label}</text>
            </g>
          );
        })}
        {edges.map((edge) => {
          return <path key={`${edge.from} ${edge.to}`} data-dependency={`${edge.from} ${edge.to}`} className={clsx(styles.dependency, (edge.from === lit || edge.to === lit) && styles.lit)} d={edge.d} />;
        })}
        {layout.modules.map((placed) => {
          const path = placed.module.path;
          return (
            <g
              key={path}
              data-module={path}
              className={clsx(styles.node, (path === lit || neighbours.has(path)) && styles.lit)}
              onMouseEnter={() => {
                setLit(path);
              }}
              onMouseLeave={() => {
                setLit(null);
              }}
            >
              <rect x={placed.x} y={placed.y} width={placed.width} height={NODE_HEIGHT} rx={6} />
              <text x={placed.x + placed.width / 2} y={placed.y + NODE_HEIGHT / 2}>{path}</text>
            </g>
          );
        })}
      </svg>
      <details className={styles.legend}>
        <summary>Every module and the modules it depends on</summary>
        <ul>
          {graph.modules.map((module) => {
            return (
              <li key={module.path}>
                <code>{module.path}</code>
                {` in ${module.directory}/`}
                {module.dependencies.length === 0 ? ', on no module' : ', on '}
                {module.dependencies.map((dependency, index) => {
                  return (
                    <span key={dependency}>
                      {index > 0 ? ', ' : null}
                      <code>{dependency}</code>
                    </span>
                  );
                })}
              </li>
            );
          })}
        </ul>
      </details>
    </figure>
  );
}

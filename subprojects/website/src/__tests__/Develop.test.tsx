/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment jsdom

import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { AROUND, PATH } from '../components/Develop/ComponentPath';
import ModuleGraph, { layoutModules, type ModuleGraphFile } from '../components/Develop/ModuleGraph';
import { readText } from './files';

// Goal: the architecture page says what the build holds: the module picture draws every module of the generated graph and the fewest edges along which every dependency of it is reached, and a module the page names is a module of the build, so a renamed module fails the build and not the reader

// jsdom replaces the URL that would resolve these beside the test
const HERE = dirname(fileURLToPath(import.meta.url));
const GRAPH = JSON.parse(readText(join(HERE, '../../build/module-graph/modules.json'))) as ModuleGraphFile;
const ARCHITECTURE = readText(join(HERE, '../docs/develop/index.md'));

function edgesOfFile(): [string, string][] {
  return GRAPH.modules.flatMap((module) => {
    return module.dependencies.map((dependency): [string, string] => {
      return [module.path, dependency];
    });
  });
}

function reachable(edges: [string, string][], from: string, to: string): boolean {
  const seen = new Set<string>();
  const pending = [from];
  while (pending.length > 0) {
    const current = pending.pop() ?? '';
    for (const [source, target] of edges) {
      if (source === current && !seen.has(target)) {
        seen.add(target);
        pending.push(target);
      }
    }
  }
  return seen.has(to);
}

afterEach(() => {
  cleanup();
});

describe('the module picture', () => {
  it('draws every module of the generated graph, and of its dependencies exactly those no longer path leads to', () => {
    const { container } = render(<ModuleGraph graph={GRAPH} />);

    const drawnModules = [...container.querySelectorAll('[data-module]')].map((node) => {
      return node.getAttribute('data-module');
    });
    const drawnDependencies = [...container.querySelectorAll('[data-dependency]')].map((edge) => {
      return edge.getAttribute('data-dependency');
    });
    expect(drawnModules.sort()).toEqual(GRAPH.modules.map((module) => {
      return module.path;
    }).sort());
    expect(drawnDependencies.sort()).toEqual(GRAPH.modules.flatMap((module) => {
      return module.dependencies.filter((dependency) => {
        return !reachable(edgesOfFile().filter(([from, to]) => {
          return from !== module.path || to !== dependency;
        }), module.path, dependency);
      }).map((dependency) => {
        return `${module.path} ${dependency}`;
      });
    }).sort());
  });

  it('leads along the drawn edges to every dependency of the generated graph', () => {
    const { container } = render(<ModuleGraph graph={GRAPH} />);
    const drawn = [...container.querySelectorAll('[data-dependency]')].map((edge): [string, string] => {
      const [from = '', to = ''] = (edge.getAttribute('data-dependency') ?? '').split(' ');
      return [from, to];
    });

    expect(edgesOfFile().filter(([from, to]) => {
      return !reachable(drawn, from, to);
    })).toEqual([]);
  });

  it('puts every module a row above each module it depends on', () => {
    const layout = layoutModules(GRAPH);
    const rowOf = new Map(layout.modules.map((placed) => {
      return [placed.module.path, placed.y];
    }));

    for (const module of GRAPH.modules) {
      for (const dependency of module.dependencies) {
        expect(rowOf.get(module.path), `${module.path} stands above ${dependency}`).toBeLessThan(rowOf.get(dependency) ?? 0);
      }
    }
  });
});

describe('the architecture page', () => {
  it('names only modules of the build', () => {
    const modules = new Set(GRAPH.modules.map((module) => {
      return module.path;
    }));
    const named = [...ARCHITECTURE.matchAll(/`(:[\w.-]+)`/g)].map((match) => {
      return match[1] ?? '';
    });

    expect(named.length).toBeGreaterThan(0);
    expect(named.filter((module) => {
      return !modules.has(module);
    })).toEqual([]);
  });

  it('leads every box of the path to its section', () => {
    expect([...PATH, ...AROUND].filter((box) => {
      return !ARCHITECTURE.includes(`{#${box.section}}`);
    }).map((box) => {
      return box.id;
    })).toEqual([]);
  });
});

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

// Goal: Develop's pictures say what the build holds: the module picture draws the generated graph whole and nothing beside it, and a module the path's boxes name is a module of the build, so a renamed module fails the build and not the reader

// jsdom replaces the URL that would resolve these beside the test
const HERE = dirname(fileURLToPath(import.meta.url));
const GRAPH = JSON.parse(readText(join(HERE, '../../build/module-graph/modules.json'))) as ModuleGraphFile;
const ARCHITECTURE = readText(join(HERE, '../docs/develop/index.md'));

afterEach(() => {
  cleanup();
});

describe('the module picture', () => {
  it('draws every module and every dependency of the generated graph, and nothing else', () => {
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
      return module.dependencies.map((dependency) => {
        return `${module.path} ${dependency}`;
      });
    }).sort());
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

describe('the path through the components', () => {
  const boxes = [...PATH, ...AROUND];

  it('names only modules of the build', () => {
    const modules = new Set(GRAPH.modules.map((module) => {
      return module.path;
    }));

    expect(boxes.flatMap((box) => {
      return box.modules;
    }).filter((module) => {
      return !modules.has(module);
    })).toEqual([]);
  });

  it('leads every box to its section on the page', () => {
    expect(boxes.filter((box) => {
      return !ARCHITECTURE.includes(`{#${box.id}}`);
    }).map((box) => {
      return box.id;
    })).toEqual([]);
  });
});

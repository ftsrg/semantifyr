/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// the parts of Learn, which its sidebar and the header's menu are made from
export interface LearnPart {
  label: string;
  // a doc the part opens on, or the address and description of a generated page that lists the part's pages
  opening: { doc: string } | { index: string; description: string };
  // the folder whose pages, but its index, the part holds
  directory?: string;
  parts?: LearnPart[];
}

export const learnParts: LearnPart[] = [
  {
    label: 'Start',
    opening: { doc: 'learn/index' },
    directory: 'learn/start',
  },
  {
    label: 'Frontends',
    opening: { doc: 'learn/frontends/index' },
    parts: [
      {
        label: 'SysML v2',
        opening: { doc: 'learn/frontends/sysml-v2/index' },
        directory: 'learn/frontends/sysml-v2',
      },
      {
        label: 'Gamma',
        opening: { doc: 'learn/frontends/gamma/index' },
        directory: 'learn/frontends/gamma',
      },
    ],
  },
  {
    label: 'Backends',
    opening: { doc: 'learn/backends/index' },
    directory: 'learn/backends',
  },
  {
    label: 'OXSTS',
    opening: { doc: 'learn/language/index' },
    parts: [
      {
        label: 'Language',
        opening: { doc: 'learn/language/specification/index' },
        directory: 'learn/language/specification',
      },
      {
        label: 'Lessons',
        opening: { doc: 'learn/language/lessons/index' },
        directory: 'learn/language/lessons',
      },
      {
        label: 'Compilation',
        opening: { doc: 'learn/language/compilation/index' },
        directory: 'learn/language/compilation',
      },
    ],
  },
  {
    label: 'Reference',
    opening: { index: '/learn/reference', description: 'The command line, the portfolios, the VS Code settings and the artifacts a verification writes.' },
    directory: 'learn/reference',
  },
];

export const sections = [
  {
    label: 'Learn',
    to: '/learn/',
  },
  {
    label: 'Develop',
    to: '/develop/',
  },
  {
    label: 'Publications',
    to: '/publications/',
  },
];

export const SOURCE_CODE = 'https://github.com/ftsrg/semantifyr';

// the docs plugin routes a doc at the site's root, an index at its folder
export function addressOf(part: LearnPart): string {
  if ('index' in part.opening) {
    return `${part.opening.index}/`;
  }
  return `/${part.opening.doc.replace(/(^|\/)index$/, '')}/`;
}

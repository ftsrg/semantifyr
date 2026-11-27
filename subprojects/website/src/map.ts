/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface LearnPart {
  label: string;
  doc: string;
  directory?: string;
  parts?: LearnPart[];
}

export const learnParts: LearnPart[] = [
  {
    label: 'Start',
    doc: 'learn/index',
    directory: 'learn/start',
  },
  {
    label: 'Frontends',
    doc: 'learn/frontends/index',
    parts: [
      {
        label: 'SysML v2',
        doc: 'learn/frontends/sysml-v2/index',
        directory: 'learn/frontends/sysml-v2',
      },
      {
        label: 'Gamma',
        doc: 'learn/frontends/gamma/index',
        directory: 'learn/frontends/gamma',
      },
    ],
  },
  {
    label: 'Backends',
    doc: 'learn/backends/index',
    directory: 'learn/backends',
  },
  {
    label: 'Language',
    doc: 'learn/language/index',
    parts: [
      {
        label: 'Lessons',
        doc: 'learn/language/lessons/index',
        directory: 'learn/language/lessons',
      },
      {
        label: 'Language Reference',
        doc: 'learn/language/reference/index',
        directory: 'learn/language/reference',
      },
      {
        label: 'Compilation',
        doc: 'learn/language/compilation/index',
        directory: 'learn/language/compilation',
      },
    ],
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
  return `/${part.doc.replace(/(^|\/)index$/, '')}/`;
}

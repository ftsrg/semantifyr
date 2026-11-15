/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// the parts of Learn, which its sidebar, its page and the header's menu are made from
export interface LearnPart {
  label: string;
  description: string;
  // a doc the part opens on, or the address of a generated page that lists the part's pages
  opening: { doc: string } | { index: string };
  // the folder whose pages, but its index, the part holds
  directory?: string;
  // the parts the part holds in place of a folder's pages
  parts?: LearnPart[];
}

export const learnParts: LearnPart[] = [
  {
    label: 'Start',
    description: 'What Semantifyr is, how it works at a glance, trying it in the browser and installing it.',
    opening: { doc: 'learn/start/index' },
    directory: 'learn/start',
  },
  {
    label: 'Frontends',
    description: 'The currently implemented frontends, SysML v2 and Gamma, each with what it supports, what it assumes, its command line and its traces.',
    opening: { doc: 'learn/frontends/index' },
    parts: [
      {
        label: 'SysML v2',
        description: 'Verifying SysML v2 models: what of the language the frontend supports, what it assumes, a tutorial, the command line and the traces.',
        opening: { doc: 'learn/frontends/sysml-v2/index' },
        directory: 'learn/frontends/sysml-v2',
      },
      {
        label: 'Gamma',
        description: 'Verifying Gamma statecharts and their compositions: what of the language the frontend supports, what it assumes, the command line and the traces.',
        opening: { doc: 'learn/frontends/gamma/index' },
        directory: 'learn/frontends/gamma',
      },
    ],
  },
  {
    label: 'Verifying',
    description: 'How a case gets its verdict, the witness and the trace behind it, and verifying in Live, in VS Code and on the command line.',
    opening: { doc: 'learn/verifying/index' },
    directory: 'learn/verifying',
  },
  {
    label: 'OXSTS Lessons',
    description: 'Defining semantics yourself in OXSTS, in five levels on a traffic light, from one light to a statechart library.',
    opening: { doc: 'learn/lessons/index' },
    directory: 'learn/lessons',
  },
  {
    label: 'How OXSTS Compiles',
    description: 'How the compiler turns an OXSTS model into the transition system a backend checks, phase by phase.',
    opening: { doc: 'learn/compilation/index' },
    directory: 'learn/compilation',
  },
  {
    label: 'The OXSTS Language',
    description: 'The specification of OXSTS: its foundations, its classes and behaviour, its standard library and what the compiler writes.',
    opening: { doc: 'learn/language/index' },
    directory: 'learn/language',
  },
  {
    label: 'Reference',
    description: 'The command line, the portfolios, the VS Code settings, the backends and the artifacts a verification writes.',
    opening: { index: '/learn/reference' },
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

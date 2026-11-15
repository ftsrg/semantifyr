/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// the parts of Learn, which its sidebar and the header's menu are made from
export interface LearnPart {
  label: string;
  description: string;
  // a doc the part opens on, or the address of a generated page that lists the part's pages
  opening: { doc: string } | { index: string };
  // the folder whose pages, but its index, the part holds
  directory?: string;
  parts?: (LearnPart | LearnPage)[];
}

// a page that stands among a part's parts
export interface LearnPage {
  doc: string;
}

export const learnParts: LearnPart[] = [
  {
    label: 'Start',
    description: 'What Semantifyr is and how it works, verifying in Live and in VS Code, and installing it.',
    opening: { doc: 'learn/index' },
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
    label: 'Backends',
    description: 'What a backend is, how a portfolio decides a verdict from the backends\' answers, the witness behind a verdict, and Theta, nuXmv, Spin and Uppaal.',
    opening: { doc: 'learn/backends/index' },
    directory: 'learn/backends',
  },
  {
    label: 'OXSTS',
    description: 'Semantifyr\'s language for semantics: lessons that teach it, its specification, how it compiles and verifying it on the command line.',
    opening: { doc: 'learn/oxsts/index' },
    parts: [
      {
        label: 'Lessons',
        description: 'Defining semantics yourself in OXSTS, in five levels on a traffic light, from one light to a statechart library.',
        opening: { doc: 'learn/oxsts/lessons/index' },
        directory: 'learn/oxsts/lessons',
      },
      {
        label: 'Language',
        description: 'The specification of OXSTS: its foundations, its classes and behaviour, its standard library and what the compiler writes.',
        opening: { doc: 'learn/oxsts/language/index' },
        directory: 'learn/oxsts/language',
      },
      {
        label: 'Compilation',
        description: 'How the compiler turns an OXSTS model into the transition system a backend checks, phase by phase.',
        opening: { doc: 'learn/oxsts/compilation/index' },
        directory: 'learn/oxsts/compilation',
      },
      {
        doc: 'learn/oxsts/command-line',
      },
    ],
  },
  {
    label: 'Reference',
    description: 'The command line, the portfolios, the VS Code settings and the artifacts a verification writes.',
    opening: { index: '/learn/reference' },
    directory: 'learn/reference',
  },
];

export function isPart(item: LearnPart | LearnPage): item is LearnPart {
  return 'label' in item;
}

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

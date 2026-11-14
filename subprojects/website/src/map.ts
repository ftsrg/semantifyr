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
}

export const learnParts: LearnPart[] = [
  {
    label: 'Start',
    description: 'What Semantifyr is, how it works at a glance, trying it in the browser and installing it.',
    opening: { doc: 'learn/start/index' },
    directory: 'learn/start',
  },
  {
    label: 'SysML v2',
    description: 'Verifying SysML v2 models: what of the language the frontend supports, what it assumes, and a tutorial.',
    opening: { doc: 'learn/sysml-v2/index' },
    directory: 'learn/sysml-v2',
  },
  {
    label: 'Gamma',
    description: 'Verifying Gamma statecharts and their compositions: what of the language the frontend supports and what it assumes.',
    opening: { doc: 'learn/gamma/index' },
    directory: 'learn/gamma',
  },
  {
    label: 'OXSTS Lessons',
    description: 'Defining semantics yourself in OXSTS, in five levels on a traffic light, from one light to a statechart library.',
    opening: { doc: 'learn/lessons/index' },
    directory: 'learn/lessons',
  },
  {
    label: 'How the Language Works',
    description: 'How the compiler turns an OXSTS model into the transition system a backend checks, phase by phase.',
    opening: { doc: 'learn/how-it-works/index' },
    directory: 'learn/how-it-works',
  },
  {
    label: 'Tools',
    description: 'Live, the VS Code extension and the command line, and how a verification reaches its verdict.',
    opening: { index: '/learn/tools' },
    directory: 'learn/tools',
  },
  {
    label: 'Reference',
    description: 'The specification of OXSTS and its standard library, the command line, the editors\' settings, the backends and the artifacts a verification writes.',
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

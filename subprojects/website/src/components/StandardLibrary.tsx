/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { Fragment } from 'react';
import CodeBlock from '@theme/CodeBlock';
import Heading from '@theme/Heading';
import { withCode } from '@site/src/lib/inlineCode';

type Kind = 'package' | 'datatype' | 'class' | 'annotation';

interface TocItem {
  value: string;
  id: string;
  level: number;
}

interface Declaration {
  kind: Kind;
  name: string;
  paragraphs: string[];
  code: string;
}

const DECLARATION: [Kind, RegExp][] = [
  ['package', /^package\s+(\w+)/],
  ['datatype', /^extern\s+datatype\s+(\w+)/],
  ['class', /^(?:abstract\s+)?class\s+(\w+)/],
  ['annotation', /^annotation\s+(\w+)/],
];

const SECTIONS: { kind: Kind; title: string; heading: (name: string) => string }[] = [
  {
    kind: 'class',
    title: 'Classes',
    heading: (name) => {
      return name;
    },
  },
  {
    kind: 'annotation',
    title: 'Annotations',
    heading: (name) => {
      return `@${name}`;
    },
  },
];

function paragraphsOf(comment: string[]): string[] {
  const lines = comment.map((line) => {
    return line.replace(/^\s*\/?\*+\/?\s?/, '').replace(/\s*\*\/\s*$/, '').trimEnd();
  });
  return lines.join('\n').split(/\n\s*\n/).map((paragraph) => {
    return paragraph.split('\n').map((line) => {
      return line.trim();
    }).filter((line) => {
      return line !== '';
    }).join(' ');
  }).filter((paragraph) => {
    return paragraph !== '';
  });
}

// what a declaration shows is its text without the comments, which the page shows as prose
function withoutComments(lines: string[]): string {
  return lines.join('\n').replace(/^[ \t]*\/\*[\s\S]*?\*\/[ \t]*\n/gm, '').replace(/^[ \t]*\/\/.*\n/gm, '').trimEnd();
}

function braceDepthOf(line: string): number {
  const code = line.replace(/\/\/.*$/, '');
  return (code.match(/\{/g)?.length ?? 0) - (code.match(/\}/g)?.length ?? 0);
}

// every top-level declaration of the library carries the comment before it, which the editors' hover also shows
function parseLibrary(library: string): Declaration[] {
  const lines = library.replace(/\r\n/g, '\n').replace(/^\/\*[\s\S]*?SPDX-[\s\S]*?\*\/\s*/, '').split('\n');
  const declarations: Declaration[] = [];
  let comment: string[] | null = null;
  let index = 0;
  while (index < lines.length) {
    const line = lines[index] ?? '';
    if (line.trim() === '') {
      index += 1;
      continue;
    }
    if (line.startsWith('/*')) {
      comment = [];
      while (index < lines.length) {
        const commentLine = lines[index] ?? '';
        comment.push(commentLine);
        index += 1;
        if (commentLine.includes('*/')) {
          break;
        }
      }
      continue;
    }
    let match: { kind: Kind; name: string } | null = null;
    for (const [kind, pattern] of DECLARATION) {
      const name = pattern.exec(line)?.[1];
      if (match === null && name !== undefined) {
        match = { kind, name };
      }
    }
    if (match === null) {
      throw new Error(`semantifyr.oxsts has a line the standard library page cannot place: ${line}`);
    }
    if (comment === null) {
      throw new Error(`semantifyr.oxsts declares ${match.name} without the comment that describes it`);
    }
    const declarationLines = [line];
    let depth = braceDepthOf(line);
    index += 1;
    while (depth > 0 && index < lines.length) {
      const bodyLine = lines[index] ?? '';
      declarationLines.push(bodyLine);
      depth += braceDepthOf(bodyLine);
      index += 1;
    }
    declarations.push({ ...match, paragraphs: paragraphsOf(comment), code: withoutComments(declarationLines) });
    comment = null;
  }
  return declarations;
}

function idOf(heading: string): string {
  return heading.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function Paragraphs({ paragraphs }: { paragraphs: string[] }): React.JSX.Element {
  return (
    <>
      {paragraphs.map((paragraph) => {
        return <p key={paragraph}>{withCode(paragraph)}</p>;
      })}
    </>
  );
}

export function tocOf(library: string): TocItem[] {
  const declarations = parseLibrary(library);
  const items: TocItem[] = [{ value: 'Datatypes', id: 'datatypes', level: 2 }];
  for (const section of SECTIONS) {
    items.push({ value: section.title, id: idOf(section.title), level: 2 });
    for (const declaration of declarations.filter((candidate) => {
      return candidate.kind === section.kind;
    })) {
      const heading = section.heading(declaration.name);
      items.push({ value: `<code>${heading}</code>`, id: idOf(heading), level: 3 });
    }
  }
  return items;
}

export default function StandardLibrary({ library }: { library: string }): React.JSX.Element {
  const declarations = parseLibrary(library);
  const ofKind = (kind: Kind): Declaration[] => {
    return declarations.filter((declaration) => {
      return declaration.kind === kind;
    });
  };
  return (
    <>
      {ofKind('package').map((declaration) => {
        return <Paragraphs key={declaration.name} paragraphs={declaration.paragraphs} />;
      })}
      <Heading as="h2" id="datatypes">
        Datatypes
      </Heading>
      <table>
        <thead>
          <tr>
            <th>Datatype</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          {ofKind('datatype').map((declaration) => {
            return (
              <tr key={declaration.name}>
                <td>
                  <code>{declaration.name}</code>
                </td>
                <td>{withCode(declaration.paragraphs.join(' '))}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {SECTIONS.map((section) => {
        return (
          <Fragment key={section.kind}>
            <Heading as="h2" id={idOf(section.title)}>
              {section.title}
            </Heading>
            {ofKind(section.kind).map((declaration) => {
              const heading = section.heading(declaration.name);
              return (
                <Fragment key={declaration.name}>
                  <Heading as="h3" id={idOf(heading)}>
                    <code>{heading}</code>
                  </Heading>
                  <CodeBlock language="oxsts">{declaration.code}</CodeBlock>
                  <Paragraphs paragraphs={declaration.paragraphs} />
                </Fragment>
              );
            })}
          </Fragment>
        );
      })}
    </>
  );
}

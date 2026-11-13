/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import clsx from 'clsx';
import type ContentType from '@theme/CodeBlock/Content';
import Line from '@theme/CodeBlock/Line';
import { useCodeBlockContext } from '@docusaurus/theme-common/internal';
import type { WrapperProps } from '@docusaurus/types';
import styles from '@docusaurus/theme-classic/lib/theme/CodeBlock/Content/styles.module.css';
import { highlight, styleOf } from '@site/src/lib/highlight';

type Props = WrapperProps<typeof ContentType>;

interface LineToken {
  content: string;
  types: string[];
  style: React.CSSProperties;
}

function linesOf(code: string, language: string | undefined): LineToken[][] {
  const highlighted = language === undefined ? null : highlight(code, language);
  if (highlighted === null) {
    return code.split('\n').map((line) => {
      return [{ content: line, types: ['plain'], style: {} }];
    });
  }
  return highlighted.map((line) => {
    return line.map((token) => {
      return { content: token.text, types: ['plain'], style: styleOf(token.scopes) };
    });
  });
}

// every block is coloured from the grammar live's editor loads for its language, in the tokens' syntax colours, and a language without one is plain
export default function CodeBlockContent({ className }: Props): React.JSX.Element {
  const { metadata, wordWrap } = useCodeBlockContext();
  const { code, language, lineNumbersStart, lineClassNames } = metadata;
  return (
    <pre ref={wordWrap.codeBlockRef} tabIndex={0} className={clsx(className, styles.codeBlock, 'thin-scrollbar')}>
      <code
        className={clsx(styles.codeBlockLines, lineNumbersStart !== undefined && styles.codeBlockLinesWithNumbering)}
        style={lineNumbersStart === undefined ? undefined : { counterReset: `line-count ${lineNumbersStart - 1}` }}
      >
        {linesOf(code, language).map((line, index) => {
          return (
            <Line
              key={index}
              line={line.length === 0 ? [{ content: '', types: ['plain'], style: {} }] : line}
              classNames={lineClassNames[index]}
              showLineNumbers={lineNumbersStart !== undefined}
              getLineProps={({ className: lineClassName }) => {
                return { className: clsx('token-line', lineClassName) };
              }}
              getTokenProps={({ token }) => {
                return { className: 'token', children: token.content, style: (token as LineToken).style };
              }}
            />
          );
        })}
      </code>
    </pre>
  );
}

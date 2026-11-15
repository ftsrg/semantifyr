/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import CodeBlock from '@theme/CodeBlock';
import { selectDeclarations } from '@site/src/components/LiveEmbed';

interface Props {
  library: string;
  name: string;
}

// the page describes the declaration, so its comments are left out
function withoutComments(text: string): string {
  return text.replace(/^[ \t]*\/\*[\s\S]*?\*\/[ \t]*\n/gm, '').replace(/^[ \t]*\/\/.*\n/gm, '');
}

export default function LibraryDeclaration({ library, name }: Props): React.JSX.Element {
  return <CodeBlock language="oxsts">{withoutComments(`${selectDeclarations(library, [name])}\n`).trimEnd()}</CodeBlock>;
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import CodeBlock from '@theme/CodeBlock';
import { declarationOf } from '@site/src/components/StandardLibrary';

export default function LibraryDeclaration({ library, name }: { library: string; name: string }): React.JSX.Element {
  return <CodeBlock language="oxsts">{declarationOf(library, name)}</CodeBlock>;
}

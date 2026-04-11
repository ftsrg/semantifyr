/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

/// <reference types="vite/client" />

declare module '*.oxsts?raw' {
  const content: string;
  export default content;
}

declare module '*.xsts?raw' {
  const content: string;
  export default content;
}

declare module '*.gamma?raw' {
  const content: string;
  export default content;
}

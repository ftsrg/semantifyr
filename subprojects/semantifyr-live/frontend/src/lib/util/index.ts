/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export type { ColorModePreference, ResolvedColorMode } from './colorMode'
export { createSemantifyrTheme } from './theme'
export { resolveBackendUrl } from './backendUrl'
export {
  formatDuration,
  formatIsoDuration,
  formatIsoDurationDetailed,
  isoDurationFromMs,
  isoDurationToMs,
} from './duration'
// `lib/util/persistence` is consumed only via the `usePersistedState` hook + its codecs
// (`stringCodec`/`boolCodec`/`sizeCodec`). The bare read*/persist* helpers live there as an
// implementation detail; routing them through this barrel would invite drift between the two
// front doors, so they are intentionally not re-exported here.
export {
  buildShareableUrl,
  type ShareablePayload,
} from './sharing'

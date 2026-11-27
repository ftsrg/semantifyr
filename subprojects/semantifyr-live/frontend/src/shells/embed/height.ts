/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { LiveHeightMessage, LiveShownMessage } from '@semantifyr/editor-common'

// the height is no secret, so any page may frame the embed; the page checks that the message comes from its frame
export function reportHeight(height: number): void {
  if (window.parent === window) {
    return
  }
  const message: LiveHeightMessage = { type: 'semantifyr-live:height', height }
  window.parent.postMessage(message, '*')
}

// a page that keeps its own text over the frame until the model shows takes this as the sign to swap them
export function reportShown(): void {
  if (window.parent === window) {
    return
  }
  const message: LiveShownMessage = { type: 'semantifyr-live:shown' }
  window.parent.postMessage(message, '*')
}

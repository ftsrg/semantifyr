/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

interface HeightMessage {
  type: 'semantifyr-live:height'
  height: number
}

// the height is no secret, so any page may frame the embed; the page checks that the message comes from its frame
export function reportHeight(height: number): void {
  if (window.parent === window) {
    return
  }
  const message: HeightMessage = { type: 'semantifyr-live:height', height }
  window.parent.postMessage(message, '*')
}

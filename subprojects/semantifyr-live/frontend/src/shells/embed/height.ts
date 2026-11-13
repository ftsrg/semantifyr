/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

interface HeightMessage {
  type: 'semantifyr-live:height'
  height: number
}

interface ShownMessage {
  type: 'semantifyr-live:shown'
}

// the height is no secret, so any page may frame the embed; the page checks that the message comes from its frame
export function reportHeight(height: number): void {
  if (window.parent === window) {
    return
  }
  const message: HeightMessage = { type: 'semantifyr-live:height', height }
  window.parent.postMessage(message, '*')
}

// a page that keeps its own text over the frame until the model shows takes this as the sign to swap them
export function reportShown(): void {
  if (window.parent === window) {
    return
  }
  const message: ShownMessage = { type: 'semantifyr-live:shown' }
  window.parent.postMessage(message, '*')
}

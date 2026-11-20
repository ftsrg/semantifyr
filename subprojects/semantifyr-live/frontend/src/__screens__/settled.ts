/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { expect, vi } from 'vitest'

function movingAnimations(): number {
  return document.getAnimations().filter((animation) => {
    return animation.playState === 'running' && animation.effect?.getComputedTiming().endTime !== Infinity
  }).length
}

export async function settledOn(expectState: () => Promise<void> | void): Promise<void> {
  await document.fonts.ready
  await vi.waitFor(async () => {
    await expectState()
    expect(movingAnimations(), 'animations still moving').toBe(0)
  }, { timeout: 30_000, interval: 50 })
}

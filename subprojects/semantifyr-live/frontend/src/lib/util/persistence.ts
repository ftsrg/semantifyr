/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

function safeRead(key: string): string | null {
  if (typeof window === 'undefined') {
    return null
  }
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

function safeWrite(key: string, value: string): void {
  if (typeof window === 'undefined') {
    return
  }
  try {
    window.localStorage.setItem(key, value)
  } catch {
    /* ignore */
  }
}

export function readPersistedSize(key: string, fallback: number): number {
  const stored = safeRead(key)
  if (stored === null) {
    return fallback
  }
  const parsed = Number.parseInt(stored, 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

export function readPersistedString(key: string, fallback: string): string {
  return safeRead(key) ?? fallback
}

export function readPersistedBool(key: string, fallback: boolean): boolean {
  const stored = safeRead(key)
  if (stored === 'true') {
    return true
  }
  if (stored === 'false') {
    return false
  }
  return fallback
}

export function persistSize(key: string, value: number): void {
  safeWrite(key, String(Math.round(value)))
}

export function persistString(key: string, value: string): void {
  safeWrite(key, value)
}

export function persistBool(key: string, value: boolean): void {
  safeWrite(key, value ? 'true' : 'false')
}

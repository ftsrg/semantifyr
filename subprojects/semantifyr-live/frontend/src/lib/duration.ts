/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { parse as parseIso } from 'iso8601-duration';

export function formatDuration(totalSeconds: number): string {
  if (totalSeconds < 60) return `${totalSeconds}s`;
  const minutes = Math.floor(totalSeconds / 60);
  if (minutes < 60) return `${minutes}m ${totalSeconds % 60}s`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ${minutes % 60}m`;
  const days = Math.floor(hours / 24);
  return `${days}d ${hours % 24}h`;
}

export function formatIsoDuration(iso: string): string {
  let parsed;
  try {
    parsed = parseIso(iso);
  } catch {
    return '0s';
  }
  const seconds = Math.floor(parsed.seconds ?? 0);
  const minutes = parsed.minutes ?? 0;
  const hours = parsed.hours ?? 0;
  const days = (parsed.days ?? 0) + (parsed.weeks ?? 0) * 7;
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m ${seconds}s`;
  return `${seconds}s`;
}

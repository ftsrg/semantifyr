/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { isoDurationToMs } from '@semantifyr/editor-common';

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
  return formatDuration(Math.floor(isoDurationToMs(iso) / 1000));
}

export function formatIsoDurationDetailed(iso: string): string {
  const totalMs = isoDurationToMs(iso);
  if (totalMs < 1000) return `${Math.round(totalMs)} ms`;
  const totalSeconds = totalMs / 1000;
  if (totalSeconds < 60) {
    return `${totalSeconds.toFixed(totalSeconds < 10 ? 2 : 1)} s`;
  }
  return formatIsoDuration(iso);
}

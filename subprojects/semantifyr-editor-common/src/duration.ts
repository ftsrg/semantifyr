/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

const ISO_DURATION = /^P(?:(\d+(?:\.\d+)?)W)?(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$/;

// a duration that does not parse counts as none
export function isoDurationToMs(iso: string): number {
    const match = ISO_DURATION.exec(iso);
    if (match === null) {
        return 0;
    }
    const amount = (index: number): number => {
        return Number(match[index] ?? 0);
    };
    return (amount(1) * 7 * 86400 + amount(2) * 86400 + amount(3) * 3600 + amount(4) * 60 + amount(5)) * 1000;
}

export function formatSeconds(milliseconds: number): string {
    const seconds = milliseconds / 1000;
    return seconds < 10 ? `${seconds.toFixed(1)} s` : `${String(Math.round(seconds))} s`;
}

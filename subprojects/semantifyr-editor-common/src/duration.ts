/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

const AMOUNT = String.raw`(-?\d+(?:\.\d+)?)`;

const ISO_DURATION = new RegExp(`^(-)?P(?:${AMOUNT}W)?(?:${AMOUNT}D)?(?:T(?:${AMOUNT}H)?(?:${AMOUNT}M)?(?:${AMOUNT}S)?)?$`);

export function isoDurationToMs(iso: string): number {
    const match = ISO_DURATION.exec(iso);
    if (match === null) {
        throw new Error(`Not an ISO-8601 duration: ${iso}`);
    }
    const amount = (index: number): number => {
        return Number(match[index] ?? 0);
    };
    const sign = match[1] === undefined ? 1 : -1;
    return sign * (amount(2) * 7 * 86400 + amount(3) * 86400 + amount(4) * 3600 + amount(5) * 60 + amount(6)) * 1000;
}

export function formatSeconds(milliseconds: number): string {
    const seconds = milliseconds / 1000;
    return seconds < 10 ? `${seconds.toFixed(1)} s` : `${String(Math.round(seconds))} s`;
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { WitnessState } from "./wire.ts";

export interface WitnessStateChange {
    variable: string;
    value: string;
    previous: string | undefined;
}

export function witnessStateChanges(state: WitnessState): WitnessStateChange[][] {
    const current = new Map<string, string>();
    return [state.initialStep, state.initializedStep, ...state.steps].map((step) => {
        const changes = (step?.values ?? []).map((value) => {
            return { variable: value.variable, value: value.value, previous: current.get(value.variable) };
        }).filter((change) => change.value !== change.previous);
        for (const change of changes) {
            current.set(change.variable, change.value);
        }
        return changes;
    });
}

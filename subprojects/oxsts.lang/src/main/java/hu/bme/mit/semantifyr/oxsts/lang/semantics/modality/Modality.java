/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.modality;

public enum Modality {
    CONSTANT,
    COMPILE_TIME,
    RUNTIME;

    public Modality leastUpperBound(Modality other) {
        return values()[Math.max(ordinal(), other.ordinal())];
    }

    public boolean isAtMost(Modality upperBound) {
        return ordinal() <= upperBound.ordinal();
    }

    public static Modality leastUpperBoundOf(Modality... modalities) {
        Modality result = CONSTANT;
        for (Modality m : modalities) {
            result = result.leastUpperBound(m);
        }
        return result;
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.inlayhints;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;

final class LampModels {

    private static final String LAMPS = """
            package example::lamps

            annotation Brightness(level: int, label: string)

            class Lamp {
                var level: int := 0

                tran set(level: int, step: int) {
                    self.level := level + step
                }

                prop isAt(level: int): bool {
                    return self.level == level
                }
            }

            """;

    private LampModels() {}

    static MarkedText withGarage(String garage) {
        return MarkedText.of(LAMPS + garage);
    }
}

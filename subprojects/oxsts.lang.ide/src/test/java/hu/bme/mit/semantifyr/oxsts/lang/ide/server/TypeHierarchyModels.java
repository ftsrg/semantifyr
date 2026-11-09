/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;

final class TypeHierarchyModels {

    // Markers: the Lamp class, then its toggle transition.
    static final MarkedText LAMPS_LIBRARY = MarkedText.of("""
            package example::library

            class <|>Lamp {
                var on: bool := false

                tran <|>toggle() {
                    self.on := !self.on
                }
            }
            """);

    // Markers: the DimmableLamp class, the Lamp reference, DimmableLamp's toggle, the SmartLamp class, SmartLamp's
    // toggle.
    static final MarkedText GARAGE = MarkedText.of("""
            package example::garage

            import example::library

            class <|>DimmableLamp : <|>Lamp {
                var dimmed: bool := false

                redefine tran <|>toggle() {
                    self.dimmed := false
                    self.on := !self.on
                }
            }

            class <|>SmartLamp : DimmableLamp {
                redefine tran <|>toggle() {
                    self.on := true
                }
            }
            """);

    private TypeHierarchyModels() {
        throw new IllegalStateException("This is a static utility class and should not be instantiated directly");
    }
}

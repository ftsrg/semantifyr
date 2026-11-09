/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

final class CallHierarchyModels {

    static final String LAMPS_FILE_NAME = "Lamps.oxsts";

    // The marker starts the name of the library transition.
    static final String LAMPS = """
            package example::library

            class Lamp {
                var on: bool := false

                tran <|>toggle() {
                    self.on := true
                }
            }
            """;

    // In source order, the markers start the call in reset, the case's main transition and each call in main.
    static final String GARAGE = """
            package example::garage

            import example::library

            class GarageLamp : Lamp {
                redefine tran toggle() {
                    self.on := false
                }
            }

            @VerificationCase
            class Garage {
                contains lamp: Lamp[1]
                contains garageLamp: GarageLamp[1]

                tran reset() {
                    inline lamp.<|>toggle()
                }

                <|>redefine tran {
                    inline lamp.<|>toggle()
                    inline garageLamp.<|>toggle()
                    inline <|>reset()
                }

                prop {
                    return true
                }
            }
            """;

    private CallHierarchyModels() {
        throw new IllegalStateException("This is a static utility class and should not be instantiated directly");
    }
}

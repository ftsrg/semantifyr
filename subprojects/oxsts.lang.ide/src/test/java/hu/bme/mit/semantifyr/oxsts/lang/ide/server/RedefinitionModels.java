/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

final class RedefinitionModels {

    // Every marker starts a setColor token: the declaration, its redefinition and the calls through either.
    static final String TRAFFIC_LIGHTS = """
            package example::trafficlight

            enum LightColor {
                Red,
                Green
            }

            class TrafficLight {
                var color: LightColor := LightColor::Red

                /*
                 * Switches the light.
                 */
                tran <|>setColor(color: LightColor) {
                    self.color := color
                }

                redefine tran {
                    inline <|>setColor(LightColor::Green)
                }
            }

            class SpecialLight : TrafficLight {
                redefine tran <|>setColor(color: LightColor) {
                    self.color := LightColor::Red
                }
            }

            class Crossing {
                contains light: TrafficLight[1]
                contains special: SpecialLight[1]

                redefine tran {
                    inline light.<|>setColor(LightColor::Red)
                    inline special.<|>setColor(LightColor::Green)
                }
            }
            """;

    private RedefinitionModels() {
        throw new IllegalStateException("This is a static utility class and should not be instantiated directly");
    }
}

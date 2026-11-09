/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

final class TrafficLightFixture {

    static final String LIBRARY_FILE_NAME = "Lights.oxsts";

    static final String LIBRARY = """
            package example::lights

            enum LightColor {
                Red,
                Yellow,
                Green
            }

            /*
             * A traffic light cycling through its colors.
             */
            class TrafficLight {
                var color: LightColor := LightColor::Red

                /*
                 * Switches the light.
                 */
                tran setColor(color: LightColor) {
                    self.color := color
                }

                tran blink(color: LightColor, times: int) {
                    inline setColor(color)
                }

                redefine tran {
                    inline setColor(LightColor::Green)
                }

                prop isColor(color: LightColor): bool {
                    return self.color == color
                }
            }
            """;

    private TrafficLightFixture() {}

    static String crossing(String members) {
        return """
                package example::crossing

                import example::lights

                class Crossing {
                    contains light: TrafficLight[1]

                """ + members.indent(4) + "}\n";
    }
}

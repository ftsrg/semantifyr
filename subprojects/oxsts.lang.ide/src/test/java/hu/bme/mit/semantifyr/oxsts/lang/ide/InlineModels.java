/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

public final class InlineModels {

    public static final String TRAFFIC_LIGHT_FILE_NAME = "Lights.oxsts";

    public static final String TRAFFIC_LIGHT = """
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

            class SpecialLight : TrafficLight {
                redefine tran setColor(color: LightColor) {
                    self.color := LightColor::Red
                }
            }

            class DimmedLight : TrafficLight {
                redefine tran {
                    self.color := LightColor::Yellow
                }
            }

            class Intersection {
                contains light: TrafficLight[1]
                contains special: SpecialLight[1]
                contains dimmed: DimmedLight[1]

                tran cycle() {
                    inline light.setColor(LightColor::Red)
                    inline special.setColor(LightColor::Green)
                    inline light.main()
                    inline dimmed.main()
                }
            }
            """;

    public static final String LAMP_LIBRARY_FILE_NAME = "Lamps.oxsts";

    public static final String LAMP_LIBRARY = """
            package example::library

            annotation Brightness(level: int, label: string)

            enum LightColor {
                Red,
                Green
            }

            class Lamp {
                var on: bool := false
                var level: int := 0

                tran toggle() {
                    self.on := !self.on
                }

                tran set(level: int, step: int) {
                    self.level := level + step
                }

                prop isOn(): bool {
                    return self.on
                }

                prop isAt(level: int): bool {
                    return self.level == level
                }
            }
            """;

    private InlineModels() {}

    public static String crossing(String members) {
        return """
                package example::crossing

                import example::lights

                class Crossing {
                    contains light: TrafficLight[1]

                """ + members.indent(4) + "}\n";
    }
}

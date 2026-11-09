/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

final class QuickFixModels {

    static final String TRAFFIC_LIGHT = """
            package example::trafficlight

            enum LightColor {
                Red,
                Yellow,
                Green
            }

            class TrafficLight {
                var color: LightColor := LightColor::Red

                tran setColor(color: LightColor) {
                    self.color := color
                }
            }
            """;

    static final String SHAPES = """
            package example::shapes

            abstract class Shape {
                abstract tran draw()
            }
            """;

    private QuickFixModels() {}
}

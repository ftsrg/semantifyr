/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public final class TestModels {

    private static final Path GAMMA_LIBRARY = Path.of("../frontends/gamma/models/libraries/default");

    private TestModels() {}

    public static final String TRAFFIC_LIGHTS = """
            package example::trafficlights

            enum LightColor {
                Red,
                Yellow,
                Green
            }

            class TrafficLight {
                var color: LightColor := LightColor::Red

                tran assumeColor(color: LightColor) {
                    assume(self.color == color)
                }

                @Trace
                tran setColor(color: LightColor) {
                    self.color := color
                }

                redefine tran {
                    choice {
                        inline assumeColor(LightColor::Red)
                        inline setColor(LightColor::Green)
                    } or {
                        inline assumeColor(LightColor::Green)
                        inline setColor(LightColor::Yellow)
                    } or {
                        inline assumeColor(LightColor::Yellow)
                        inline setColor(LightColor::Red)
                    }
                }

                prop isColor(color: LightColor): bool {
                    return self.color == color
                }
            }

            class Intersection {
                contains north: TrafficLight
                contains east: TrafficLight

                redefine tran {
                    choice {
                        assume(!east.isColor(LightColor::Green))
                        inline north.main()
                    } or {
                        assume(!north.isColor(LightColor::Green))
                        inline east.main()
                    }
                }
            }

            @VerificationCase
            class GreenColorIsReachable {
                contains light: TrafficLight[1]

                redefine tran {
                    inline light.main()
                }

                prop {
                    return EF (light.color == LightColor::Green)
                }
            }

            @VerificationCase
            class IntersectionNeverBothGreen {
                contains intersection: Intersection

                redefine tran {
                    inline intersection.main()
                }

                prop {
                    return AG !(intersection.north.color == LightColor::Green && intersection.east.color == LightColor::Green)
                }
            }
            """;

    public static String trafficLightsWith(String addition) {
        return TRAFFIC_LIGHTS + "\n" + addition;
    }

    public static Map<String, String> gammaLibrary() throws IOException {
        var files = new TreeMap<String, String>();
        try (var paths = Files.list(GAMMA_LIBRARY)) {
            for (var path : paths.toList()) {
                files.put(path.getFileName().toString(), Files.readString(path));
            }
        }
        return files;
    }
}

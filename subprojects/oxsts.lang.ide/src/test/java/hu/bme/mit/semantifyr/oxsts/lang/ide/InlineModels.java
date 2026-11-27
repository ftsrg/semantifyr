/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import static hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel;

public final class InlineModels {

    public static final String TRAFFIC_LIGHT_FILE_NAME = "Lights.semantifyr";

    public static final String TRAFFIC_LIGHT = testModel(InlineModels.class, "traffic_light.semantifyr");

    public static final String LAMP_LIBRARY_FILE_NAME = "Lamps.semantifyr";

    public static final String LAMP_LIBRARY = testModel(InlineModels.class, "lamp_library.semantifyr");

    private InlineModels() {}

    public static String trafficLightWith(String addition) {
        return TRAFFIC_LIGHT + "\n" + addition;
    }

    public static String crossing(String members) {
        return """
                package example::crossing

                import example::lights

                class Crossing {
                    contains light: TrafficLight[1]

                """ + members.indent(4) + "}\n";
    }
}

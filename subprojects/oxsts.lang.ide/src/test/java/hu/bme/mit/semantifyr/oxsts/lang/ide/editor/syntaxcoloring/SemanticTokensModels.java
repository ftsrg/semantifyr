/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.editor.syntaxcoloring;

final class SemanticTokensModels {

    static final String LAMP_LIBRARY = """
            package example::library

            enum LightColor {
                Red,
                Green
            }

            class Lamp {
                var on: bool := false
            }
            """;

    static final String GARAGE_WITH_QUALIFIED_REFERENCES = """
            package example::garage

            import example::library

            class Garage {
                contains lamp: example::library::Lamp
                var color: LightColor := LightColor::Red
            }
            """;

    static final String VEHICLES_WITH_MODIFIERS = """
            package example::vehicles

            abstract class Vehicle {
                abstract tran drive()
            }

            class Garage {
                refers vehicle: Vehicle
                var open: bool := false
            }
            """;

    private SemanticTokensModels() {}
}

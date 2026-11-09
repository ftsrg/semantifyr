/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import org.eclipse.lsp4j.HoverParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OxstsHoverTest extends OxstsLanguageServerTest {

    @BeforeEach
    void addLightsLibrary() {
        addLibraryFile(TrafficLightFixture.LIBRARY_FILE_NAME, TrafficLightFixture.LIBRARY);
    }

    private String hover(String members) throws Exception {
        var model = MarkedText.of(TrafficLightFixture.crossing(members));
        var document = openModel(model.text());
        var hover = languageServer
                .hover(new HoverParams(document, model.position()))
                .get();
        return hover.getContents().getRight().getValue();
    }

    @Test
    void hoverOnACallIntoALibraryClassShowsTheSignatureAndTheComment() throws Exception {
        var contents = hover("""
                tran switchToGreen() {
                    inline light.set<|>Color(LightColor::Green)
                }
                """);

        assertThat(contents).isEqualTo("""
                `example::lights::TrafficLight`

                ```oxsts
                tran setColor(color: LightColor)
                ```

                Switches the light.""");
    }

    @Test
    void hoverOnARedefinitionNamesTheRedefinedDeclarationAndInheritsItsComment() throws Exception {
        var model = MarkedText.of("""
                package example::crossing

                import example::lights

                class SlowLight : TrafficLight {
                    redefine tran set<|>Color(color: LightColor) {
                        self.color := LightColor::Yellow
                    }
                }
                """);
        var document = openModel(model.text());

        var hover = languageServer
                .hover(new HoverParams(document, model.position()))
                .get();

        assertThat(hover.getContents().getRight().getValue()).isEqualTo("""
                `example::crossing::SlowLight`

                ```oxsts
                redefine tran setColor(color: LightColor)
                ```

                Redefines `tran setColor(color: LightColor)` in `TrafficLight`.

                Switches the light.""");
    }

    @Test
    void hoverOnAnUndocumentedFeatureShowsItsTypeAndMultiplicity() throws Exception {
        var contents = hover("""
                tran switchToGreen() {
                    inline li<|>ght.setColor(LightColor::Green)
                }
                """);

        assertThat(contents).isEqualTo("""
                `example::crossing::Crossing`

                ```oxsts
                contains light: TrafficLight[1]
                ```""");
    }

    @Test
    void hoverOnAParameterShowsItsType() throws Exception {
        var contents = hover("""
                tran switchTo(target: LightColor) {
                    inline light.setColor(tar<|>get)
                }
                """);

        assertThat(contents).isEqualTo("""
                `example::crossing::Crossing::switchTo`

                ```oxsts
                parameter target: LightColor
                ```""");
    }

    @Test
    void hoverOnAnEnumLiteralShowsItsEnum() throws Exception {
        var contents = hover("""
                tran switchToGreen() {
                    inline light.setColor(LightColor::Gr<|>een)
                }
                """);

        assertThat(contents).isEqualTo("""
                `example::lights::LightColor`

                ```oxsts
                enum literal LightColor::Green
                ```""");
    }

    @Test
    void hoverOnACallOfAnUnnamedTransitionNamesTheClassOfTheResolvedRedefinition() throws Exception {
        var contents = hover("""
                tran step() {
                    inline light.ma<|>in()
                }
                """);

        assertThat(contents).isEqualTo("""
                `example::lights::TrafficLight`

                ```oxsts
                redefine tran
                ```

                Redefines `tran` in `Anything`.

                The base main transition.""");
    }
}

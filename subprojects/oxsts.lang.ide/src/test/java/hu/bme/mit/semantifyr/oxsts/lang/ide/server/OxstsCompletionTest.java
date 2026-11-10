/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static hu.bme.mit.semantifyr.oxsts.lang.ide.TestModels.basicsWith;
import static hu.bme.mit.semantifyr.oxsts.lang.ide.TestModels.gammaLibrary;
import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import java.util.List;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.junit.jupiter.api.Test;

class OxstsCompletionTest extends OxstsLanguageServerTest {

    @Test
    void containmentTypeOffersOnlyClasses() throws Exception {
        var items = complete(MarkedText.of(basicsWith("""
                class Case {
                    contains other: <|>
                }
                """)));

        assertThat(labels(items))
                .contains("TrafficLight", "Intersection", "Anything")
                .doesNotContain("bool", "int", "any", "LightColor");
        assertThat(item(items, "TrafficLight")).satisfies(item -> {
            assertThat(item.getKind()).isEqualTo(CompletionItemKind.Class);
            assertThat(item.getDetail()).isEqualTo("example::trafficlight::basics");
        });
    }

    @Test
    void referenceTypeOffersClassesDataTypesAndEnums() throws Exception {
        var items = complete(MarkedText.of(basicsWith("""
                class Case {
                    refers color: <|>
                }
                """)));

        assertThat(labels(items)).contains("TrafficLight", "bool", "LightColor");
        assertThat(item(items, "LightColor").getKind()).isEqualTo(CompletionItemKind.Enum);
    }

    @Test
    void expressionMemberOffersValuesAndNoTransitions() throws Exception {
        var items = complete(MarkedText.of(basicsWith("""
                class Case {
                    contains light: TrafficLight

                    prop {
                        return EF (light.<|>)
                    }
                }
                """)));

        assertThat(labels(items)).containsExactlyInAnyOrder("color", "isColor");
        assertThat(item(items, "color")).satisfies(item -> {
            assertThat(item.getKind()).isEqualTo(CompletionItemKind.Field);
            assertThat(item.getDetail()).isEqualTo("LightColor");
        });
        assertThat(item(items, "isColor")).satisfies(item -> {
            assertThat(item.getKind()).isEqualTo(CompletionItemKind.Function);
            assertThat(item.getDetail()).isEqualTo("isColor(color: LightColor): bool");
            assertThat(item.getTextEdit().getLeft().getNewText()).isEqualTo("isColor(${1:color})$0");
        });
    }

    @Test
    void statementStartOffersOnlyAssignableTargets() throws Exception {
        var items = complete(MarkedText.of(basicsWith("""
                class Case {
                    contains light: TrafficLight
                    var counter: int := 0

                    redefine tran {
                        <|>
                    }
                }
                """)));

        assertThat(labels(items)).containsExactlyInAnyOrder("counter", "light");
    }

    @Test
    void inlineStatementOffersTransitionsAndInstanceFeatures() throws Exception {
        var items = complete(MarkedText.of(basicsWith("""
                class Case {
                    contains light: TrafficLight
                    var counter: int := 0

                    redefine tran {
                        inline <|>
                    }
                }
                """)));

        assertThat(labels(items)).contains("light", "main", "init").doesNotContain("counter", "prop", "Case");
    }

    @Test
    void inlineCallInsertsTheTransitionWithItsParameters() throws Exception {
        var items = complete(MarkedText.of(basicsWith("""
                class Case {
                    contains light: TrafficLight

                    redefine tran {
                        inline light.<|>
                    }
                }
                """)));

        assertThat(item(items, "setColor")).satisfies(item -> {
            assertThat(item.getKind()).isEqualTo(CompletionItemKind.Method);
            assertThat(item.getDetail()).isEqualTo("setColor(color: LightColor)");
            assertThat(item.getTextEdit().getLeft().getNewText()).isEqualTo("setColor(${1:color})$0");
            assertThat(item.getInsertTextFormat()).isEqualTo(InsertTextFormat.Snippet);
        });
        assertThat(item(items, "main").getTextEdit().getLeft().getNewText()).isEqualTo("main()$0");
    }

    @Test
    void callArgumentOffersTheNamedArgumentAndValuesOfTheParameterTypeFirst() throws Exception {
        var items = complete(MarkedText.of(basicsWith("""
                class Case {
                    contains light: TrafficLight

                    redefine tran {
                        inline light.setColor(<|>)
                    }
                }
                """)));

        var proposals = items.stream()
                .filter(item -> item.getKind() != CompletionItemKind.Keyword)
                .toList();
        assertThat(proposals.getFirst().getLabel()).isEqualTo("color =");
        assertThat(proposals.subList(1, 7))
                .extracting(CompletionItem::getKind)
                .containsOnly(CompletionItemKind.EnumMember);
        assertThat(item(items, "color =")).satisfies(item -> {
            assertThat(item.getTextEdit().getLeft().getNewText()).isEqualTo("color = ");
            assertThat(item.getDetail()).isEqualTo("LightColor");
        });
        assertThat(item(items, "LightColor::Red").getKind()).isEqualTo(CompletionItemKind.EnumMember);
        assertThat(labels(items)).doesNotContain("value", "\"value\"", "main", "TrafficLight", "VerificationCase");
    }

    @Test
    void propertyArgumentInAnExpressionOffersTheNamedArgument() throws Exception {
        var items = complete(MarkedText.of(basicsWith("""
                class Case {
                    contains light: TrafficLight

                    prop {
                        return EF (light.isColor(<|>))
                    }
                }
                """)));

        assertThat(labels(items).getFirst()).isEqualTo("color =");
    }

    @Test
    void redefineTransitionOffersTheInheritedSignaturesWithoutInit() throws Exception {
        var items = complete(MarkedText.of(basicsWith("""
                class SpecialLight : TrafficLight {
                    redefine tran <|>
                }
                """)));

        assertThat(labels(items))
                .containsExactlyInAnyOrder("assumeColor(color: LightColor)", "setColor(color: LightColor)");
        assertThat(item(items, "setColor(color: LightColor)")
                        .getTextEdit()
                        .getLeft()
                        .getNewText())
                .isEqualTo("setColor(color: LightColor) {\n\t$0\n}");
    }

    @Test
    void classBodyOffersRedefinitionsOfInheritedMembers() throws Exception {
        var items = complete(MarkedText.of(basicsWith("""
                class SpecialLight : TrafficLight {
                    redefine tran setColor(color: LightColor) {
                        self.color := color
                    }

                    <|>
                }
                """)));

        assertThat(labels(items))
                .contains(
                        "redefine tran assumeColor(color: LightColor)",
                        "redefine prop isColor(color: LightColor): bool",
                        "redefine tran",
                        "redefine init")
                .doesNotContain("redefine tran setColor(color: LightColor)");
        assertThat(item(items, "redefine prop isColor(color: LightColor): bool"))
                .satisfies(item -> {
                    assertThat(item.getKind()).isEqualTo(CompletionItemKind.Function);
                    assertThat(item.getDetail()).isEqualTo("TrafficLight");
                    assertThat(item.getTextEdit().getLeft().getNewText())
                            .isEqualTo("redefine prop isColor(color: LightColor): bool {\n\treturn $0\n}");
                });
    }

    @Test
    void classBodyOffersAnAbstractFeatureAsContainsAndRefersRedefinitions() throws Exception {
        var items = complete(MarkedText.of(basicsWith("""
                abstract class LightHolder {
                    features light: TrafficLight
                }

                class ConcreteLightHolder : LightHolder {
                    <|>
                }
                """)));

        assertThat(labels(items))
                .contains("redefine contains light: TrafficLight", "redefine refers light: TrafficLight")
                .doesNotContain("redefine features light: TrafficLight");
        assertThat(item(items, "redefine refers light: TrafficLight")).satisfies(item -> {
            assertThat(item.getKind()).isEqualTo(CompletionItemKind.Property);
            assertThat(item.getDetail()).isEqualTo("LightHolder");
            assertThat(item.getTextEdit().getLeft().getNewText()).isEqualTo("redefine refers light: TrafficLight");
        });
    }

    @Test
    void classBodyAfterAFeatureOffersTheRedefinitionsOfTheClassNotOfTheFeatureType() throws Exception {
        var items = complete(MarkedText.of(basicsWith("""
                class Case {
                    contains light: TrafficLight
                    <|>
                }
                """)));

        assertThat(labels(items))
                .contains("redefine tran", "redefine init")
                .doesNotContain("redefine tran setColor(color: LightColor)");
    }

    @Test
    void libraryClassBodyOffersRedefinitionsOfLibraryMembers() throws Exception {
        for (var file : gammaLibrary().entrySet()) {
            addLibraryFile(file.getKey(), file.getValue());
        }
        var items = complete(MarkedText.of("""
                package example::library

                import semantifyr::gamma::statecharts

                class MyChart : Statechart {
                    <|>
                }
                """));

        assertThat(labels(items))
                .contains(
                        "redefine tran schedule()",
                        "redefine tran passTime()",
                        "redefine contains regions: Region[0..*]",
                        "redefine refers regionSchedule: RegionSchedule");
        assertThat(item(items, "redefine contains regions: Region[0..*]").getDetail())
                .isEqualTo("Statechart");
    }

    @Test
    void libraryTypesAndMembersCarryKindsAndDetails() throws Exception {
        for (var file : gammaLibrary().entrySet()) {
            addLibraryFile(file.getKey(), file.getValue());
        }
        var items = complete(MarkedText.of("""
                package example::library

                import semantifyr::gamma::statecharts

                class Case {
                    contains chart: Statechart

                    redefine tran {
                        inline chart.<|>
                    }
                }
                """));

        assertThat(labels(items))
                .contains("passTime", "schedule", "assumeNoTransitionEnabled", "regions")
                .doesNotContain("regionSchedule");
        assertThat(item(items, "passTime").getDetail()).isEqualTo("passTime()");
        assertThat(item(items, "regions")).satisfies(item -> {
            assertThat(item.getKind()).isEqualTo(CompletionItemKind.Property);
            assertThat(item.getDetail()).isEqualTo("contains Region[0..*]");
        });
    }

    private List<CompletionItem> complete(MarkedText model) throws Exception {
        var document = openModel(model.text());
        return languageServer
                .completion(new CompletionParams(document, model.position()))
                .get()
                .getRight()
                .getItems();
    }

    private static List<String> labels(List<CompletionItem> items) {
        return items.stream()
                .filter(item -> item.getKind() != CompletionItemKind.Keyword)
                .map(CompletionItem::getLabel)
                .toList();
    }

    private static CompletionItem item(List<CompletionItem> items, String label) {
        return items.stream()
                .filter(item -> item.getLabel().equals(label))
                .findFirst()
                .orElseThrow();
    }
}

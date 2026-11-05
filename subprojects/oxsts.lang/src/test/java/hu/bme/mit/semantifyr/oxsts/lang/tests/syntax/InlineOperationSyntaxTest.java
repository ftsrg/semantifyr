/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.syntax;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineChoiceFor;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineSeqFor;
import org.eclipse.xtext.EcoreUtil2;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class InlineOperationSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void inlineCall() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran step() { count := count + 1 }
                redefine tran { inline step() }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var calls = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), InlineCall.class);
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().getCallExpression()).isNotNull();
    }

    @Test
    void inlineIfWithElse() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                redefine tran { inline if (1 < 2) { count := 1 } else { count := 2 } }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var inlineIfs = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), InlineIfOperation.class);
        assertThat(inlineIfs).hasSize(1);
        var inlineIf = inlineIfs.getFirst();
        assertThat(inlineIf.getGuard()).isNotNull();
        assertThat(inlineIf.getBody()).isNotNull();
        assertThat(inlineIf.getElse()).isNotNull();
    }

    @Test
    void inlineIfWithoutElse() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                redefine tran { inline if (1 < 2) { count := 1 } }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var inlineIfs = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), InlineIfOperation.class);
        assertThat(inlineIfs).hasSize(1);
        assertThat(inlineIfs.getFirst().getElse()).isNull();
    }

    @Test
    void inlineSeqForBare() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var total: int := 0
                redefine tran { inline for (index in 1..3) { total := total + 1 } }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var inlineFors = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), InlineSeqFor.class);
        assertThat(inlineFors).hasSize(1);
        var inlineFor = inlineFors.getFirst();
        assertThat(inlineFor.getLoopVariable().getName()).isEqualTo("index");
        assertThat(inlineFor.getRangeExpression()).isNotNull();
        assertThat(inlineFor.getBody()).isNotNull();
        assertThat(inlineFor.getElse()).isNull();
    }

    @Test
    void inlineSeqForExplicitSeqKeyword() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var total: int := 0
                redefine tran { inline for seq (index in 1..3) { total := total + 1 } }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var inlineFors = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), InlineSeqFor.class);
        assertThat(inlineFors).hasSize(1);
    }

    @Test
    void inlineSeqForWithElse() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var total: int := 0
                redefine tran {
                    inline for (index in 1..0) { total := total + 1 }
                    else { total := -1 }
                }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var inlineFors = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), InlineSeqFor.class);
        assertThat(inlineFors).hasSize(1);
        assertThat(inlineFors.getFirst().getElse()).isNotNull();
    }

    @Test
    void inlineChoiceFor() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var chosen: int := 0
                redefine tran { inline for choice (index in 1..3) { chosen := index } }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var inlineFors = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), InlineChoiceFor.class);
        assertThat(inlineFors).hasSize(1);
        assertThat(inlineFors.getFirst().getLoopVariable().getName()).isEqualTo("index");
    }

    @Test
    void inlineChoiceForWithElse() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var chosen: int := 0
                redefine tran {
                    inline for choice (index in 1..0) { chosen := index }
                    else { chosen := -1 }
                }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var inlineFors = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), InlineChoiceFor.class);
        assertThat(inlineFors).hasSize(1);
        assertThat(inlineFors.getFirst().getElse()).isNotNull();
    }
}

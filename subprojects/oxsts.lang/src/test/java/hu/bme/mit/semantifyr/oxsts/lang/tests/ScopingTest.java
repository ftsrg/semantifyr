/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression;
import org.eclipse.xtext.EcoreUtil2;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@InjectWithOxsts
public class ScopingTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void featureReferenceResolvesToDeclaredFeature() {
        var pkg = parseHelper.parse("""
            package test
            class Worker { var done: bool := false }
            class Host {
                contains w: Worker[1]
            }
            """);

        pkg.assertNoResourceErrors();
        var worker = pkg.classByName("Worker");
        var hostW = pkg.classByName("Host").featureByName("w");

        assertThat(hostW.typeDomain())
            .as("feature w's type should resolve to the Worker class declaration")
            .isSameAs(worker.eObject());
    }

    @Test
    void redefinedFeatureIsMarkedAsRedefine() {
        var pkg = parseHelper.parse("""
            package test
            class Worker { var done: bool := false }
            class BetterWorker : Worker { }
            class Base {
                contains w: Worker[1]
            }
            class Child : Base {
                redefine contains w: BetterWorker[1]
            }
            """);

        pkg.assertNoResourceErrors();

        var redefinedW = pkg.classByName("Child").featureByName("w");
        assertThat(redefinedW.isRedefine())
            .as("Child's w should have `redefine` flag set")
            .isTrue();

        var betterWorker = pkg.classByName("BetterWorker");
        assertThat(redefinedW.typeDomain())
            .as("Child's redefined w should be typed BetterWorker, not Worker")
            .isSameAs(betterWorker.eObject());
    }

    @Test
    void inlineCallTargetResolvesToNamedTransition() {
        var pkg = parseHelper.parse("""
            package test
            class Host {
                var x: int := 0
                tran step() { x := x + 1 }
                redefine tran { inline step() }
            }
            """);

        pkg.assertNoResourceErrors();
        var host = pkg.classByName("Host");
        var stepTran = host.namedTransition("step");
        var mainTran = host.anonymousMain();

        var inlineCall = EcoreUtil2.eAllOfType(mainTran.eObject(), InlineCall.class).getFirst();
        var callExpression = (CallSuffixExpression) inlineCall.getCallExpression();
        var callPrimary = callExpression.getPrimary();

        // The bare `step` reference in `inline step()` should resolve to the
        // step transition declaration, not some dangling proxy.
        assertThat(callPrimary).isInstanceOf(ElementReference.class);
        assertThat(((ElementReference) callPrimary).getElement())
            .as("inline call target must resolve to the step transition EObject")
            .isSameAs(stepTran.eObject());
    }

    @Test
    void navigatedCallTargetResolvesToFeatureMember() {
        var pkg = parseHelper.parse("""
            package test
            class Worker {
                var done: bool := false
                tran finish() { done := true }
            }
            class Host {
                contains w: Worker[1]
                redefine tran { inline w.finish() }
            }
            """);

        pkg.assertNoResourceErrors();
        var workerFinish = pkg.classByName("Worker").namedTransition("finish");
        var hostW = pkg.classByName("Host").featureByName("w");
        var main = pkg.classByName("Host").anonymousMain();

        var inlineCall = EcoreUtil2.eAllOfType(main.eObject(), InlineCall.class).getFirst();
        var callExpression = (CallSuffixExpression) inlineCall.getCallExpression();
        var navigation = (NavigationSuffixExpression) callExpression.getPrimary();

        // The primary of the navigation is `w`; the member is `finish`.
        assertThat(((ElementReference) navigation.getPrimary()).getElement())
            .as("navigation's primary must resolve to host's w feature")
            .isSameAs(hostW.eObject());
        assertThat(navigation.getMember())
            .as("navigation's member must resolve to Worker's finish transition")
            .isSameAs(workerFinish.eObject());
    }

    @Test
    void enumLiteralReferenceResolvesAcrossTypes() {
        var pkg = parseHelper.parse("""
            package test
            enum Color { Red, Green, Blue }
            class C {
                var c: Color := Color::Red
            }
            """);

        pkg.assertNoResourceErrors();
        var red = pkg.enumByName("Color").literalByName("Red");
        var initializer = pkg.classByName("C").variableByName("c").initializer();

        var reference = (ElementReference) initializer;
        assertThat(reference.getElement())
            .as("`Color::Red` must resolve to the Red enum literal")
            .isSameAs(red);
    }
}

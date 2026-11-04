/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Modules;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsRuntimeModule;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.resource.ISelectable;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ScopingTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private ParseHelper<OxstsModelPackage> packageParseHelper;

    @Inject
    private Provider<XtextResourceSet> resourceSetProvider;

    @Test
    void featureReferenceResolvesToDeclaredFeature() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker { var done: bool := false }
            class Host {
                contains worker: Worker[1]
            }
            """);

        parsedPackage.assertNoResourceErrors();
        var worker = parsedPackage.classByName("Worker");
        var hostWorker = parsedPackage.classByName("Host").featureByName("worker");

        assertThat(hostWorker.typeDomain())
                .as("feature worker's type should resolve to the Worker class declaration")
                .isSameAs(worker.eObject());
    }

    @Test
    void redefinedFeatureIsMarkedAsRedefine() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker { var done: bool := false }
            class BetterWorker : Worker { }
            class Base {
                contains worker: Worker[1]
            }
            class Child : Base {
                redefine contains worker: BetterWorker[1]
            }
            """);

        parsedPackage.assertNoResourceErrors();

        var redefinedWorker = parsedPackage.classByName("Child").featureByName("worker");
        assertThat(redefinedWorker.isRedefine())
                .as("Child's worker should have `redefine` flag set")
                .isTrue();

        var betterWorker = parsedPackage.classByName("BetterWorker");
        assertThat(redefinedWorker.typeDomain())
                .as("Child's redefined worker should be typed BetterWorker, not Worker")
                .isSameAs(betterWorker.eObject());
    }

    @Test
    void inlineCallTargetResolvesToNamedTransition() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var x: int := 0
                tran step() { x := x + 1 }
                redefine tran { inline step() }
            }
            """);

        parsedPackage.assertNoResourceErrors();
        var host = parsedPackage.classByName("Host");
        var stepTransition = host.namedTransition("step");
        var mainTransition = host.anonymousMain();

        var inlineCall = EcoreUtil2.eAllOfType(mainTransition.eObject(), InlineCall.class)
                .getFirst();
        var callExpression = (CallSuffixExpression) inlineCall.getCallExpression();
        var callPrimary = callExpression.getPrimary();

        assertThat(callPrimary).isInstanceOf(ElementReference.class);
        assertThat(((ElementReference) callPrimary).getElement())
                .as("inline call target must resolve to the step transition EObject")
                .isSameAs(stepTransition.eObject());
    }

    @Test
    void navigatedCallTargetResolvesToFeatureMember() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker {
                var done: bool := false
                tran finish() { done := true }
            }
            class Host {
                contains worker: Worker[1]
                redefine tran { inline worker.finish() }
            }
            """);

        parsedPackage.assertNoResourceErrors();
        var workerFinish = parsedPackage.classByName("Worker").namedTransition("finish");
        var hostWorker = parsedPackage.classByName("Host").featureByName("worker");
        var main = parsedPackage.classByName("Host").anonymousMain();

        var inlineCall = EcoreUtil2.eAllOfType(main.eObject(), InlineCall.class).getFirst();
        var callExpression = (CallSuffixExpression) inlineCall.getCallExpression();
        var navigation = (NavigationSuffixExpression) callExpression.getPrimary();

        assertThat(((ElementReference) navigation.getPrimary()).getElement())
                .as("navigation's primary must resolve to host's worker feature")
                .isSameAs(hostWorker.eObject());
        assertThat(navigation.getMember())
                .as("navigation's member must resolve to Worker's finish transition")
                .isSameAs(workerFinish.eObject());
    }

    @Test
    void enumLiteralReferenceResolvesAcrossTypes() {
        var parsedPackage = parseHelper.parse("""
            package test
            enum Color {
                Red,
                Green,
                Blue,
            }
            class Lamp {
                var color: Color := Color::Red
            }
            """);

        parsedPackage.assertNoResourceErrors();
        var red = parsedPackage.enumByName("Color").literalByName("Red");
        var initializer =
                parsedPackage.classByName("Lamp").variableByName("color").initializer();

        var reference = (ElementReference) initializer;
        assertThat(reference.getElement())
                .as("`Color::Red` must resolve to the Red enum literal")
                .isSameAs(red);
    }

    @Test
    void membersOfTheFirstSupertypeResolveUnderMultipleInheritance() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Left { var x: int := 0 }
            class Right { var y: int := 0 }
            class Both : Left, Right {
                redefine tran { x := 1 }
            }
            class User {
                contains both: Both
                redefine tran { both.x := 2 }
            }
            """);

        parsedPackage.assertNoResourceErrors();
    }

    @Test
    void multipleInheritanceIsRejectedAtTheSecondSupertype() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Left { var x: int := 0 }
            class Right { var y: int := 0 }
            class Both : Left, Right {
                redefine tran { x := 1 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.MULTIPLE_INHERITANCE, "'Right'");
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.MULTIPLE_INHERITANCE);
                    assertThat(issue.getLineNumber()).isEqualTo(4);
                });
    }

    @Test
    void scopeFailureIsReportedInsteadOfAnUnexplainedUnresolvedReference() throws Exception {
        var failingInjector = Guice.createInjector(Modules.override(new OxstsRuntimeModule())
                .with(binder -> binder.bind(DomainMemberCollectionProvider.class)
                        .to(FailingDomainMemberCollectionProvider.class)));
        var failingParseHelper =
                failingInjector.getInstance(Key.get(new TypeLiteral<ParseHelper<OxstsModelPackage>>() {}));

        var oxstsPackage = failingParseHelper.parse("""
            package test
            class Worker { var done: bool := false }
            class Host {
                contains worker: Worker
                redefine tran { worker.done := true }
            }
            """);
        EcoreUtil.resolveAll(oxstsPackage);

        assertThat(oxstsPackage.eResource().getErrors())
                .anySatisfy(error -> assertThat(error.getMessage()).contains("member collection unavailable"));
    }

    @Test
    void navigationAfterAnUnresolvedMemberReportsOnlyTheUnresolvedReferences() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker {
                tran step() { }
            }
            class Host {
                contains worker: Worker
                redefine tran { inline worker.missing.step() }
            }
            """, CheckMode.ALL);

        assertThat(parsedPackage.getResourceErrors())
                .isNotEmpty()
                .allSatisfy(error -> assertThat(error.getMessage()).startsWith("Couldn't resolve reference"));
        assertThat(parsedPackage.getResourceErrors())
                .anySatisfy(error -> assertThat(error.getMessage()).contains("'missing'"));
    }

    @Test
    void importedNamesResolveUnqualifiedAndOtherPackagesOnlyQualified() throws Exception {
        var resourceSet = resourceSetProvider.get();
        packageParseHelper.parse("""
            package library
            class Lamp { var on: bool := false }
            """, resourceSet);
        packageParseHelper.parse("""
            package other
            enum Color {
                Red,
                Blue,
            }
            """, resourceSet);
        var user = packageParseHelper.parse("""
            package user
            import library
            class Room {
                contains lamp: Lamp
                var color: other::Color := other::Color::Red
            }
            """, resourceSet);
        EcoreUtil.resolveAll(resourceSet);

        assertThat(user.eResource().getErrors()).isEmpty();
    }

    @Test
    void importedGlobalFeatureResolvesUnqualified() throws Exception {
        var resourceSet = resourceSetProvider.get();
        var library = packageParseHelper.parse("""
            package library
            class Counter { var tick: int := 0 }
            global containment counter: Counter[1]
            """, resourceSet);
        var user = packageParseHelper.parse("""
            package user
            import library
            class Ticker {
                redefine tran { counter.tick := counter.tick + 1 }
            }
            """, resourceSet);
        EcoreUtil.resolveAll(resourceSet);

        assertThat(user.eResource().getErrors()).isEmpty();
        var counter = library.getDeclarations().stream()
                .filter(FeatureDeclaration.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(EcoreUtil2.eAllOfType(user, NavigationSuffixExpression.class))
                .isNotEmpty()
                .allSatisfy(navigation -> assertThat(((ElementReference) navigation.getPrimary()).getElement())
                        .isSameAs(counter));
    }

    @Test
    void nameOfAnUnimportedPackageDoesNotResolveUnqualified() throws Exception {
        var resourceSet = resourceSetProvider.get();
        packageParseHelper.parse("""
            package library
            class Lamp { var on: bool := false }
            """, resourceSet);
        var user = packageParseHelper.parse("""
            package user
            class Room {
                contains lamp: Lamp
            }
            """, resourceSet);
        EcoreUtil.resolveAll(resourceSet);

        assertThat(user.eResource().getErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getMessage()).contains("'Lamp'"));
    }

    public static class FailingDomainMemberCollectionProvider extends DomainMemberCollectionProvider {
        @Override
        public ISelectable getMembers(DomainDeclaration domain) {
            throw new IllegalStateException("member collection unavailable");
        }
    }
}

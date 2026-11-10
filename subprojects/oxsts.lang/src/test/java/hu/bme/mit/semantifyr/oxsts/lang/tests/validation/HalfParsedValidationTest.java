/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.diagnostics.ExceptionDiagnostic;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.EValidatorRegistrar;
import org.eclipse.xtext.validation.IResourceValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@InjectWithOxsts
public class HalfParsedValidationTest {

    @Inject
    private ParseHelper<OxstsModelPackage> parseHelper;

    @Inject
    private Injector injector;

    @Inject
    private OxstsPackageParseHelper packageParseHelper;

    @Inject
    private IResourceValidator resourceValidator;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "contains leaf",
                "contains",
                "refers leaf",
                "redefine refers",
                "redefine contains",
                "contains leaf: Leaf { contains }",
                "contains leaf: Leaf { redefine refers }",
            })
    void everyCheckCompletesOnAHalfParsedFeature(String member) throws Exception {
        var oxstsPackage = parseHelper.parse("""
            package test
            class Leaf {
                refers next: Leaf[0..1]
            }
            class Host {
                %s
            }
            """.formatted(member));
        assertThat(oxstsPackage.eResource().getErrors()).isNotEmpty();

        var validator = injector.getInstance(RethrowingValidator.class);
        var diagnostics = new BasicDiagnostic();
        Map<Object, Object> context = new HashMap<>(Map.of(CheckMode.KEY, CheckMode.ALL));
        oxstsPackage.eAllContents().forEachRemaining(element -> {
            validator.validate(element.eClass(), element, diagnostics, context);
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "counter :=",
                "counter",
                "counter := counter.",
                "inline counter.",
                "havoc()",
                "assume()",
                "if ()",
                "var",
            })
    void anIncompleteStatementReportsOnlyItsSyntaxError(String statement) {
        var parsedPackage = packageParseHelper.parse("""
            package test
            class Host {
                var counter: int := 0
                redefine tran {
                    %s
                }
            }
            """.formatted(statement), CheckMode.ALL);

        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(Diagnostic.SYNTAX_DIAGNOSTIC);
                });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "inline counter.",
                "inline worker.",
                "inline worker.missing()",
                "inline worker.missing.step()",
            })
    void aHalfWrittenCallReportsOnlyItsSyntaxOrLinkingError(String statement) throws Exception {
        var oxstsPackage = parseHelper.parse("""
            package test
            class Worker {
                tran step() { }
            }
            class Host {
                contains worker: Worker
                var counter: int := 0
                redefine tran {
                    %s
                }
            }
            """.formatted(statement));
        EcoreUtil.resolveAll(oxstsPackage);

        var validator = injector.getInstance(RethrowingValidator.class);
        var diagnostics = new BasicDiagnostic();
        Map<Object, Object> context = new HashMap<>(Map.of(CheckMode.KEY, CheckMode.ALL));
        oxstsPackage.eAllContents().forEachRemaining(element -> {
            validator.validate(element.eClass(), element, diagnostics, context);
        });

        assertThat(oxstsPackage.eResource().getErrors()).noneMatch(ExceptionDiagnostic.class::isInstance);
        var issues = resourceValidator.validate(oxstsPackage.eResource(), CheckMode.ALL, CancelIndicator.NullImpl);
        assertThat(issues)
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isIn(Diagnostic.SYNTAX_DIAGNOSTIC, Diagnostic.LINKING_DIAGNOSTIC);
                });
    }

    public static class RethrowingValidator extends OxstsValidator {
        // registered beside OxstsValidator it would report every issue of the later tests twice
        @Override
        public void register(EValidatorRegistrar registrar) {}

        @Override
        protected void handleExceptionDuringValidation(Throwable exception) {
            throw new AssertionError("A check threw on the half-parsed model", exception);
        }
    }
}

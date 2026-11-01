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
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@InjectWithOxsts
public class HalfParsedValidationTest {

    @Inject
    private ParseHelper<OxstsModelPackage> parseHelper;

    @Inject
    private Injector injector;

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

    public static class RethrowingValidator extends OxstsValidator {
        @Override
        protected void handleExceptionDuringValidation(Throwable exception) {
            throw new AssertionError("A check threw on the half-parsed model", exception);
        }
    }
}

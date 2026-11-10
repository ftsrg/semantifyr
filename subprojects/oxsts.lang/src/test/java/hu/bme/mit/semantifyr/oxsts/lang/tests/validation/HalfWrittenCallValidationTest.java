/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.inject.Inject;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@InjectWithOxsts
public class HalfWrittenCallValidationTest {

    @Inject
    private ParseHelper<OxstsModelPackage> parseHelper;

    @Inject
    private Injector injector;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "inline counter.missing()",
                "inline counter.",
                "inline counter.missing(1, value = 2)",
            })
    void callChecksCompleteOnAHalfWrittenCall(String callText) throws Exception {
        var oxstsPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran add(value: int) {
                    count := count + value
                }
            }
            class Host {
                contains counter: Counter
                redefine tran {
                    %s
                }
            }
            """.formatted(callText));
        EcoreUtil.resolveAll(oxstsPackage);
        assertThat(oxstsPackage.eResource().getErrors()).isNotEmpty();

        var validator = injector.getInstance(OxstsValidator.class);
        var calls = EcoreUtil2.eAllOfType(oxstsPackage, CallSuffixExpression.class);
        assertThat(calls).isNotEmpty();
        for (var call : calls) {
            assertThatCode(() -> {
                        validator.checkCallExpressionArgumentCount(call);
                        validator.checkCallArgumentBindings(call);
                    })
                    .doesNotThrowAnyException();
        }
    }
}

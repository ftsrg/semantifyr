/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import java.util.List;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@InjectWithOxsts
public class TimeScaleValidationTest {

    @Inject
    private ParseHelper<InlinedOxsts> parseHelper;

    @Inject
    private IResourceValidator resourceValidator;

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void witnessWithATimeScaleOfAtLeastOneIsAccepted(int timeScale) throws Exception {
        assertThat(validate("witness of semantifyr::Anything timescale " + timeScale))
                .isEmpty();
    }

    @Test
    void timeScaleOfZeroIsRejected() throws Exception {
        assertThat(validate("witness of semantifyr::Anything timescale 0"))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.INCORRECT_TIME_SCALE);
                    assertThat(issue.getMessage()).contains("at least 1");
                });
    }

    @Test
    void timeScaleOnAnInlinedModelThatIsNoWitnessIsRejected() throws Exception {
        assertThat(validate("inlined oxsts of semantifyr::Anything timescale 1"))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.INCORRECT_TIME_SCALE);
                    assertThat(issue.getMessage()).contains("Only a witness");
                });
    }

    private List<Issue> validate(String header) throws Exception {
        var inlinedOxsts = parseHelper.parse(header + """

            init {
            }

            tran {
            }

            prop {
                AG true
            }
            """);
        return resourceValidator.validate(inlinedOxsts.eResource(), CheckMode.ALL, CancelIndicator.NullImpl);
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.formatting;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import org.eclipse.xtext.resource.SaveOptions;
import org.eclipse.xtext.serializer.ISerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@InjectWithOxsts
public class OxstsFormatterTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private ISerializer serializer;

    @ParameterizedTest
    @ValueSource(strings = {"..", "..<"})
    void rangeOperatorHasNoSpaces(String operator) {
        var pkg = parseHelper.parse("""
            package test
            class C {
                contains children: C[0  %1$s  2]
                var x: int := 0
                redefine tran {
                    for (i in 0  %1$s  2) {
                        x := x + i
                    }
                }
            }
            """.formatted(operator));
        pkg.assertNoResourceErrors();

        var formatted = serializer.serialize(pkg.getOxstsPackage(), SaveOptions.newBuilder().format().getOptions());

        assertThat(formatted).contains("C[0" + operator + "2]", "in 0" + operator + "2)");
    }
}

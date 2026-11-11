/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.validation;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class VariableAnnotationValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void stringArgumentOnAVariablePasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Note(text: string)
            class Counter {
                @Note("a counter")
                var count: int := 0
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }
}

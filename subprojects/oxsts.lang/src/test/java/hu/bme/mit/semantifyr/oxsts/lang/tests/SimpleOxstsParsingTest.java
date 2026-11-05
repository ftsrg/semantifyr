/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class SimpleOxstsParsingTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void simpleTest() {
        var model = parseHelper.parse("""
                package test

                class Element
                class Holder {
                    features elements: Element[0..*]
                    contains elementWithExactMultiplicity: Element[1] subsets elements
                    contains elementWithDefaultMultiplicity: Element subsets elements
                    contains elementWithRangeMultiplicity: Element[1..1] subsets elements
                    refers size: int = 3
                    refers twiceSize: int = size * 2
                }
                class Model {
                    var counter: int := 10
                    prop alwaysFalse(): bool { return false }
                    init { }
                    tran { counter := 20 }
                    tran named() { }
                    tran named(value: int) { counter := value }
                }
                """);
        assertThat(model.getResourceErrors()).isEmpty();
    }
}

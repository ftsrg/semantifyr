/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


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
                contains e1: Element[1] subsets elements
                contains e2: Element subsets elements
                contains e3: Element[1..1] subsets elements
                refers size: int = 3
                refers twiceSize: int = size * 2
            }
            class Model {
                var x: int := 10
                prop p1(): bool { return false }
                init { }
                tran { x := 20 }
                tran named() { }
                tran named(p: int) { x := p }
            }
            """);
        assertThat(model.getResourceErrors()).isEmpty();
    }

}

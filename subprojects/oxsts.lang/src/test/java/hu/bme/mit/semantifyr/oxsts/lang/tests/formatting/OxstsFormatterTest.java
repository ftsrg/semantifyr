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
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.resource.SaveOptions;
import org.eclipse.xtext.serializer.ISerializer;
import org.junit.jupiter.api.Test;
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
        var parsedPackage = parseHelper.parse("""
            package test
            class Node {
                contains children: Node[0  %1$s  2]
                var sum: int := 0
                redefine tran {
                    for (index in 0  %1$s  2) {
                        sum := sum + index
                    }
                }
            }
            """.formatted(operator));
        parsedPackage.assertNoResourceErrors();

        var options = SaveOptions.newBuilder().format().getOptions();
        var formatted = serializer.serialize(parsedPackage.getOxstsPackage(), options);

        assertThat(formatted).contains("Node[0" + operator + "2]", "in 0" + operator + "2)");
    }

    @Test
    void serializedDeclarationIsIndentedWithFourSpacesAndHasNoSpaceBeforeItsParameters() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Shape {
                tran draw(size: int) {
                }
            }
            class Square : Shape {
                redefine tran draw(size: int) {
                    assume(size > 0)
                }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var resourceSet = parsedPackage.getOxstsPackage().eResource().getResourceSet();
        var copy = EcoreUtil.copy(parsedPackage.getOxstsPackage());
        resourceSet.createResource(URI.createURI("copy.oxsts")).getContents().add(copy);

        var serialized =
                serializer.serialize(copy, SaveOptions.newBuilder().format().getOptions());

        assertThat(serialized).endsWith("""
            class Square : Shape {
                redefine tran draw(size: int) {
                    assume(size > 0)
                }
            }""");
    }
}

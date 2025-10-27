/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.naming;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.QualifiedName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@InjectWithOxsts
public class QualifiedNameConverterTests {

    @Inject
    private IQualifiedNameConverter qualifiedNameConverter;

    public static Stream<Arguments> toStringTest() {
        return Stream.of(
                Arguments.of("a", QualifiedName.create("a")),
                Arguments.of("a1", QualifiedName.create("a1")),
                Arguments.of("_", QualifiedName.create("_")),
                Arguments.of("a::b", QualifiedName.create("a", "b")),
                Arguments.of("'a b'", QualifiedName.create("a b")),
                Arguments.of("'11'", QualifiedName.create("11")),
                Arguments.of("'$$'", QualifiedName.create("$$")),
                Arguments.of("'::'", QualifiedName.create("::")),
                Arguments.of("main", QualifiedName.create("main")),
                Arguments.of("init", QualifiedName.create("init")),
                Arguments.of("havoc", QualifiedName.create("havoc")),
                Arguments.of("prop", QualifiedName.create("prop")),
                Arguments.of("'a b'::c", QualifiedName.create("a b", "c")),
                Arguments.of("'a b'::'c d'", QualifiedName.create("a b", "c d")),
                Arguments.of("a::'b c'", QualifiedName.create("a", "b c")),
                Arguments.of("'a b'::c1::_d", QualifiedName.create("a b", "c1", "_d"))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void toStringTest(String expected, QualifiedName qualifiedName) {
        var string = qualifiedNameConverter.toString(qualifiedName);
        assertThat(string).isEqualTo(expected);
    }

    public static Stream<Arguments> toQualifiedNameTest() {
        return toStringTest();
    }

    @ParameterizedTest
    @MethodSource
    public void toQualifiedNameTest(String string, QualifiedName expected) {
        var qualifiedName = qualifiedNameConverter.toQualifiedName(string);
        assertThat(qualifiedName).isEqualTo(expected);
    }

}

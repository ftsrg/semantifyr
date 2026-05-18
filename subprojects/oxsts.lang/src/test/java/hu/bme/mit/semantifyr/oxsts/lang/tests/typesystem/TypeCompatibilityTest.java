/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.typesystem;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ImmutableTypeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.TypeCompatibility;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.TypeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.WrappedOxstsPackage;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class TypeCompatibilityTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private TypeCompatibility typeCompatibility;

    @Inject
    private BuiltinSymbolResolver builtinSymbolResolver;

    @Inject
    private ExpressionTypeEvaluatorProvider typeEvaluatorProvider;

    @Test
    void anyIsSupertypeOfEveryPrimitive() {
        var pkg = parse("""
            package test
            class C { var x: int := 0 }
            """);
        var context = pkg.getOxstsPackage();

        var anyType = typeOf(builtinSymbolResolver.anyDatatype(context));
        var intType = typeOf(builtinSymbolResolver.intDatatype(context));
        var boolType = typeOf(builtinSymbolResolver.boolDatatype(context));
        var realType = typeOf(builtinSymbolResolver.realDatatype(context));

        assertThat(typeCompatibility.isAssignable(anyType, intType, context)).isTrue();
        assertThat(typeCompatibility.isAssignable(anyType, boolType, context)).isTrue();
        assertThat(typeCompatibility.isAssignable(anyType, realType, context)).isTrue();
    }

    @Test
    void intNotAssignableToBool() {
        var pkg = parse("""
            package test
            class C { var x: int := 0 }
            """);
        var context = pkg.getOxstsPackage();

        var intType = typeOf(builtinSymbolResolver.intDatatype(context));
        var boolType = typeOf(builtinSymbolResolver.boolDatatype(context));

        assertThat(typeCompatibility.isAssignable(boolType, intType, context)).isFalse();
        assertThat(typeCompatibility.isAssignable(intType, boolType, context)).isFalse();
    }

    @Test
    void subclassAssignableToSuperclass() {
        var pkg = parse("""
            package test
            class A { }
            class B : A { }
            class C : B { }
            """);
        var context = pkg.getOxstsPackage();
        var a = typeOf(pkg.classByName("A").eObject());
        var b = typeOf(pkg.classByName("B").eObject());
        var c = typeOf(pkg.classByName("C").eObject());

        // C -> B -> A : walking up the chain is allowed.
        assertThat(typeCompatibility.isAssignable(a, b, context))
                .as("B should be assignable to A (direct supertype)")
                .isTrue();
        assertThat(typeCompatibility.isAssignable(a, c, context))
                .as("C should be assignable to A (transitive supertype)")
                .isTrue();
        assertThat(typeCompatibility.isAssignable(b, c, context))
                .as("C should be assignable to B")
                .isTrue();

        // Reverse direction should fail.
        assertThat(typeCompatibility.isAssignable(b, a, context))
                .as("A should NOT be assignable to B")
                .isFalse();
        assertThat(typeCompatibility.isAssignable(c, a, context))
                .as("A should NOT be assignable to C")
                .isFalse();
    }

    @Test
    void featureSubsetChainIsAssignable() {
        var pkg = parse("""
            package test
            class Item { }
            class Holder {
                features allItems: Item[0..*]
                contains priority: Item[1] subsets allItems
                contains veryImportant: Item[1] subsets priority
            }
            """);
        var context = pkg.getOxstsPackage();
        var all = typeOf(pkg.classByName("Holder").featureByName("allItems").eObject());
        var priority =
                typeOf(pkg.classByName("Holder").featureByName("priority").eObject());
        var important =
                typeOf(pkg.classByName("Holder").featureByName("veryImportant").eObject());

        // Subset chain: veryImportant -> priority -> allItems.
        assertThat(typeCompatibility.isAssignable(all, priority, context)).isTrue();
        assertThat(typeCompatibility.isAssignable(all, important, context))
                .as("veryImportant should be assignable to allItems transitively")
                .isTrue();
        assertThat(typeCompatibility.isAssignable(priority, important, context)).isTrue();

        // Reverse is not allowed.
        assertThat(typeCompatibility.isAssignable(important, priority, context)).isFalse();
    }

    @Test
    void dataFeatureUnwrapsToUnderlyingType() {
        var pkg = parse("""
            package test
            class C {
                refers size: int = 3
                var n: int := size
            }
            """);
        var context = pkg.getOxstsPackage();
        var intType = typeOf(builtinSymbolResolver.intDatatype(context));
        var sizeExpr = pkg.classByName("C").variableByName("n").initializer();
        var sizeType = typeEvaluatorProvider.evaluate(sizeExpr);

        // `size` types as the feature; unwrap should make it int-compatible.
        assertThat(typeCompatibility.isAssignable(intType, sizeType, context))
                .as("`size` (feature-typed as int) should be assignable to int")
                .isTrue();
        assertThat(typeCompatibility.isNumeric(sizeType, context))
                .as("`size` should be numeric via data-feature unwrap")
                .isTrue();
    }

    @Test
    void unrelatedClassesNotAssignable() {
        var pkg = parse("""
            package test
            class A { }
            class X { }
            """);
        var context = pkg.getOxstsPackage();
        var a = typeOf(pkg.classByName("A").eObject());
        var x = typeOf(pkg.classByName("X").eObject());

        assertThat(typeCompatibility.isAssignable(a, x, context)).isFalse();
        assertThat(typeCompatibility.isAssignable(x, a, context)).isFalse();
    }

    @Test
    void numericPredicateMatchesIntRealAny() {
        var pkg = parse("""
            package test
            class C { var x: int := 0 }
            """);
        var context = pkg.getOxstsPackage();

        assertThat(typeCompatibility.isNumeric(typeOf(builtinSymbolResolver.intDatatype(context)), context))
                .isTrue();
        assertThat(typeCompatibility.isNumeric(typeOf(builtinSymbolResolver.realDatatype(context)), context))
                .isTrue();
        assertThat(typeCompatibility.isNumeric(typeOf(builtinSymbolResolver.anyDatatype(context)), context))
                .isTrue();
        assertThat(typeCompatibility.isNumeric(typeOf(builtinSymbolResolver.boolDatatype(context)), context))
                .isFalse();
        assertThat(typeCompatibility.isNumeric(typeOf(builtinSymbolResolver.stringDatatype(context)), context))
                .isFalse();
    }

    @Test
    void booleanPredicateMatchesBoolAny() {
        var pkg = parse("""
            package test
            class C { var x: int := 0 }
            """);
        var context = pkg.getOxstsPackage();

        assertThat(typeCompatibility.isBoolean(typeOf(builtinSymbolResolver.boolDatatype(context)), context))
                .isTrue();
        assertThat(typeCompatibility.isBoolean(typeOf(builtinSymbolResolver.anyDatatype(context)), context))
                .isTrue();
        assertThat(typeCompatibility.isBoolean(typeOf(builtinSymbolResolver.intDatatype(context)), context))
                .isFalse();
    }

    private WrappedOxstsPackage parse(String source) {
        var pkg = parseHelper.parse(source);
        pkg.assertNoResourceErrors();
        return pkg;
    }

    private TypeEvaluation typeOf(EObject domain) {
        return new ImmutableTypeEvaluation((hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration) domain);
    }
}

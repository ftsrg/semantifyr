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
        var parsedPackage = parse("""
            package test
            class Counter { var count: int := 0 }
            """);
        var context = parsedPackage.getOxstsPackage();

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
        var parsedPackage = parse("""
            package test
            class Counter { var count: int := 0 }
            """);
        var context = parsedPackage.getOxstsPackage();

        var intType = typeOf(builtinSymbolResolver.intDatatype(context));
        var boolType = typeOf(builtinSymbolResolver.boolDatatype(context));

        assertThat(typeCompatibility.isAssignable(boolType, intType, context)).isFalse();
        assertThat(typeCompatibility.isAssignable(intType, boolType, context)).isFalse();
    }

    @Test
    void subclassAssignableToSuperclass() {
        var parsedPackage = parse("""
            package test
            class Vehicle { }
            class Car : Vehicle { }
            class SportsCar : Car { }
            """);
        var context = parsedPackage.getOxstsPackage();
        var vehicle = typeOf(parsedPackage.classByName("Vehicle").eObject());
        var car = typeOf(parsedPackage.classByName("Car").eObject());
        var sportsCar = typeOf(parsedPackage.classByName("SportsCar").eObject());

        assertThat(typeCompatibility.isAssignable(vehicle, car, context))
                .as("Car should be assignable to Vehicle (direct supertype)")
                .isTrue();
        assertThat(typeCompatibility.isAssignable(vehicle, sportsCar, context))
                .as("SportsCar should be assignable to Vehicle (transitive supertype)")
                .isTrue();
        assertThat(typeCompatibility.isAssignable(car, sportsCar, context))
                .as("SportsCar should be assignable to Car")
                .isTrue();

        // Reverse direction should fail.
        assertThat(typeCompatibility.isAssignable(car, vehicle, context))
                .as("Vehicle should NOT be assignable to Car")
                .isFalse();
        assertThat(typeCompatibility.isAssignable(sportsCar, vehicle, context))
                .as("Vehicle should NOT be assignable to SportsCar")
                .isFalse();
    }

    @Test
    void featureSubsetChainIsAssignable() {
        var parsedPackage = parse("""
            package test
            class Item { }
            class Holder {
                features allItems: Item[0..*]
                contains priority: Item[1] subsets allItems
                contains veryImportant: Item[1] subsets priority
            }
            """);
        var context = parsedPackage.getOxstsPackage();
        var all = typeOf(
                parsedPackage.classByName("Holder").featureByName("allItems").eObject());
        var priority = typeOf(
                parsedPackage.classByName("Holder").featureByName("priority").eObject());
        var important = typeOf(parsedPackage
                .classByName("Holder")
                .featureByName("veryImportant")
                .eObject());

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
        var parsedPackage = parse("""
            package test
            class Buffer {
                refers size: int = 3
                var capacity: int := size
            }
            """);
        var context = parsedPackage.getOxstsPackage();
        var intType = typeOf(builtinSymbolResolver.intDatatype(context));
        var sizeExpression =
                parsedPackage.classByName("Buffer").variableByName("capacity").initializer();
        var sizeType = typeEvaluatorProvider.evaluate(sizeExpression);

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
        var parsedPackage = parse("""
            package test
            class Vehicle { }
            class Plant { }
            """);
        var context = parsedPackage.getOxstsPackage();
        var vehicle = typeOf(parsedPackage.classByName("Vehicle").eObject());
        var plant = typeOf(parsedPackage.classByName("Plant").eObject());

        assertThat(typeCompatibility.isAssignable(vehicle, plant, context)).isFalse();
        assertThat(typeCompatibility.isAssignable(plant, vehicle, context)).isFalse();
    }

    @Test
    void numericPredicateMatchesIntRealAny() {
        var parsedPackage = parse("""
            package test
            class Counter { var count: int := 0 }
            """);
        var context = parsedPackage.getOxstsPackage();

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
        var parsedPackage = parse("""
            package test
            class Counter { var count: int := 0 }
            """);
        var context = parsedPackage.getOxstsPackage();

        assertThat(typeCompatibility.isBoolean(typeOf(builtinSymbolResolver.boolDatatype(context)), context))
                .isTrue();
        assertThat(typeCompatibility.isBoolean(typeOf(builtinSymbolResolver.anyDatatype(context)), context))
                .isTrue();
        assertThat(typeCompatibility.isBoolean(typeOf(builtinSymbolResolver.intDatatype(context)), context))
                .isFalse();
    }

    private WrappedOxstsPackage parse(String source) {
        var parsedPackage = parseHelper.parse(source);
        parsedPackage.assertNoResourceErrors();
        return parsedPackage;
    }

    private TypeEvaluation typeOf(EObject domain) {
        return new ImmutableTypeEvaluation((hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration) domain);
    }
}

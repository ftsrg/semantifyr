/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.syntax;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class FeatureSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void containmentKind() {
        assertFeatureKind("contains leaves: Leaf[0..*]", FeatureKind.CONTAINMENT);
    }

    @Test
    void containerKind() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf {
                container parent: Host[0..1] opposite children
            }
            class Host {
                contains children: Leaf[0..*] opposite parent
            }
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.classByName("Leaf").featureByName("parent").kind())
                .isEqualTo(FeatureKind.CONTAINER);
    }

    @Test
    void referenceKind() {
        assertFeatureKind("refers target: Leaf[0..1]", FeatureKind.REFERENCE);
    }

    @Test
    void derivedReferenceKind() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf {
                refers home: Host[0..1]
            }
            class Host {
                derived refers inhabitants: Leaf[0..*] opposite home
            }
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage
                        .classByName("Host")
                        .featureByName("inhabitants")
                        .kind())
                .isEqualTo(FeatureKind.DERIVED);
    }

    @Test
    void abstractFeatureKind() {
        assertFeatureKind("features slot: Leaf", FeatureKind.FEATURE);
    }

    private void assertFeatureKind(String declaration, FeatureKind expectedKind) {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf { }
            class Host { %s }
            """.formatted(declaration));
        parsedPackage.assertNoResourceErrors();
        var features = parsedPackage.classByName("Host").features();
        assertThat(features).hasSize(1);
        assertThat(features.getFirst().kind()).isEqualTo(expectedKind);
    }

    @Test
    void featureWithInlineExpressionInitializer() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer { refers size: int = 3 }
            """);
        parsedPackage.assertNoResourceErrors();
        var size = parsedPackage.classByName("Buffer").featureByName("size");
        assertThat(size.expression()).isInstanceOf(LiteralInteger.class);
    }

    @Test
    void featureWithSubsets() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Holder {
                contains all: Item[0..*]
                contains priority: Item[0..*] subsets all
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var priority = parsedPackage.classByName("Holder").featureByName("priority");
        assertThat(priority.superset()).isNotNull();
        assertThat(priority.superset().name()).isEqualTo("all");
    }

    @Test
    void featureWithOpposite() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Node {
                container parent: Node[0..1] opposite children
                contains children: Node[0..*] opposite parent
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var node = parsedPackage.classByName("Node");
        assertThat(node.featureByName("parent").opposite()).isNotNull();
        assertThat(node.featureByName("parent").opposite().name()).isEqualTo("children");
        assertThat(node.featureByName("children").opposite().name()).isEqualTo("parent");
    }

    @Test
    void featureWithNestedBlock() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf {
                refers tag: int = 0
            }
            class Host {
                contains leaf: Leaf[1] {
                    redefine refers tag: int = 5
                }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var leaf = parsedPackage.classByName("Host").featureByName("leaf");
        assertThat(leaf.innerFeatures()).hasSize(1);
        assertThat(leaf.innerFeatures().getFirst().name()).isEqualTo("tag");
        assertThat(leaf.innerFeatures().getFirst().isRedefine()).isTrue();
    }

    @Test
    void featureWithRedefineFlag() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker { }
            class Base { contains workers: Worker[0..*] }
            class Child : Base {
                redefine contains workers: Worker[1]
            }
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.classByName("Child").featureByName("workers").isRedefine())
                .isTrue();
    }

    @Test
    void featureWithAllThreeModifiers() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Base {
                contains items: Item[0..*]
                contains priority: Item[0..*] subsets items
                container holder: Holder[0..1]
            }
            class Holder {
                contains base: Base[1]
            }
            class Derived : Base {
                redefine contains priority: Item[0..*] subsets items redefines priority
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var priority = parsedPackage.classByName("Derived").featureByName("priority");
        assertThat(priority.isRedefine()).isTrue();
        assertThat(priority.superset()).isNotNull();
        assertThat(priority.superset().name()).isEqualTo("items");
    }
}

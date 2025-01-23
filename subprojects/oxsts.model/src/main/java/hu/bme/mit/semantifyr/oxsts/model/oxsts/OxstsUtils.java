/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.model.oxsts;

import org.eclipse.emf.ecore.EObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OxstsUtils {

    public static List<Element> getAccessibleElements(EObject element) {
        if (element == null) {
            return Collections.emptyList();
        }

        var parent = element.eContainer();
        var elements = new ArrayList<Element>();

        elements.addAll(getLocalAccessibleElements(element));
        elements.addAll(getAccessibleElements(parent));

        return elements;
    }

    public static List<Element> getLocalAccessibleElements(EObject element) {
        if (element == null) {
            return Collections.emptyList();
        }

        var elements = new ArrayList<Element>();

        switch (element) {
            case Package _package -> {
                elements.addAll(getLocalAccessibleElements(_package));
                elements.addAll(_package.getImports().stream().flatMap(it ->
                        getLocalAccessibleElements(it.getPackage()).stream()
                ).toList());
            }
            case Type type -> elements.addAll(getInheritedElements(type));
            case Feature feature -> elements.addAll(getInheritedElements(feature.getTyping()));
            case Parameter parameter -> elements.addAll(getInheritedElements(parameter.getType()));
            case Argument argument -> elements.addAll(getInheritedElements(argument.getTyping()));
            default -> {
            }
        }

        return elements;
    }

    public static List<Element> getLocalAccessibleElements(Package _package) {
        var elements = new ArrayList<Element>();
        elements.addAll(_package.getTypes().stream().map(it -> (Element) it).toList());
        elements.addAll(_package.getEnums().stream().map(it -> (Element) it).toList());
        elements.addAll(_package.getPatterns().stream().map(it -> (Element) it).toList());
        return elements;
    }

    public static List<Element> getInheritedElements(Typing typing) {
        var elements = new ArrayList<Element>();

        if (typing == null) {
            return elements;
        }

        if (typing instanceof ReferenceTyping referenceTyping) {
            var chain = referenceTyping.getReference();
            var lastExpression = chain.getChains().get(chain.getChains().size() - 1);
            var referencedElement = getReferredElement(lastExpression);
            if (referencedElement instanceof Type type) {
                elements.addAll(getInheritedElements(type));
            }
        }

        return elements;
    }

    public static List<Element> getInheritedElements(Type type) {
        if (type == null) {
            return List.of();
        }

        var supertype = type.getSupertype();
        var elements = new ArrayList<Element>();

        elements.addAll(type.getFeatures());
        elements.addAll(type.getVariables());
        elements.addAll(type.getProperties());
        elements.addAll(type.getTransitions());
        elements.addAll(type.getInitTransition());
        elements.addAll(type.getHavocTransition());
        elements.addAll(type.getMainTransition());

        elements.addAll(getInheritedElements(supertype));

        return elements;
    }

    public static Element getReferredElement(ReferenceExpression expression) {
        if (expression instanceof DeclarationReferenceExpression declarationReference) {
            return declarationReference.getElement();
        } else if (expression instanceof ChainReferenceExpression chainReference) {
            return getReferredElement(chainReference.getChains().getLast());
        } else {
            return null;
        }
    }

    public static Element getReferredElement(ChainingExpression expression) {
        if (expression instanceof DeclarationReferenceExpression declarationReferenceExpression) {
            return declarationReferenceExpression.getElement();
        }

        throw new IllegalStateException("");
    }

}

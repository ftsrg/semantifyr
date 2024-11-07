/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Package;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class contains custom scoping description.
 * <p>
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#scoping
 * on how and when to use it.
 */
public class OxstsScopeProvider extends AbstractOxstsScopeProvider {

    private boolean isTypeReference(EReference reference) {
        return reference == OxstsPackage.Literals.TYPE__SUPERTYPE ||
                reference == OxstsPackage.Literals.FEATURE__TYPING ||
                reference == OxstsPackage.Literals.REFERENCE_TYPING__REFERENCE ||
                reference == OxstsPackage.Literals.TYPE_CONSTRAINT__TYPE ||
                reference == OxstsPackage.Literals.FEATURE_CONSTRAINT__TYPE ||
                reference == OxstsPackage.Literals.PARAMETER__TYPE;
    }

    @Override
    public IScope getScope(EObject context, EReference reference) {
        if (reference == OxstsPackage.Literals.IMPORT__PACKAGE) {
            return super.getScope(context, reference);
        }

        if (isTypeReference(reference)) {
            var _package = EcoreUtil2.getContainerOfType(context, Package.class);
            return scopeElement(_package, reference);
        }

        if (context instanceof ChainingExpression chain) {
            return calculateChainScope(chain, reference);
        }

        if (context instanceof FeatureConstraint featureConstraint) {
            return calculateFeatureConstraintScope(featureConstraint, reference);
        }

        return scopeElement(context, reference);
    }

    protected IScope calculateChainScope(ChainingExpression expression, EReference reference) {
        var chain = EcoreUtil2.getContainerOfType(expression, ChainReferenceExpression.class);

        var inlineComposite = EcoreUtil2.getContainerOfType(expression, InlineComposite.class);

        var index = chain.getChains().indexOf(expression);

        if (inlineComposite != null && inlineComposite.getTransition() == chain) {
            // calculate reference pretending to be from the feature's poing of view
            if (index <= 0) {
                return scopeElement(getReferredElement(inlineComposite.getFeature()), reference);
            }
        }

        if (index <= 0) {
            var referenceTyping = EcoreUtil2.getContainerOfType(expression, ReferenceTyping.class);

            if (referenceTyping != null) {
                return scopeElement(referenceTyping.eContainer().eContainer(), reference);
            }

            return scopeElement(chain, reference);
        }

        var lastExpression = chain.getChains().get(index - 1);
        var referencedElement = getReferredElement(lastExpression);

        return scopeElement(referencedElement, reference);
    }

    protected IScope calculateFeatureConstraintScope(FeatureConstraint featureConstraint, EReference reference) {
        return scopeElement(featureConstraint.getType(), reference);
    }

    private String customNameProvider(EObject eObject) {
        var element = (Element) eObject;

        if (element instanceof Transition transition) {
            var baseType = EcoreUtil2.getContainerOfType(transition, BaseType.class);
            if (baseType.getMainTransition().contains(transition)) {
                return "main";
            } else if (baseType.getInitTransition().contains(transition)) {
                return "init";
            } else if (baseType.getHavocTransition().contains(transition)) {
                return "havoc";
            }
        }

        return element.getName();
    }

    protected IScope scopeElement(EObject element, EReference reference) {
        var referenceClass = reference.getEReferenceType().getInstanceClass();
        var accessibleElements = getAccessibleElements(element).stream().filter(referenceClass::isInstance).toList();

        return Scopes.scopeFor(accessibleElements, QualifiedName.wrapper(this::customNameProvider), super.getScope(element, reference));
    }

    protected List<Element> getAccessibleElements(EObject element) {
        if (element == null) {
            return Collections.emptyList();
        }

        var parent = element.eContainer();
        var elements = new ArrayList<Element>();

        if (element instanceof Package _package) {
            elements.addAll(getLocalScope(_package));
            elements.addAll(_package.getImports().stream().flatMap(it ->
                    getLocalScope(it.getPackage()).stream()
            ).toList());
        } else if (element instanceof Type type) {
            elements.addAll(getInheritedElements(type));
        } else if (element instanceof Feature feature) {
            elements.addAll(getInheritedElements(feature.getTyping()));
        } else if (element instanceof Parameter parameter) {
            elements.addAll(getInheritedElements(parameter.getType()));
        } else if (element instanceof Argument argument) {
            elements.addAll(getInheritedElements(argument.getTyping()));
        }

        elements.addAll(getAccessibleElements(parent));

        return elements;
    }

    protected List<Element> getLocalScope(Package _package) {
        var elements = new ArrayList<Element>();
        elements.addAll(_package.getTypes().stream().map(it -> (Element) it).toList());
        elements.addAll(_package.getEnums().stream().map(it -> (Element) it).toList());
        elements.addAll(_package.getPatterns().stream().map(it -> (Element) it).toList());
        return elements;
    }

    protected List<Element> getInheritedElements(Typing typing) {
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

    protected List<Element> getInheritedElements(Type type) {
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

    protected Element getReferredElement(ReferenceExpression expression) {
        if (expression instanceof DeclarationReferenceExpression declarationReference) {
            return declarationReference.getElement();
        } else if (expression instanceof ChainReferenceExpression chainReference) {
            return getReferredElement(chainReference.getChains().get(chainReference.getChains().size() - 1));
        } else {
            return null;
        }
    }

    protected Element getReferredElement(ChainingExpression expression) {
        if (expression instanceof DeclarationReferenceExpression declarationReferenceExpression) {
            return declarationReferenceExpression.getElement();
        }

        throw new IllegalStateException("");
    }

}

/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
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

import static hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsUtils.getAccessibleElements;
import static hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsUtils.getReferredElement;

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
        var accessibleElements = getAccessibleElements(element).filter(referenceClass::isInstance).toList();

        return Scopes.scopeFor(accessibleElements, QualifiedName.wrapper(this::customNameProvider), super.getScope(element, reference));
    }

}

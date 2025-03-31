/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsNameProvider;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Package;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.IScopeProvider;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.AbstractDeclarativeScopeProvider;

import java.util.stream.Stream;

public class OxstsScopeProvider implements IScopeProvider {

    @Inject
    @Named(AbstractDeclarativeScopeProvider.NAMED_DELEGATE)
    private IScopeProvider localScopeProvider;

    @Inject
    private OxstsNameProvider oxstsNameProvider;

    @Inject
    private OxstsInheritanceAwareScopeComputor oxstsInheritanceAwareScopeComputor;

    @Override
    public IScope getScope(EObject context, EReference reference) {
        if (context == null || context.eResource() == null) {
            return IScope.NULLSCOPE;
        }

        try {
            return calculateScope(context, reference);
        } catch (RuntimeException e) {
            return IScope.NULLSCOPE;
        }
    }

    protected IScope calculateScope(EObject context, EReference reference) {
        if (isTypeReference(reference)) {
            return calculateTypeScope(context, reference);
        }

        if (isTransitionReference(reference)) {
            return calculateTransitionScope(context, reference);
        }

        if (reference == OxstsPackage.Literals.FEATURE_CONSTRAINT__FEATURE) {
            return calculateFeatureConstraintFeatureScope(context, reference);
        }

        if (isFeatureReference(reference)) {
            return calculateFeatureScope(context, reference);
        }

        if (isPatternReference(reference)) {
            return calculatePatternScope(context, reference);
        }

        if (context instanceof ElementReferenceExpression elementReference) {
            var typing = EcoreUtil2.getContainerOfType(context, ReferenceTyping.class);

            if (typing != null) {
                return calculateTypingChainScope(typing, elementReference, reference);
            }

            return calculateSimpleChainScope(elementReference, reference);
        }

        if (context instanceof ChainingExpression chainingExpression) {
            var typing = EcoreUtil2.getContainerOfType(context, ReferenceTyping.class);

            if (typing != null) {
                return calculateTypingChainScope(typing, chainingExpression, reference);
            }

            return calculateSimpleChainScope(chainingExpression, reference);
        }

        return calculateOuterScope(context, reference);
    }

    protected IScope calculateTypeScope(EObject context, EReference reference) {
        var _package = EcoreUtil2.getContainerOfType(context, Package.class);
        var types = OxstsUtils.getAllTypesTransitive(_package);

        return scopeFor(types, context, reference);
    }

    protected IScope calculateTransitionScope(EObject context, EReference reference) {
        if (context instanceof InlineComposite composite) {
            var transitionScope = composite.getReference().getChains().getLast().getElement();
            var transitions = oxstsInheritanceAwareScopeComputor.getAllInheritedTransitions(transitionScope);
            return scopeFor(transitions);
        }

        if (context instanceof InlineCall call) {
            if (call.getReference() != null) {
                var transitionScope = call.getReference().getChains().getLast().getElement();
                var transitions = oxstsInheritanceAwareScopeComputor.getAllInheritedTransitions(transitionScope);
                return scopeFor(transitions);
            }

            var transitionScope = EcoreUtil2.getContainerOfType(call, Type.class);
            var transitions = oxstsInheritanceAwareScopeComputor.getAllInheritedTransitions(transitionScope);
            return scopeFor(transitions, context, reference);
        }

        return IScope.NULLSCOPE;
    }

    protected IScope calculateFeatureConstraintFeatureScope(EObject context, EReference reference) {
        var featureConstraint = (FeatureConstraint) context;
        var features = oxstsInheritanceAwareScopeComputor.getAllInheritedFeatures(featureConstraint.getType());

        return scopeFor(features);
    }

    protected IScope calculateFeatureScope(EObject context, EReference reference) {
        var container = (Element) context.eContainer();
        var features = oxstsInheritanceAwareScopeComputor.getAllInheritedFeatures(container);

        return scopeFor(features);
    }

    protected IScope calculatePatternScope(EObject context, EReference reference) {
        var _package = EcoreUtil2.getContainerOfType(context, Package.class);
        var patterns = OxstsUtils.getAllPatternsTransitive(_package);

        return scopeFor(patterns, context, reference);
    }

    protected IScope calculateSimpleChainScope(ElementReferenceExpression context, EReference reference) {
        var chain = EcoreUtil2.getContainerOfType(context, ChainingExpression.class);
        var index = chain.getChains().indexOf(context);

        if (index <= 0) {
            return scopeFor(streamAllAccessibleElements(context).toList(), context, reference);
        }

        var lastExpression = chain.getChains().get(index - 1);
        var referencedElement = lastExpression.getElement();

        return scopeFor(oxstsInheritanceAwareScopeComputor.getAllInheritedElements(referencedElement));
    }

    protected IScope calculateSimpleChainScope(ChainingExpression chain, EReference reference) {
        // last resolved element!
        var index = chain.getChains().size();

        if (index <= 0) {
            return scopeFor(streamAllAccessibleElements(chain).toList(), chain, reference);
        }

        var lastExpression = chain.getChains().get(index - 1);
        var referencedElement = lastExpression.getElement();

        return scopeFor(oxstsInheritanceAwareScopeComputor.getAllInheritedElements(referencedElement));
    }

    protected IScope calculateTypingChainScope(ReferenceTyping typing, ElementReferenceExpression context, EReference reference) {
        var chain = EcoreUtil2.getContainerOfType(context, ChainingExpression.class);
        var index = chain.getChains().indexOf(context);

        if (index <= 0) {
            var parentOfVarOrFeature = (Element) typing.eContainer().eContainer();
            return scopeFor(
                    streamAllAccessibleElements(parentOfVarOrFeature)
                            .filter(e -> e instanceof Type || e instanceof Feature)
                            .toList(),
                    context,
                    reference
            );
        } else {
            var lastExpression = chain.getChains().get(index - 1);
            var referencedElement = lastExpression.getElement();

            return scopeFor(oxstsInheritanceAwareScopeComputor.getAllInheritedFeatures(referencedElement));
        }
    }

    protected IScope calculateTypingChainScope(ReferenceTyping typing, ChainingExpression chain, EReference reference) {
        // last resolved element!
        var index = chain.getChains().size();

        if (index <= 0) {
            var parentOfVarOrFeature = (Element) typing.eContainer().eContainer();
            return scopeFor(
                    streamAllAccessibleElements(parentOfVarOrFeature)
                            .filter(e -> e instanceof Type || e instanceof Feature)
                            .toList(),
                    chain,
                    reference
            );
        } else {
            var lastExpression = chain.getChains().get(index - 1);
            var referencedElement = lastExpression.getElement();

            return scopeFor(oxstsInheritanceAwareScopeComputor.getAllInheritedFeatures(referencedElement));
        }
    }

    protected Stream<Element> streamAllAccessibleElements(Element scope) {
        return OxstsUtils.streamAllContainerNamespaces(scope).flatMap(ns -> oxstsInheritanceAwareScopeComputor.getAccessibleElements(ns).stream());
    }

    protected IScope scopeFor(Iterable<? extends Element> elements) {
        return scopeFor(elements, IScope.NULLSCOPE);
    }

    protected IScope scopeFor(Iterable<? extends Element> elements, EObject context, EReference reference) {
        return scopeFor(elements, localScopeProvider.getScope(context, reference));
    }

    protected IScope scopeFor(Iterable<? extends Element> elements, IScope outer) {
        return Scopes.scopeFor(elements, QualifiedName.wrapper(oxstsNameProvider), outer);
    }

    protected IScope calculateOuterScope(EObject element, EReference reference) {
        if (element == null || element.eResource() == null) {
            return IScope.NULLSCOPE;
        }

        return localScopeProvider.getScope(element, reference);
    }

    private boolean isTypeReference(EReference reference) {
        return reference == OxstsPackage.Literals.TYPE__SUPERTYPE ||
                reference == OxstsPackage.Literals.TYPE_CONSTRAINT__TYPE ||
                reference == OxstsPackage.Literals.FEATURE_CONSTRAINT__TYPE ||
                reference == OxstsPackage.Literals.PARAMETER__TYPE;
    }

    private boolean isPatternReference(EReference reference) {
        return reference == OxstsPackage.Literals.DERIVED__PATTERN ||
                reference == OxstsPackage.Literals.PATTERN_CONSTRAINT__PATTERN;
    }

    private boolean isTransitionReference(EReference reference) {
        return reference == OxstsPackage.Literals.INLINE_COMPOSITE__TRANSITION ||
                reference == OxstsPackage.Literals.INLINE_CALL__TRANSITION;
    }

    private boolean isFeatureReference(EReference reference) {
        return reference == OxstsPackage.Literals.FEATURE__SUBSETS ||
                reference == OxstsPackage.Literals.FEATURE__REDEFINES;
    }

}

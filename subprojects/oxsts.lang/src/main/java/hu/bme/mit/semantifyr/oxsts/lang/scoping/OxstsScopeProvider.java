/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ImmutableTypeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.domain.DomainMemberCalculator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.ISelectable;
import org.eclipse.xtext.scoping.ICaseInsensitivityHelper;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.SelectableBasedScope;

public class OxstsScopeProvider extends AbstractOxstsScopeProvider {

    @Inject
    private ExpressionTypeEvaluatorProvider expressionTypeEvaluatorProvider;

    @Inject
    private DomainMemberCalculator domainMemberCalculator;

    @Inject
    private ICaseInsensitivityHelper caseInsensitivityHelper;

    @Override
    public IScope getScope(EObject context, EReference reference) {
        if (context == null || context.eResource() == null) {
            return IScope.NULLSCOPE;
        }

        try {
            return calculateScope(context, reference);
        } catch (RuntimeException e) {
            // FIXME: should log here
            e.printStackTrace();
            return IScope.NULLSCOPE;
        }
    }

    protected IScope calculateScope(EObject context, EReference reference) {
        if (reference == OxstsPackage.Literals.FEATURE_DECLARATION__SUPERSET) {
            return calculateFeatureSuperSetsScope(context, reference);
        }

        if (reference == OxstsPackage.Literals.REDEFINABLE_DECLARATION__REDEFINED) {
            return calculateFeatureRedefinedScope(context, reference);
        }

        if (reference == OxstsPackage.Literals.FEATURE_DECLARATION__OPPOSITE) {
            return calculateOppositeScope(context, reference);
        }

        if (reference == OxstsPackage.Literals.FEATURE_DECLARATION__TYPE
            || reference == OxstsPackage.Literals.VARIABLE_DECLARATION__TYPE) {
            return super.getScope(context, reference);
        }

        if (reference == OxstsPackage.Literals.ARGUMENT__PARAMETER) {
            var parent = context.eContainer();

            if (parent instanceof Annotation annotation) {
                var declaration = annotation.getDeclaration();

                return scopeFor(declaration.getParameters());
            } else if (parent instanceof CallSuffixExpression callExpression) {
//                var expression = callExpression.getExpression(); // TODO: implement static evaluator

                return IScope.NULLSCOPE;
            }

            return IScope.NULLSCOPE;
        }

        if (context instanceof NavigationSuffixExpression navigationSuffixExpression) {
            var primary = navigationSuffixExpression.getPrimary();
            var primaryEvaluation = expressionTypeEvaluatorProvider.evaluate(primary);

            if (primaryEvaluation instanceof ImmutableTypeEvaluation(DomainDeclaration domainDeclaration)) {
                var members = domainMemberCalculator.getMembers(domainDeclaration);
                return scopeFor(reference, members);
            }

            return IScope.NULLSCOPE;
        }

        return super.getScope(context, reference);
    }

    protected IScope calculateOppositeScope(EObject context, EReference reference) {
        var featureDeclaration = EcoreUtil2.getContainerOfType(context, FeatureDeclaration.class);
        if (featureDeclaration == null) {
            return IScope.NULLSCOPE;
        }
        var type = featureDeclaration.getType();
        if (!(type instanceof ClassDeclaration classDeclaration)) {
            return IScope.NULLSCOPE;
        }

        return scopeFor(reference, domainMemberCalculator.getMembers(classDeclaration));
    }

    protected IScope calculateFeatureSuperSetsScope(EObject context, EReference reference) {
        var container = (DomainDeclaration) context.eContainer();

        return scopeFor(reference, domainMemberCalculator.getMembers(container));
    }

    protected IScope calculateFeatureRedefinedScope(EObject context, EReference reference) {
        var container = (DomainDeclaration) context.eContainer();

        return scopeFor(reference, domainMemberCalculator.getParentCollection(container).getMembers());
    }

    protected IScope scopeFor(EReference reference, ISelectable selectable) {
        return SelectableBasedScope.createScope(IScope.NULLSCOPE, selectable, reference.getEReferenceType(), caseInsensitivityHelper.isIgnoreCase(reference));
    }

    protected IScope scopeFor(Iterable<? extends Element> elements) {
        return Scopes.scopeFor(elements, QualifiedName.wrapper(NamingUtil::getName), IScope.NULLSCOPE);
    }

}

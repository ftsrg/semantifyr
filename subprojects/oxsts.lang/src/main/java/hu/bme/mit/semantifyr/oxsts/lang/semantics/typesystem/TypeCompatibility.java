/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import org.eclipse.emf.ecore.EObject;

public class TypeCompatibility {

    @Inject
    private BuiltinSymbolResolver builtinSymbolResolver;

    public boolean isEnumType(TypeEvaluation typeEvaluation) {
        return typeEvaluation != null && typeEvaluation.getDomain() instanceof EnumDeclaration;
    }

    public boolean isDataType(TypeEvaluation typeEvaluation) {
        return typeEvaluation != null && typeEvaluation.getDomain() instanceof DataTypeDeclaration;
    }

    public boolean isClassType(TypeEvaluation typeEvaluation) {
        return typeEvaluation != null && typeEvaluation.getDomain() instanceof ClassDeclaration;
    }

    public boolean isFeatureType(TypeEvaluation typeEvaluation) {
        return typeEvaluation != null && typeEvaluation.getDomain() instanceof FeatureDeclaration;
    }

    public boolean isAny(TypeEvaluation typeEvaluation, EObject context) {
        return isDomain(typeEvaluation, builtinSymbolResolver.anyDatatype(context));
    }

    public boolean isInt(TypeEvaluation typeEvaluation, EObject context) {
        return isDomain(typeEvaluation, builtinSymbolResolver.intDatatype(context));
    }

    public boolean isBool(TypeEvaluation typeEvaluation, EObject context) {
        return isDomain(typeEvaluation, builtinSymbolResolver.boolDatatype(context));
    }

    public boolean isReal(TypeEvaluation typeEvaluation, EObject context) {
        return isDomain(typeEvaluation, builtinSymbolResolver.realDatatype(context));
    }

    public boolean isString(TypeEvaluation typeEvaluation, EObject context) {
        return isDomain(typeEvaluation, builtinSymbolResolver.stringDatatype(context));
    }

    public boolean isDomain(TypeEvaluation typeEvaluation, DomainDeclaration expected) {
        if (typeEvaluation == null || expected == null) {
            return false;
        }
        return typeEvaluation.getDomain() == expected;
    }

    public boolean isNumeric(TypeEvaluation typeEvaluation, EObject context) {
        if (typeEvaluation instanceof InvalidTypeEvaluation) {
            return true; // don't cascade
        }
        if (isInt(typeEvaluation, context) || isReal(typeEvaluation, context) || isAny(typeEvaluation, context)) {
            return true;
        }

        var unwrapped = unwrappedDomain(typeEvaluation);
        return unwrapped == builtinSymbolResolver.intDatatype(context)
                || unwrapped == builtinSymbolResolver.realDatatype(context)
                || unwrapped == builtinSymbolResolver.anyDatatype(context);
    }

    public boolean isBoolean(TypeEvaluation typeEvaluation, EObject context) {
        if (typeEvaluation instanceof InvalidTypeEvaluation) {
            return true;
        }
        if (isBool(typeEvaluation, context) || isAny(typeEvaluation, context)) {
            return true;
        }

        var unwrapped = unwrappedDomain(typeEvaluation);
        return unwrapped == builtinSymbolResolver.boolDatatype(context)
                || unwrapped == builtinSymbolResolver.anyDatatype(context);
    }

    public DomainDeclaration unwrappedDomain(TypeEvaluation typeEvaluation) {
        if (typeEvaluation == null || typeEvaluation.getDomain() == null) {
            return null;
        }
        var domain = typeEvaluation.getDomain();
        if (domain instanceof FeatureDeclaration feature && feature.getTypeSpecification() != null) {
            return feature.getTypeSpecification().getDomain();
        }
        return domain;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isComparable(TypeEvaluation left, TypeEvaluation right, EObject context) {
        if (left instanceof InvalidTypeEvaluation || right instanceof InvalidTypeEvaluation) {
            return true;
        }
        if (left instanceof NothingTypeEvaluation || right instanceof NothingTypeEvaluation) {
            return true;
        }

        return isDomainAssignable(left.getDomain(), right.getDomain(), context)
                || isDomainAssignable(right.getDomain(), left.getDomain(), context);
    }

    /**
     * Returns {@code true} if a value of the {@code assigned} type can be assigned into a location
     * typed as {@code assignee}. Checks domain compatibility plus multiplicity (can an optional
     * slot accept nothing? can a one slot accept a some? etc.).
     */
    public boolean isAssignable(TypeEvaluation assignee, TypeEvaluation assigned, EObject context) {
        if (assignee instanceof InvalidTypeEvaluation || assigned instanceof InvalidTypeEvaluation) {
            return true;
        }
        if (assigned instanceof NothingTypeEvaluation) {
            return assignee.getRange() != null && assignee.getRange().getLowerBound() == 0;
        }
        if (!isDomainAssignable(assignee.getDomain(), assigned.getDomain(), context)) {
            return false;
        }
        return isMultiplicityAssignable(assignee.getRange(), assigned.getRange());
    }

    public boolean isDomainAssignable(DomainDeclaration assignee, DomainDeclaration assigned, EObject context) {
        if (assignee == null || assigned == null) {
            return true;
        }
        if (isAnyDomain(assignee, context)) {
            return true;
        }
        if (assignee == assigned) {
            return true;
        }
        if (assignee instanceof ClassDeclaration assigneeClass && assigned instanceof ClassDeclaration assignedClass) {
            return isSubclassOf(assignedClass, assigneeClass);
        }
        if (assignee instanceof FeatureDeclaration assigneeFeat
                && assigned instanceof FeatureDeclaration assignedFeat) {
            return isFeatureSubsetOf(assignedFeat, assigneeFeat);
        }
        // Mixed class / feature: unwrap one level and retry. A data-typed
        // feature (refers x: int = 3) is interchangeable with its int.
        var assigneeUnwrapped = unwrap(assignee);
        var assignedUnwrapped = unwrap(assigned);
        if (assigneeUnwrapped != assignee || assignedUnwrapped != assigned) {
            return isDomainAssignable(assigneeUnwrapped, assignedUnwrapped, context);
        }
        return false;
    }

    private DomainDeclaration unwrap(DomainDeclaration domain) {
        if (domain instanceof FeatureDeclaration feature && feature.getTypeSpecification() != null) {
            return feature.getTypeSpecification().getDomain();
        }
        return domain;
    }

    private boolean isMultiplicityAssignable(RangeEvaluation assignee, RangeEvaluation assigned) {
        if (assignee == null || assigned == null) {
            return true;
        }
        var assigneeLowerBound = assignee.getLowerBound();
        var assigneeUpperBound = assignee.getUpperBound();
        var assignedLowerBound = assigned.getLowerBound();
        var assignedUpperBound = assigned.getUpperBound();

        if (assigneeUpperBound != RangeEvaluation.INFINITY
                && assignedUpperBound != RangeEvaluation.INFINITY
                && assignedUpperBound > assigneeUpperBound) {
            return false;
        }

        return assignedLowerBound >= assigneeLowerBound;
    }

    private boolean isSubclassOf(ClassDeclaration candidate, ClassDeclaration target) {
        if (candidate == target) {
            return true;
        }
        for (var superType : candidate.getSuperTypes()) {
            if (isSubclassOf(superType, target)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFeatureSubsetOf(FeatureDeclaration candidate, FeatureDeclaration target) {
        if (candidate == target) {
            return true;
        }
        if (candidate.getSuperset() == null) {
            return false;
        }
        return isFeatureSubsetOf(candidate.getSuperset(), target);
    }

    private boolean isAnyDomain(DomainDeclaration domain, EObject context) {
        return domain == builtinSymbolResolver.anyDatatype(context);
    }
}

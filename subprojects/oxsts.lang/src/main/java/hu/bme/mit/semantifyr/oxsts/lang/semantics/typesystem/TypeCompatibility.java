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
        if (isInt(typeEvaluation, context)
                || isReal(typeEvaluation, context)
                || isAny(typeEvaluation, context)) {
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

    /**
     * {@code true} if the two operands live in the same lattice family,
     * ignoring multiplicity. Comparison is not unsafe - it always returns
     * a boolean - so we do NOT require the stricter {@link #isAssignable}
     * check. The rule: the two domains must be in a subtype relation
     * (either direction). Unrelated domains (e.g. {@code int} vs
     * {@code bool}, or unrelated class hierarchies) are still rejected
     * because the comparison is trivially false and is almost always a
     * bug. This could be relaxed further to "always allow" in the future
     * if that turns out to be the better default.
     */
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
     * Returns {@code true} if the {@code source} type can be assigned into a
     * location typed as {@code target}. Checks domain compatibility plus
     * multiplicity (can an optional slot accept nothing? can a one slot
     * accept a some? etc.).
     */
    public boolean isAssignable(TypeEvaluation target, TypeEvaluation source, EObject context) {
        if (target instanceof InvalidTypeEvaluation || source instanceof InvalidTypeEvaluation) {
            return true;
        }
        if (source instanceof NothingTypeEvaluation) {
            return target.getRange() != null && target.getRange().getLowerBound() == 0;
        }
        if (!isDomainAssignable(target.getDomain(), source.getDomain(), context)) {
            return false;
        }
        return isMultiplicityAssignable(target.getRange(), source.getRange());
    }

    /**
     * Returns {@code true} if a value of {@code source} domain can be
     * assigned to a target of {@code target} domain under the supertype
     * lattice.
     */
    public boolean isDomainAssignable(DomainDeclaration target, DomainDeclaration source, EObject context) {
        if (target == null || source == null) {
            return true;
        }
        if (isAnyDomain(target, context)) {
            return true;
        }
        if (target == source) {
            return true;
        }
        if (target instanceof ClassDeclaration targetClass && source instanceof ClassDeclaration sourceClass) {
            return isSubclassOf(sourceClass, targetClass);
        }
        if (target instanceof FeatureDeclaration targetFeat && source instanceof FeatureDeclaration sourceFeat) {
            return isFeatureSubsetOf(sourceFeat, targetFeat);
        }
        // Mixed class / feature: unwrap one level and retry. A data-typed
        // feature (refers x: int = 3) is interchangeable with its int.
        var targetUnwrapped = unwrap(target);
        var sourceUnwrapped = unwrap(source);
        if (targetUnwrapped != target || sourceUnwrapped != source) {
            return isDomainAssignable(targetUnwrapped, sourceUnwrapped, context);
        }
        return false;
    }

    private DomainDeclaration unwrap(DomainDeclaration domain) {
        if (domain instanceof FeatureDeclaration feature && feature.getTypeSpecification() != null) {
            return feature.getTypeSpecification().getDomain();
        }
        return domain;
    }

    private boolean isMultiplicityAssignable(RangeEvaluation target, RangeEvaluation source) {
        if (target == null || source == null) {
            return true;
        }
        int targetLowerBound = target.getLowerBound();
        int targetUpperBound = target.getUpperBound();
        int sourceLowerBound = source.getLowerBound();
        int sourceUpperBound = source.getUpperBound();

        if (targetUpperBound != RangeEvaluation.INFINITY
                && sourceUpperBound != RangeEvaluation.INFINITY
                && sourceUpperBound > targetUpperBound) {
            return false;
        }

        return sourceLowerBound >= targetLowerBound;
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

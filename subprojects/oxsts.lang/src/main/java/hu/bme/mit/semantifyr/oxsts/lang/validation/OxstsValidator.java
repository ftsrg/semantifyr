/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.validation;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.xtext.validation.Check;

/**
 * This class contains custom validation rules.
 * <p>
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
public class OxstsValidator extends AbstractOxstsValidator {

	public static final String INVALID_TYPE = "invalidType";
	public static final String INVALID_INLINING = "invalidInlining";
	public static final String INVALID_MULTIPLICITIY = "invalidMultiplicity";

    @Check
    public void checkFeatureSubsetting(Feature feature) {
        if (feature.getSubsets().isEmpty()) return;

        var featureTyping = feature.getTyping();
        var subsettingTyping = feature.getSubsets().stream().findFirst().get().getTyping();

        if (featureTyping instanceof IntegerType) {
            if (!(subsettingTyping instanceof IntegerType)) {
                error("Feature must have type that is compatible with subsetted feature",
                        OxstsPackage.Literals.FEATURE__SUBSETS,
                        INVALID_TYPE);
            } else {
                return;
            }
        } else if (featureTyping instanceof BooleanType) {
            if (!(subsettingTyping instanceof BooleanType)) {
                error("Feature must have type that is compatible with subsetted feature",
                        OxstsPackage.Literals.FEATURE__SUBSETS,
                        INVALID_TYPE);
            } else {
                return;
            }
        }

        var featureType = getType(featureTyping);
        var subsettingType = getType(subsettingTyping);

        if (!isSupertypeOf(subsettingType, featureType)) {
            error("Feature must have type that is compatible with subsetted feature",
					OxstsPackage.Literals.FEATURE__SUBSETS,
                    INVALID_TYPE);
        }
    }

    @Check
    public void checkFeatureRedefinition(Feature feature) {
        if (feature.getRedefines() == null) return;

        var featureTyping = feature.getTyping();
        var redefinedTyping = feature.getRedefines().getTyping();

        if (featureTyping instanceof IntegerType) {
            if (!(redefinedTyping instanceof IntegerType)) {
                error("Feature must have type that is compatible with redefined feature",
                        OxstsPackage.Literals.FEATURE__SUBSETS,
                        INVALID_TYPE);
            } else {
                return;
            }
        } else if (featureTyping instanceof BooleanType) {
            if (!(redefinedTyping instanceof BooleanType)) {
                error("Feature must have type that is compatible with redefined feature",
                        OxstsPackage.Literals.FEATURE__SUBSETS,
                        INVALID_TYPE);
            } else {
                return;
            }
        }

        var featureType = getType(featureTyping);
        var redefinedType = getType(redefinedTyping);

        if (!isSupertypeOf(redefinedType, featureType)) {
            error("Feature must have type that is compatible with redefined feature",
                    OxstsPackage.Literals.FEATURE__SUBSETS,
                    INVALID_TYPE);
        }
    }

    @Check
    public void checkTransitionInlining(InlineCall operation) {
        var bindings = operation.getParameterBindings();
        var transition = (Transition) getReference(operation.getReference());

        if (transition == null) return;

        if (bindings.size() < transition.getParameters().size()) {
            error("Transition inlining defines too few parameter bindings",
                    OxstsPackage.Literals.INLINE_CALL__PARAMETER_BINDINGS,
                    INVALID_INLINING);
        } else if (bindings.size() > transition.getParameters().size()) {
            error("Transition inlining defines too much parameter bindings",
                    OxstsPackage.Literals.INLINE_CALL__PARAMETER_BINDINGS,
                    INVALID_INLINING);
        }
    }

    public void checkCompositeTransitionInlining(InlineComposite operation) {
        var feature = (Feature) getReference(operation.getFeature());
        var multiplicity = feature.getMultiplicity();

        if (multiplicity instanceof OneMultiplicity || multiplicity instanceof OptionalMultiplicity) {
            error("Composite inlining must reference feature with many multiplicity",
                    OxstsPackage.Literals.INLINE_COMPOSITE__FEATURE,
                    INVALID_MULTIPLICITIY);
        }
    }

    private Type getType(Typing typing) {
        if (typing instanceof ReferenceTyping referenceTyping) {
            var reference = referenceTyping.getReference().getChains().stream().findFirst().get();

            if (reference instanceof DeclarationReferenceExpression declarationReference) {
                return (Type) declarationReference.getElement();
            }
        }

        throw new RuntimeException("Typing is of incorrect form!");
    }

    private boolean isSupertypeOf(Type superType, Type type) {
        if (type == null) return false;

        if (type == superType) {
            return true;
        }

        return isSupertypeOf(superType, type.getSupertype());
    }

    private Element getReference(ReferenceExpression reference) {
        if (reference instanceof ChainReferenceExpression chainReference) {
            var last = chainReference.getChains().get(chainReference.getChains().size() - 1);
            if (last instanceof DeclarationReferenceExpression declarationReference) {
                return declarationReference.getElement();
            } else if (last instanceof ImplicitTransitionExpression implicitTransition) {
                return null;
            }
        }

        throw new RuntimeException("Reference expression is of incorrect form!");
    }

}

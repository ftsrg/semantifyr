/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.validation;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Type;

/**
 * This class contains custom validation rules.
 * <p>
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
public class OxstsValidator extends AbstractOxstsValidator {

	public static final String INVALID_TYPE = "invalidName";
//
//	@Check
//	public void checkGreetingStartsWithCapital(Greeting greeting) {
//		if (!Character.isUpperCase(greeting.getName().charAt(0))) {
//			warning("Name should start with a capital",
//					OxstsPackage.Literals.GREETING__NAME,
//					INVALID_NAME);
//		}
//	}

//    @Check
//    public void checkFeatureSubsetting(Feature feature) {
//        if (feature.getSubsets() == null) return;
//
//        var featureType = feature.getType();
//        var subsettingType = feature.getSubsets().getType();
//
//        if (!isSupertypeOf(subsettingType, featureType)) {
//            error("Feature must have type that is compatible with subsetted feature",
//					OxstsPackage.Literals.FEATURE__SUBSETS,
//                    INVALID_TYPE);
//        }
//    }

    private boolean isSupertypeOf(Type superType, Type type) {
        if (type == null) return false;

        if (type == superType) {
            return true;
        }

        return isSupertypeOf(superType, type.getSupertype());
    }

}
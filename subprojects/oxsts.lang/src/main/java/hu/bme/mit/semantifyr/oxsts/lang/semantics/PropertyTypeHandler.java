/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsFactory;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification;
import org.eclipse.xtext.util.Tuples;

public class PropertyTypeHandler {

    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.PropertyTypeHandler.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache cache;

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    public TypeSpecification getPropertyReturnType(PropertyDeclaration property) {
        return cache.get(Tuples.create(CACHE_KEY, property), property.eResource(), () -> computePropertyReturnType(property));
    }

    protected TypeSpecification computePropertyReturnType(PropertyDeclaration property) {
        var returnType = property.getReturnType();

        if (returnType != null) {
            return returnType;
        }

        var typeSpecification = OxstsFactory.eINSTANCE.createTypeSpecification();
        typeSpecification.setDomain(builtinSymbolResolver.boolDatatype(property));

        return typeSpecification;
    }

}

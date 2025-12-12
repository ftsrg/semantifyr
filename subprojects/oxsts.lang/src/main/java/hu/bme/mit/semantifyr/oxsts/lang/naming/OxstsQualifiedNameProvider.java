/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.naming;

import com.google.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Strings;
import org.eclipse.xtext.util.Tuples;

@Singleton
public class OxstsQualifiedNameProvider extends IQualifiedNameProvider.AbstractImpl {
    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider.FQN";

    @Inject
    private IQualifiedNameConverter converter;

    @Inject
    private IResourceScopeCache cache;

    @Override
    public QualifiedName getFullyQualifiedName(final EObject obj) {
        return cache.get(Tuples.pair(obj, CACHE_KEY), obj.eResource(), () -> computeFullyQualifiedName(obj));
    }

    protected QualifiedName computeFullyQualifiedName(EObject obj) {
        var name = NamingUtil.getName(obj);

        if (Strings.isEmpty(name)) {
            return null;
        }

        var qualifiedName = converter.toQualifiedName(name);

        while (obj.eContainer() != null) {
            obj = obj.eContainer();
            var parentsQualifiedName = getFullyQualifiedName(obj);
            if (parentsQualifiedName != null) {
                return parentsQualifiedName.append(qualifiedName);
            }
        }

        return qualifiedName;
    }

}

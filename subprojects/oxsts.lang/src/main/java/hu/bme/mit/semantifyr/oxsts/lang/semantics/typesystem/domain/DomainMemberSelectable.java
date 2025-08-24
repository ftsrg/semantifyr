/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.domain;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.EObjectDescription;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.ISelectable;

import java.util.Objects;

public class DomainMemberSelectable implements ISelectable {

    private final DomainMemberCollection domainMemberCollection;

    public DomainMemberSelectable(DomainMemberCollection domainMemberCollection) {
        this.domainMemberCollection = domainMemberCollection;
    }

    @Override
    public boolean isEmpty() {
        return domainMemberCollection.declarations.isEmpty();
    }

    @Override
    public Iterable<IEObjectDescription> getExportedObjects() {
        return FluentIterable.from(domainMemberCollection.declarations.reversed()).transform(declaration -> {
            var element = domainMemberCollection.redefinitions.get(declaration);
            if (element == null) {
                return null;
            }
            var name = NamingUtil.getName(declaration);
            if (name == null) {
                return null;
            }
            return EObjectDescription.create(name, element.getDeclaration());
        }).filter(Objects::nonNull);
    }

    @Override
    public Iterable<IEObjectDescription> getExportedObjects(EClass type, QualifiedName name, boolean ignoreCase) {
        return Iterables.filter(getExportedObjects(), ignoreCase
                ? input -> name.equalsIgnoreCase(input.getName()) && EcoreUtil2.isAssignableFrom(type, input.getEClass())
                : input -> name.equals(input.getName()) && EcoreUtil2.isAssignableFrom(type, input.getEClass())
        );
    }

    @Override
    public Iterable<IEObjectDescription> getExportedObjectsByType(EClass type) {
        return Iterables.filter(getExportedObjects(), input -> EcoreUtil2.isAssignableFrom(type, input.getEClass()));
    }

    @Override
    public Iterable<IEObjectDescription> getExportedObjectsByObject(EObject object) {
        final URI uri = EcoreUtil2.getPlatformResourceOrNormalizedURI(object);
        return Iterables.filter(getExportedObjects(), input -> {
            if (input.getEObjectOrProxy() == object) {
                return true;
            }
            return uri.equals(input.getEObjectURI());
        });
    }

}

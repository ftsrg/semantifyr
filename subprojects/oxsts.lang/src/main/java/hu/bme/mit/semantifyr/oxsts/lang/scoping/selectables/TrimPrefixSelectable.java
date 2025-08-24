/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping.selectables;

import com.google.common.collect.Iterables;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.ISelectable;
import org.eclipse.xtext.resource.impl.AliasedEObjectDescription;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class TrimPrefixSelectable implements ISelectable {
    private final ISelectable delegateSelectable;
    private final QualifiedName prefix;

    public TrimPrefixSelectable(ISelectable delegateSelectable, QualifiedName prefix) {
        this.delegateSelectable = delegateSelectable;
        this.prefix = prefix;
    }

    @Override
    public boolean isEmpty() {
        return delegateSelectable.isEmpty();
    }

    @Override
    public Iterable<IEObjectDescription> getExportedObjects() {
        var delegateIterable = delegateSelectable.getExportedObjects();
        return getAliasedElements(delegateIterable);
    }

    @Override
    public Iterable<IEObjectDescription> getExportedObjects(EClass type, QualifiedName name, boolean ignoreCase) {
        var originalName = prefix.append(name);
        return Iterables.transform(
            delegateSelectable.getExportedObjects(type, originalName, ignoreCase),
            description -> new AliasedEObjectDescription(description.getName().skipFirst(prefix.getSegmentCount()), description)
        );
    }

    @Override
    public Iterable<IEObjectDescription> getExportedObjectsByType(EClass type) {
        var delegateIterable = delegateSelectable.getExportedObjectsByType(type);
        return getAliasedElements(delegateIterable);
    }

    @Override
    public Iterable<IEObjectDescription> getExportedObjectsByObject(EObject object) {
        var delegateIterable = delegateSelectable.getExportedObjectsByObject(object);
        return getAliasedElements(delegateIterable);
    }

    private Iterable<IEObjectDescription> getAliasedElements(Iterable<IEObjectDescription> delegateIterable) {
        return () -> new Iterator<>() {
            private final Iterator<IEObjectDescription> delegateIterator = delegateIterable.iterator();
            private IEObjectDescription next = computeNext();

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public IEObjectDescription next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                var current = next;
                next = computeNext();
                return current;
            }

            private IEObjectDescription computeNext() {
                while (delegateIterator.hasNext()) {
                    var description = delegateIterator.next();
                    var qualifiedName = description.getName();
                    if (qualifiedName.startsWith(prefix) && qualifiedName.getSegmentCount() > prefix.getSegmentCount()) {
                        return new AliasedEObjectDescription(qualifiedName.skipFirst(prefix.getSegmentCount()), description);
                    }
                }
                return null;
            }
        };
    }
}

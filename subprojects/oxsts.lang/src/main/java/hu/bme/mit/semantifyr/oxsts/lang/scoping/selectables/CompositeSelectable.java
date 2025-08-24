/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping.selectables;

import org.eclipse.xtext.resource.ISelectable;
import org.eclipse.xtext.resource.impl.AbstractCompoundSelectable;

import java.util.Collection;
import java.util.List;

public class CompositeSelectable extends AbstractCompoundSelectable {
    private static final CompositeSelectable EMPTY = new CompositeSelectable(List.of());

    private final List<? extends ISelectable> children;

    private CompositeSelectable(List<? extends ISelectable> children) {
        this.children = children;
    }

    @Override
    protected Iterable<? extends ISelectable> getSelectables() {
        return children;
    }

    public static ISelectable of(Collection<? extends ISelectable> children) {
        var filteredChildren = children.stream().filter(selectable -> !selectable.isEmpty()).toList();
        return switch (filteredChildren.size()) {
            case 0 -> EMPTY;
            case 1 -> filteredChildren.getFirst();
            default -> new CompositeSelectable(filteredChildren);
        };
    }

}

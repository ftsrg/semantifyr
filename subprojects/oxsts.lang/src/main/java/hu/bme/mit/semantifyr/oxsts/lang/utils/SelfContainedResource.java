/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

public final class SelfContainedResource extends AdapterImpl {

    private SelfContainedResource() {}

    public static void mark(Resource resource) {
        resource.eAdapters().add(new SelfContainedResource());
    }

    public static boolean isMarked(Resource resource) {
        return resource != null && EcoreUtil.getExistingAdapter(resource, SelfContainedResource.class) != null;
    }

    @Override
    public boolean isAdapterForType(Object type) {
        return type == SelfContainedResource.class;
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.utils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.SemantifyrScopes;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

import static hu.bme.mit.semantifyr.semantics.utils.EcoreUtilsKt.unload;

@Singleton
public class CompilationScopeRunner {

    @Inject
    private SemantifyrLoader semantifyrLoader;

    private void useResourceSetInCompilationScope(ResourceSet resourceSet, Runnable block) {
        try {
            SemantifyrScopes.runInScope(block);
        } finally {
            unload(resourceSet);
        }
    }

    public void runInCompilationScope(ResourceSet resourceSet, ResourceSetRunnable runnable) {
        ResourceSet compilationResourceSet = semantifyrLoader.cloneOxstsPackagesInResourceSet(resourceSet);

        useResourceSetInCompilationScope(compilationResourceSet, () ->
                runnable.run(compilationResourceSet)
        );
    }

    public void runInCompilationScope(Resource resource, ResourceRunnable runnable) {
        ResourceSet compilationResourceSet = semantifyrLoader.cloneOxstsPackagesInResourceSet(resource.getResourceSet());
        Resource compilationResource = compilationResourceSet.getResource(resource.getURI(), true);

        useResourceSetInCompilationScope(compilationResourceSet, () ->
                runnable.run(compilationResource)
        );
    }

    public <T extends EObject> void runInCompilationScope(T eObject, EObjectRunnable<T> runnable) {
        ResourceSet compilationResourceSet = semantifyrLoader.cloneOxstsPackagesInResourceSet(eObject.eResource().getResourceSet());

        @SuppressWarnings("unchecked")
        T compilationEObject = (T) compilationResourceSet.getEObject(EcoreUtil.getURI(eObject), true);

        useResourceSetInCompilationScope(compilationResourceSet, () ->
                runnable.run(compilationEObject)
        );
    }

}

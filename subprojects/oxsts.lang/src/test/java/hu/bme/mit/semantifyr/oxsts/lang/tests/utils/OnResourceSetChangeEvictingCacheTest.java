/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.lang.utils.SelfContainedResource;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsFactory;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class OnResourceSetChangeEvictingCacheTest {

    @Inject
    private ParseHelper<OxstsModelPackage> packageParseHelper;

    @Inject
    private ParseHelper<InlinedOxsts> inlinedParseHelper;

    @Inject
    private OnResourceSetChangeEvictingCache cache;

    private OxstsModelPackage library;

    private InlinedOxsts inlined;

    private final AtomicInteger libraryComputations = new AtomicInteger();

    private final AtomicInteger inlinedComputations = new AtomicInteger();

    @BeforeEach
    void parseLibraryAndInlinedModel() throws Exception {
        library = packageParseHelper.parse("""
            package lib
            class Host {
                var x: int := 0
            }
            """);
        inlined = inlinedParseHelper.parse("""
            inlined oxsts of lib::Host
            init { }
            tran { }
            prop { AG true }
            """, library.eResource().getResourceSet());
    }

    @Test
    void editOfASelfContainedResourceEvictsOnlyItsOwnEntries() {
        SelfContainedResource.mark(inlined.eResource());
        lookUp();

        inlined.getVariables().add(OxstsFactory.eINSTANCE.createVariableDeclaration());
        lookUp();

        assertThat(libraryComputations).hasValue(1);
        assertThat(inlinedComputations).hasValue(2);
    }

    @Test
    void editOfAnUnmarkedResourceEvictsEveryEntry() {
        lookUp();

        inlined.getVariables().add(OxstsFactory.eINSTANCE.createVariableDeclaration());
        lookUp();

        assertThat(libraryComputations).hasValue(2);
        assertThat(inlinedComputations).hasValue(2);
    }

    @Test
    void editOfAPackageEvictsEveryEntry() {
        SelfContainedResource.mark(inlined.eResource());
        lookUp();

        library.getDeclarations().add(OxstsFactory.eINSTANCE.createClassDeclaration());
        lookUp();

        assertThat(libraryComputations).hasValue(2);
        assertThat(inlinedComputations).hasValue(2);
    }

    @Test
    void newResourceEvictsEveryEntry() throws Exception {
        SelfContainedResource.mark(inlined.eResource());
        lookUp();

        packageParseHelper.parse("package other", library.eResource().getResourceSet());
        lookUp();

        assertThat(libraryComputations).hasValue(2);
        assertThat(inlinedComputations).hasValue(2);
    }

    private void lookUp() {
        lookUp(library.eResource(), libraryComputations);
        lookUp(inlined.eResource(), inlinedComputations);
    }

    private void lookUp(Resource resource, AtomicInteger computations) {
        cache.get("key", resource, computations::incrementAndGet);
    }
}

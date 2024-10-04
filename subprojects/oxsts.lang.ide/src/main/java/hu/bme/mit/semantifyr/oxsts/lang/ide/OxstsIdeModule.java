/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import com.google.inject.Binder;
import org.eclipse.xtext.ide.server.*;

/**
 * Use this class to register ide components.
 */
public class OxstsIdeModule extends AbstractOxstsIdeModule {
    @Override
    public void configure(Binder binder) {
        super.configure(binder);

        // Bind the required interfaces to their implementations
        binder.bind(IProjectDescriptionFactory.class).to(DefaultProjectDescriptionFactory.class);
        binder.bind(IMultiRootWorkspaceConfigFactory.class).to(MultiRootWorkspaceConfigFactory.class);
    }
}

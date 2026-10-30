/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import com.google.inject.Injector;

public final class OxstsInjector {

    private final Injector injector;

    public OxstsInjector(Injector injector) {
        this.injector = injector;
    }

    public Injector get() {
        return injector;
    }
}

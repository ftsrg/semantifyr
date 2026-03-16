/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.utils;

import org.eclipse.emf.ecore.resource.Resource;

@FunctionalInterface
public interface ResourceRunnable {
    void run(Resource resource);
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.utils;

import org.eclipse.emf.ecore.EObject;

@FunctionalInterface
public interface EObjectRunnable<T extends EObject> {
    void run(T eObject);
}

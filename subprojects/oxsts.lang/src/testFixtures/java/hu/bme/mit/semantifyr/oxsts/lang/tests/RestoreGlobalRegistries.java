/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.eclipse.xtext.testing.GlobalRegistries;
import org.eclipse.xtext.testing.GlobalRegistries.GlobalStateMemento;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(RestoreGlobalRegistries.Extension.class)
public @interface RestoreGlobalRegistries {

    class Extension implements BeforeAllCallback, AfterAllCallback {

        private static final ExtensionContext.Namespace NAMESPACE =
                ExtensionContext.Namespace.create(RestoreGlobalRegistries.class);

        @Override
        public void beforeAll(ExtensionContext context) {
            context.getStore(NAMESPACE).put(GlobalStateMemento.class, GlobalRegistries.makeCopyOfGlobalState());
        }

        @Override
        public void afterAll(ExtensionContext context) {
            context.getStore(NAMESPACE)
                    .get(GlobalStateMemento.class, GlobalStateMemento.class)
                    .restoreGlobalState();
        }
    }
}

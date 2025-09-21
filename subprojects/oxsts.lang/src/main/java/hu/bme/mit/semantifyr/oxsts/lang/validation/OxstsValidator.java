/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.validation;

/**
 * This class contains custom validation rules.
 * <p>
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
public class OxstsValidator extends AbstractOxstsValidator {

    @Override
    protected void handleExceptionDuringValidation(Throwable targetException) throws RuntimeException {
        // swallow all exceptions!
    }

}

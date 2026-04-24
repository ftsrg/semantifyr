/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import hu.bme.mit.semantifyr.oxsts.lang.utils.SourceLocation;
import org.eclipse.emf.ecore.EObject;

public class EvaluationFailureException extends RuntimeException {

    public EvaluationFailureException(String message) {
        super(message);
    }

    public EvaluationFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Builds an {@link EvaluationFailureException} whose message is prefixed
     * with the source location of the given element. Use at evaluator sites
     * where the offending expression is known so diagnostics point at the
     * user's code.
     */
    public static EvaluationFailureException at(EObject eObject, String message) {
        return new EvaluationFailureException(SourceLocation.prefixFor(eObject) + message);
    }

}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.verifier.CallTraceDto;
import hu.bme.mit.semantifyr.verifier.TraceDto;
import hu.bme.mit.semantifyr.verifier.WitnessStateDto;

public record VerificationTrace(CallTraceDto callTrace, WitnessStateDto witnessState, String witnessUri) {

    public static VerificationTrace fromDto(TraceDto trace) {
        var resource = trace.getBackAnnotatedModel().eResource();
        var uri = resource == null ? null : resource.getURI().toString();
        return new VerificationTrace(trace.getCallTrace(), trace.getWitnessState(), uri);
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.verifier.CallTraceDto;
import hu.bme.mit.semantifyr.verifier.TraceDto;
import hu.bme.mit.semantifyr.verifier.WitnessStateDto;
import java.io.StringWriter;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.resource.SaveOptions;
import org.eclipse.xtext.serializer.ISerializer;

public record VerificationTrace(
        CallTraceDto callTrace, WitnessStateDto witnessState, String backAnnotatedSource, String witnessUri) {

    public static VerificationTrace fromDto(TraceDto trace) {
        var model = trace.getBackAnnotatedModel();
        var resource = model.eResource();
        if (resource == null) {
            return new VerificationTrace(trace.getCallTrace(), trace.getWitnessState(), null, null);
        }

        var uri = resource.getURI();
        var serializer = IResourceServiceProvider.Registry.INSTANCE
                .getResourceServiceProvider(uri)
                .get(ISerializer.class);

        var writer = new StringWriter();
        try {
            serializer.serialize(model, writer, SaveOptions.defaultOptions());
        } catch (Exception e) {
            return new VerificationTrace(trace.getCallTrace(), trace.getWitnessState(), null, uri.toString());
        }

        return new VerificationTrace(trace.getCallTrace(), trace.getWitnessState(), writer.toString(), uri.toString());
    }
}

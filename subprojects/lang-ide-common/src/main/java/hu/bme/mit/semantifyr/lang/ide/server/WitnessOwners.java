/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.emf.common.util.URI;

@Singleton
public class WitnessOwners {

    private final Map<URI, URI> caseResourceByWitness = new ConcurrentHashMap<>();

    public void record(URI witness, URI caseResource) {
        caseResourceByWitness.put(witness, caseResource);
    }

    public URI caseResourceOrNull(URI witness) {
        return caseResourceByWitness.entrySet().stream()
                .filter(entry -> UriUtils.isSameFile(entry.getKey(), witness))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}

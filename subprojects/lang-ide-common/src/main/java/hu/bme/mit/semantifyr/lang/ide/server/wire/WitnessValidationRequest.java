/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

public record WitnessValidationRequest(String uri, Range range, String portfolio, String witnessUri) {

    public Location toLocation() {
        return new Location(uri, range);
    }
}

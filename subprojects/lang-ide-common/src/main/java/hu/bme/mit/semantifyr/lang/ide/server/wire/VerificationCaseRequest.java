/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

public record VerificationCaseRequest(String uri, Range range, String portfolio) {

    public Location toLocation() {
        return new Location(uri, range);
    }

    public static VerificationCaseRequest fromLocation(Location location, String portfolio) {
        return new VerificationCaseRequest(location.getUri(), location.getRange(), portfolio);
    }
}

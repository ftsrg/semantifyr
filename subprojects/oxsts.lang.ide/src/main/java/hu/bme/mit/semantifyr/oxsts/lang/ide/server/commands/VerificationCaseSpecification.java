/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import org.eclipse.lsp4j.Location;

public record VerificationCaseSpecification(
        String id,
        String label,
        Location location
) {
}

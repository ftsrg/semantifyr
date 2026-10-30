/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import org.eclipse.lsp4j.Location;

public record VerificationCaseSpecification(String id, String label, Location location) {}

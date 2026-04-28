/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.commands;

import org.eclipse.lsp4j.Location;

public record VerificationCaseSpecification(String id, String label, Location location) {}

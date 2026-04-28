/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.client;

import java.util.List;
import org.eclipse.lsp4j.Location;

public record NavigateToParams(List<Location> locations) {}

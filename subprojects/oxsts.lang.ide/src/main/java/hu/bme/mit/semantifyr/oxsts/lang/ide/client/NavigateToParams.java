/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.client;

import org.eclipse.lsp4j.Location;

import java.util.List;

public record NavigateToParams(List<Location> locations) {

}

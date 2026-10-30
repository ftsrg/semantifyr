/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;

public record VerifyInlinedOxstsCommandParams(InlinedOxsts inlinedOxsts, String portfolioId) {}

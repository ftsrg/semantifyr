/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.commands;

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration;

public record ValidateWitnessCommandParams(VerificationCaseDeclaration caseDeclaration, String portfolioId) {}

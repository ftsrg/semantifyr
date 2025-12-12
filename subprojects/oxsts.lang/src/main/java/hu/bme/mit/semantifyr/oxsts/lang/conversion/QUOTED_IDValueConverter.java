/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package hu.bme.mit.semantifyr.oxsts.lang.conversion;

import org.eclipse.xtext.conversion.impl.STRINGValueConverter;
import org.eclipse.xtext.util.Strings;

public class QUOTED_IDValueConverter extends STRINGValueConverter {
	@Override
	protected String toEscapedString(String value) {
		return '\'' + Strings.convertToJavaString(value, false) + '\'';
	}
}

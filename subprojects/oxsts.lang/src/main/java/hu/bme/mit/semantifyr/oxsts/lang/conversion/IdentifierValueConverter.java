/*
 * SPDX-FileCopyrightText: 2024 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package hu.bme.mit.semantifyr.oxsts.lang.conversion;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.services.OxstsGrammarAccess;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.conversion.IValueConverter;
import org.eclipse.xtext.conversion.ValueConverterException;
import org.eclipse.xtext.nodemodel.INode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.eclipse.xtext.EcoreUtil2.eAllContentsAsList;
import static org.eclipse.xtext.EcoreUtil2.typeSelect;

public class IdentifierValueConverter implements IValueConverter<String> {
	private final Set<String> keywords;

    @Inject
	private QUOTED_IDValueConverter quotedIdValueConverter;

	@Inject
	public IdentifierValueConverter(OxstsGrammarAccess grammarAccess, QUOTED_IDValueConverter quotedIdValueConverter) {
		this.quotedIdValueConverter = quotedIdValueConverter;
		quotedIdValueConverter.setRule(grammarAccess.getQUOTED_IDRule());

		keywords = new LinkedHashSet<>(GrammarUtil.getAllKeywords(grammarAccess.getGrammar()));
        List<Keyword> list = typeSelect(eAllContentsAsList(grammarAccess.getKEYWORDRule()), Keyword.class);
        for (Keyword keyword : list) {
            keywords.remove(keyword.getValue());
        }

	}

	@Override
	public String toValue(String string, INode node) throws ValueConverterException {
		if (string == null) {
			return null;
		}
		if (NamingUtil.isQuotedId(string)) {
			return quotedIdValueConverter.toValue(string, node);
		}
		return string;
	}

	@Override
	public String toString(String value) throws ValueConverterException {
		if (value == null) {
			throw new ValueConverterException("Identifier may not be null.", null, null);
		}
		if (NamingUtil.isSimpleId(value) && !keywords.contains(value)) {
			return value;
		}
        if (NamingUtil.isQuotedId(value)) {
            return value;
        }
		return quotedIdValueConverter.toString(value);
	}
}

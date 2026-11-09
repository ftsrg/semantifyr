/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.symbol;

import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.ide.server.symbol.DocumentSymbolMapper;

@Singleton
public class OxstsDocumentSymbolNameProvider extends DocumentSymbolMapper.DocumentSymbolNameProvider {

    @Override
    public String getName(EObject object) {
        if (object == null) {
            return null;
        }
        return NamingUtil.getName(object);
    }
}

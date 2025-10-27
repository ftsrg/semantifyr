/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.editor.quickfix;

import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.ide.editor.quickfix.AbstractDeclarativeIdeQuickfixProvider;
import org.eclipse.xtext.ide.editor.quickfix.DiagnosticResolutionAcceptor;
import org.eclipse.xtext.ide.editor.quickfix.QuickFix;

@SuppressWarnings({"UnstableApiUsage", "unused"})
public class OxstsQuickFixProvider extends AbstractDeclarativeIdeQuickfixProvider {

//    @QuickFix(OxstsValidator.DATA_TYPE_NOT_IN_BUILTIN_ISSUE)
//    public void removeNotBuiltinDataType(DiagnosticResolutionAcceptor acceptor) {
//        acceptor.accept("Remove", ((diagnostic, object, document) -> {
//            return createTextEdit(diagnostic, "");
//        }));
//    }

    @QuickFix(OxstsValidator.DATA_TYPE_NOT_IN_BUILTIN_ISSUE)
    public void removeNotBuiltinDataType(DiagnosticResolutionAcceptor acceptor) {
        acceptor.accept("Remove", ((diagnostic, object) -> {
            return (o) -> EcoreUtil2.remove(o);
        }));
    }

}

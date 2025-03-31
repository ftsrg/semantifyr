/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.contentassist;

import com.google.inject.Inject;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.CrossReference;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.xtext.CurrentTypeFinder;

public class OxstsContentProposalProvider extends IdeContentProposalProvider {

    @Inject
    private CurrentTypeFinder currentTypeFinder;

    @Override
    protected void _createProposals(CrossReference reference, ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
        EClassifier type = currentTypeFinder.findCurrentTypeAfter(reference);
        if (type instanceof EClass) {
            EReference eReference = GrammarUtil.getReference(reference, (EClass)type);
            EObject currentModel = context.getCurrentNode().getSemanticElement();
            if (eReference != null && currentModel != null) {
                IScope scope = this.getScopeProvider().getScope(currentModel, eReference);
                this.getCrossrefProposalProvider().lookupCrossReference(scope, reference, context, acceptor, this.getCrossrefFilter(reference, context));
            }
        }

    }

}

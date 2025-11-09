/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.editor.contentassist;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.services.OxstsGrammarAccess;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;
import org.eclipse.xtext.util.TextRegion;

public class OxstsIdeContentProposalProvider extends IdeContentProposalProvider {

    @Inject
    protected OxstsGrammarAccess oxstsGrammarAccess;

    @Inject
    protected DomainMemberCollectionProvider domainMemberCollectionProvider;

    @Override
    protected void _createProposals(RuleCall ruleCall, ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
        if (
            this.oxstsGrammarAccess.getIdentifierRule().equals(ruleCall.getRule())
            && context.getCurrentModel() instanceof RedefinableDeclaration redefinableDeclaration
            && redefinableDeclaration.isRedefine()
        ) {
            // we should redefine some existing element
            var containerDomain = EcoreUtil2.getContainerOfType(redefinableDeclaration.eContainer(), DomainDeclaration.class);
            var availableElements = domainMemberCollectionProvider.getParentCollection(containerDomain).getDeclarations();

            for (var declaration : availableElements) {
                if (EcoreUtil2.isAssignableFrom(redefinableDeclaration.eClass(), declaration.eClass())) {
                    var proposal = NamingUtil.getName(declaration);
                    var entry = this.getProposalCreator().createProposal(proposal, context, ContentAssistEntry.KIND_REFERENCE, (it) -> {
                        it.getEditPositions().add(new TextRegion(context.getOffset(), proposal.length()));
                        it.setDescription(ruleCall.getRule().getName());
                    });
                    acceptor.accept(entry, this.getProposalPriorities().getDefaultPriority(entry));
                }
            }

        }
    }

}

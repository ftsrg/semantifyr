/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.editor.contentassist;

import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AnnotationDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParametricDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.CrossReference;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;
import org.eclipse.xtext.ide.editor.contentassist.IdeCrossrefProposalProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.util.TextRegion;

public class OxstsIdeCrossrefProposalProvider extends IdeCrossrefProposalProvider {

    public static final String KIND_ENUM_MEMBER = "ENUM_MEMBER";

    public static final String KIND_STRUCT = "STRUCT";

    @Override
    protected ContentAssistEntry createProposal(
            IEObjectDescription candidate, CrossReference crossReference, ContentAssistContext context) {
        var entry = super.createProposal(candidate, crossReference, context);
        if (entry == null) {
            return null;
        }
        var element = candidate.getEObjectOrProxy();
        entry.setKind(kind(element));
        if (element.eIsProxy()) {
            entry.setDescription(packageName(candidate.getQualifiedName()));
            return entry;
        }
        entry.setDescription(detail(element, candidate));
        if (element instanceof ParametricDeclaration callable
                && OxstsUtils.isCallable(callable)
                && !isFollowedByArguments(context)) {
            addCallArguments(entry, callable, context);
        }
        return entry;
    }

    private String detail(EObject element, IEObjectDescription candidate) {
        if (element instanceof EnumLiteral literal) {
            return NamingUtil.getName(literal.eContainer());
        }
        var detail = DeclarationSignatures.detail(element);
        if (detail != null) {
            return detail;
        }
        return packageName(candidate.getQualifiedName());
    }

    private String packageName(QualifiedName qualifiedName) {
        if (qualifiedName == null || qualifiedName.getSegmentCount() < 2) {
            return null;
        }
        return getQualifiedNameConverter().toString(qualifiedName.skipLast(1));
    }

    private void addCallArguments(
            ContentAssistEntry entry, ParametricDeclaration callable, ContentAssistContext context) {
        var start = context.getOffset() - entry.getPrefix().length();
        var proposal = new StringBuilder(entry.getProposal()).append("(");
        var parameters = callable.getParameters();
        for (var index = 0; index < parameters.size(); index++) {
            if (index > 0) {
                proposal.append(", ");
            }
            var name = parameters.get(index).getName();
            entry.getEditPositions().add(new TextRegion(start + proposal.length(), name.length()));
            proposal.append(name);
        }
        proposal.append(")");
        entry.setLabel(entry.getProposal());
        entry.setProposal(proposal.toString());
        entry.setEscapePosition(start + proposal.length());
    }

    private boolean isFollowedByArguments(ContentAssistContext context) {
        var text = context.getRootNode().getText();
        var index = context.getOffset();
        while (index < text.length() && Character.isJavaIdentifierPart(text.charAt(index))) {
            index++;
        }
        return index < text.length() && text.charAt(index) == '(';
    }

    private String kind(EObject element) {
        return switch (element) {
            case ClassDeclaration ignored -> ContentAssistEntry.KIND_CLASS;
            case DataTypeDeclaration ignored -> ContentAssistEntry.KIND_CLASS;
            case EnumDeclaration ignored -> ContentAssistEntry.KIND_ENUM;
            case RecordDeclaration ignored -> KIND_STRUCT;
            case AnnotationDeclaration ignored -> ContentAssistEntry.KIND_INTERFACE;
            case OxstsModelPackage ignored -> ContentAssistEntry.KIND_MODULE;
            case EnumLiteral ignored -> KIND_ENUM_MEMBER;
            case TransitionDeclaration ignored -> ContentAssistEntry.KIND_METHOD;
            case PropertyDeclaration ignored -> ContentAssistEntry.KIND_FUNCTION;
            case FeatureDeclaration ignored -> ContentAssistEntry.KIND_PROPERTY;
            case ParameterDeclaration ignored -> ContentAssistEntry.KIND_VARIABLE;
            case VariableDeclaration variable
            when variable.eContainer() instanceof ClassDeclaration
                    || variable.eContainer() instanceof RecordDeclaration -> ContentAssistEntry.KIND_FIELD;
            case VariableDeclaration ignored -> ContentAssistEntry.KIND_VARIABLE;
            default -> ContentAssistEntry.KIND_REFERENCE;
        };
    }
}

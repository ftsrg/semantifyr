/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.editor.contentassist;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.InheritanceHandler;
import hu.bme.mit.semantifyr.oxsts.lang.services.OxstsGrammarAccess;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Annotation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Argument;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParametricDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PostfixUnaryExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.CrossReference;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.IEObjectDescription;

public class OxstsIdeContentProposalProvider extends IdeContentProposalProvider {

    private static final int NAMED_ARGUMENT_PRIORITY = 700;

    private static final int EXPECTED_TYPE_PRIORITY_BONUS = 100;

    private enum Usage {
        CALL,
        ASSIGNMENT,
        VALUE,
    }

    @Inject
    protected OxstsGrammarAccess oxstsGrammarAccess;

    @Inject
    protected DomainMemberCollectionProvider domainMemberCollectionProvider;

    @Inject
    protected InheritanceHandler inheritanceHandler;

    @Override
    protected void _createProposals(
            Assignment assignment, ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
        if (assignment.getTerminal() instanceof CrossReference) {
            super._createProposals(assignment, context, acceptor);
        } else if ("redefine".equals(assignment.getFeature())
                && context.getCurrentModel() instanceof DomainDeclaration domain) {
            createRedefinitionProposals(domain, context, acceptor);
        }
    }

    @Override
    protected void _createProposals(
            RuleCall ruleCall, ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
        if (oxstsGrammarAccess.getIdentifierRule().equals(ruleCall.getRule())
                && context.getCurrentModel() instanceof RedefinableDeclaration redefinableDeclaration
                && redefinableDeclaration.isRedefine()) {
            createRedefinableNameProposals(redefinableDeclaration, context, acceptor);
        }
    }

    @Override
    protected void _createProposals(
            CrossReference reference, ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
        if (reference
                == oxstsGrammarAccess.getArgumentAccess().getParameterParameterDeclarationCrossReference_0_0_0()) {
            createNamedArgumentProposals(context, acceptor);
            return;
        }
        var expectedDomain =
                reference == oxstsGrammarAccess.getElementReferenceAccess().getElementNamedElementCrossReference_0()
                        ? expectedArgumentDomain(context)
                        : null;
        if (expectedDomain == null) {
            super._createProposals(reference, context, acceptor);
        } else {
            super._createProposals(reference, context, new ExpectedTypeAcceptor(acceptor, expectedDomain, context));
        }
    }

    @Override
    protected Predicate<IEObjectDescription> getCrossrefFilter(CrossReference reference, ContentAssistContext context) {
        if (reference
                == oxstsGrammarAccess.getTypeSpecificationAccess().getDomainDomainDeclarationCrossReference_0_0()) {
            return typeFilter(context.getCurrentModel());
        }
        if (reference == oxstsGrammarAccess.getElementReferenceAccess().getElementNamedElementCrossReference_0()
                || reference
                        == oxstsGrammarAccess
                                .getPostfixUnaryExpressionAccess()
                                .getMemberNamedElementCrossReference_1_0_0_3_0()) {
            var usage = usage(context);
            return candidate -> accepts(usage, candidate.getEObjectOrProxy());
        }
        if (reference
                == oxstsGrammarAccess.getRecordLiteralAccess().getRecordRecordDeclarationCrossReference_0_0_0_0()) {
            return usage(context) == Usage.VALUE ? Predicates.alwaysTrue() : Predicates.alwaysFalse();
        }
        return super.getCrossrefFilter(reference, context);
    }

    private Predicate<IEObjectDescription> typeFilter(EObject currentModel) {
        var owner = currentModel instanceof TypeSpecification ? currentModel.eContainer() : currentModel;
        if (owner instanceof FeatureDeclaration feature) {
            if (feature.getKind() == FeatureKind.CONTAINMENT || feature.getKind() == FeatureKind.CONTAINER) {
                return candidate -> isClass(candidate);
            }
            return candidate -> isClass(candidate) || isDataType(candidate) || isEnum(candidate);
        }
        if (owner instanceof VariableDeclaration && owner.eContainer() instanceof RecordDeclaration) {
            return candidate -> isDataType(candidate) || isEnum(candidate) || isRecord(candidate);
        }
        return Predicates.alwaysTrue();
    }

    private boolean isClass(IEObjectDescription candidate) {
        return candidate.getEObjectOrProxy() instanceof ClassDeclaration;
    }

    private boolean isDataType(IEObjectDescription candidate) {
        return candidate.getEObjectOrProxy() instanceof DataTypeDeclaration;
    }

    private boolean isEnum(IEObjectDescription candidate) {
        return candidate.getEObjectOrProxy() instanceof EnumDeclaration;
    }

    private boolean isRecord(IEObjectDescription candidate) {
        return candidate.getEObjectOrProxy() instanceof RecordDeclaration;
    }

    private Usage usage(ContentAssistContext context) {
        var model = context.getCurrentModel();
        if (model instanceof SequenceOperation) {
            if (isAfterInlineKeyword(context)) {
                return Usage.CALL;
            }
            return Usage.ASSIGNMENT;
        }
        if (model instanceof InlineCall) {
            return Usage.CALL;
        }
        if (model instanceof HavocOperation) {
            return Usage.ASSIGNMENT;
        }
        if (!(model instanceof NavigationSuffixExpression || model instanceof ElementReference)) {
            return Usage.VALUE;
        }
        EObject expression = model;
        while (expression.eContainer() instanceof PostfixUnaryExpression container
                && container.getPrimary() == expression) {
            expression = container;
        }
        var container = expression.eContainer();
        if (container instanceof InlineCall) {
            return Usage.CALL;
        }
        if (container instanceof HavocOperation
                || container instanceof AssignmentOperation assignment && assignment.getReference() == expression) {
            return Usage.ASSIGNMENT;
        }
        return Usage.VALUE;
    }

    // The parser leaves a lone `inline` as a leaf with a syntax error and no grammar element, so the text decides.
    private boolean isAfterInlineKeyword(ContentAssistContext context) {
        var lastCompleteNode = context.getLastCompleteNode();
        return lastCompleteNode != null && "inline".equals(lastCompleteNode.getText());
    }

    private boolean accepts(Usage usage, EObject element) {
        return switch (usage) {
            case CALL ->
                element instanceof TransitionDeclaration
                        || element instanceof FeatureDeclaration feature && isInstance(feature.getTypeSpecification())
                        || element instanceof VariableDeclaration variable
                                && isInstance(variable.getTypeSpecification())
                        || element instanceof ParameterDeclaration parameter
                                && isInstance(parameter.getTypeSpecification());
            case ASSIGNMENT ->
                element instanceof VariableDeclaration
                        || element instanceof FeatureDeclaration feature && isInstance(feature.getTypeSpecification());
            case VALUE ->
                element instanceof VariableDeclaration
                        || element instanceof FeatureDeclaration
                        || element instanceof PropertyDeclaration
                        || element instanceof ParameterDeclaration
                        || element instanceof EnumLiteral;
        };
    }

    // A loop variable has no type specification and ranges over instances as often as over values.
    private boolean isInstance(TypeSpecification typeSpecification) {
        if (typeSpecification == null) {
            return true;
        }
        var domain = typeSpecification.getDomain();
        return domain instanceof ClassDeclaration || domain instanceof FeatureDeclaration;
    }

    private EObject resolve(IEObjectDescription candidate, ContentAssistContext context) {
        return EcoreUtil.resolve(candidate.getEObjectOrProxy(), context.getResource());
    }

    private void createNamedArgumentProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
        var call = argumentList(context);
        if (call == null) {
            return;
        }
        var unfilled = unfilledParameters(call, context);
        for (var parameter : unfilled) {
            var entry = getProposalCreator()
                    .createProposal(parameter.getName() + " = ", context, ContentAssistEntry.KIND_VARIABLE, (it) -> {
                        it.setLabel(parameter.getName() + " =");
                        it.setDescription(DeclarationSignatures.type(parameter.getTypeSpecification()));
                    });
            acceptor.accept(entry, NAMED_ARGUMENT_PRIORITY);
        }
    }

    private DomainDeclaration expectedArgumentDomain(ContentAssistContext context) {
        var call = argumentList(context);
        if (call == null) {
            return null;
        }
        var unfilled = unfilledParameters(call, context);
        if (unfilled.isEmpty() || unfilled.getFirst().getTypeSpecification() == null) {
            return null;
        }
        return unfilled.getFirst().getTypeSpecification().getDomain();
    }

    private record ArgumentList(ParametricDeclaration callee, List<Argument> arguments, Argument current) {}

    private ArgumentList argumentList(ContentAssistContext context) {
        var model = context.getCurrentModel();
        var current = model instanceof Argument argument ? argument : null;
        var owner = current == null ? model : model.eContainer();
        if (owner instanceof CallSuffixExpression call) {
            var callee = callee(call.getPrimary());
            return callee == null ? null : new ArgumentList(callee, call.getArguments(), current);
        }
        if (owner instanceof Annotation annotation
                && annotation.getDeclaration() != null
                && !annotation.getDeclaration().eIsProxy()) {
            return new ArgumentList(annotation.getDeclaration(), annotation.getArguments(), current);
        }
        return null;
    }

    private ParametricDeclaration callee(Expression primary) {
        var element = switch (primary) {
            case ElementReference reference -> reference.getElement();
            case NavigationSuffixExpression navigation -> navigation.getMember();
            case null, default -> null;
        };
        if (element instanceof ParametricDeclaration callee && !callee.eIsProxy()) {
            return callee;
        }
        return null;
    }

    private List<ParameterDeclaration> unfilledParameters(ArgumentList argumentList, ContentAssistContext context) {
        var named = new HashSet<ParameterDeclaration>();
        var positional = 0;
        var cursor = context.getOffset() - context.getPrefix().length();
        for (var argument : argumentList.arguments()) {
            if (argument == argumentList.current()) {
                continue;
            }
            if (argument.getParameter() != null && !argument.getParameter().eIsProxy()) {
                named.add(argument.getParameter());
            } else if (endsBefore(argument, cursor)) {
                positional++;
            }
        }
        var parameters = argumentList.callee().getParameters();
        return parameters.subList(Math.min(positional, parameters.size()), parameters.size()).stream()
                .filter(parameter -> !named.contains(parameter))
                .toList();
    }

    private boolean endsBefore(Argument argument, int offset) {
        var node = NodeModelUtils.findActualNodeFor(argument);
        return node != null && node.getEndOffset() <= offset;
    }

    private DomainDeclaration valueDomain(EObject element) {
        var typeSpecification = switch (element) {
            case VariableDeclaration variable -> variable.getTypeSpecification();
            case FeatureDeclaration feature -> feature.getTypeSpecification();
            case ParameterDeclaration parameter -> parameter.getTypeSpecification();
            case PropertyDeclaration property -> property.getReturnType();
            case null, default -> null;
        };
        if (typeSpecification != null) {
            return typeSpecification.getDomain();
        }
        if (element instanceof EnumLiteral literal) {
            return (EnumDeclaration) literal.eContainer();
        }
        return null;
    }

    private boolean matchesDomain(DomainDeclaration actual, DomainDeclaration expected) {
        return actual != null
                && (actual == expected
                        || inheritanceHandler.getTransitiveSuperDomains(actual).contains(expected));
    }

    private class ExpectedTypeAcceptor implements IIdeContentProposalAcceptor {

        private final IIdeContentProposalAcceptor delegate;

        private final DomainDeclaration expectedDomain;

        private final ContentAssistContext context;

        ExpectedTypeAcceptor(
                IIdeContentProposalAcceptor delegate, DomainDeclaration expectedDomain, ContentAssistContext context) {
            this.delegate = delegate;
            this.expectedDomain = expectedDomain;
            this.context = context;
        }

        @Override
        public void accept(ContentAssistEntry entry, int priority) {
            if (entry != null
                    && entry.getSource() instanceof IEObjectDescription candidate
                    && matchesDomain(valueDomain(resolve(candidate, context)), expectedDomain)) {
                delegate.accept(entry, priority + EXPECTED_TYPE_PRIORITY_BONUS);
            } else {
                delegate.accept(entry, priority);
            }
        }

        @Override
        public boolean canAcceptMoreProposals() {
            return delegate.canAcceptMoreProposals();
        }

        @Override
        public boolean didAcceptAllProposals() {
            return delegate.didAcceptAllProposals();
        }
    }

    private void createRedefinableNameProposals(
            RedefinableDeclaration redefinableDeclaration,
            ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        var domain = EcoreUtil2.getContainerOfType(redefinableDeclaration.eContainer(), DomainDeclaration.class);
        var nameOnly = isFollowedByDeclarationRest(context);
        for (var declaration : redefinableMembers(domain, redefinableDeclaration)) {
            if (!isRedefinableBy(declaration, redefinableDeclaration) || NamingUtil.isAnonymous(declaration)) {
                continue;
            }
            var header = header(declaration);
            var body = nameOnly ? "" : body(declaration);
            var proposal = nameOnly ? NamingUtil.getName(declaration) : header + body;
            var entry = getProposalCreator().createProposal(proposal, context, kind(declaration), (it) -> {
                it.setLabel(header);
                it.setDescription(NamingUtil.getName(declaration.eContainer()));
                if (!body.isEmpty()) {
                    it.setEscapePosition(
                            context.getOffset() - context.getPrefix().length() + escapeIndex(header, body));
                }
            });
            acceptor.accept(entry, getProposalPriorities().getDefaultPriority(entry));
        }
    }

    private void createRedefinitionProposals(
            DomainDeclaration domain, ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
        for (var declaration : redefinableMembers(domain, null)) {
            for (var keyword : redefinitionKeywords(declaration)) {
                var header =
                        "redefine " + keyword + (NamingUtil.isAnonymous(declaration) ? "" : " " + header(declaration));
                var body = body(declaration);
                var entry = getProposalCreator().createProposal(header + body, context, kind(declaration), (it) -> {
                    it.setLabel(header);
                    it.setDescription(NamingUtil.getName(declaration.eContainer()));
                    if (!body.isEmpty()) {
                        it.setEscapePosition(
                                context.getOffset() - context.getPrefix().length() + escapeIndex(header, body));
                    }
                });
                acceptor.accept(entry, getProposalPriorities().getDefaultPriority(entry));
            }
        }
    }

    private List<String> redefinitionKeywords(RedefinableDeclaration declaration) {
        if (declaration instanceof FeatureDeclaration feature && feature.getKind() == FeatureKind.FEATURE) {
            return List.of(
                    DeclarationSignatures.keyword(FeatureKind.CONTAINMENT),
                    DeclarationSignatures.keyword(FeatureKind.REFERENCE));
        }
        return List.of(keyword(declaration));
    }

    private List<RedefinableDeclaration> redefinableMembers(DomainDeclaration domain, Declaration editedDeclaration) {
        if (domain == null) {
            return List.of();
        }
        Set<String> declaredNames = new HashSet<>();
        for (var member : ownMembers(domain)) {
            if (member != editedDeclaration && member instanceof RedefinableDeclaration) {
                declaredNames.add(NamingUtil.getName(member));
            }
        }
        return domainMemberCollectionProvider.getParentCollection(domain).getDeclarations().stream()
                .filter(RedefinableDeclaration.class::isInstance)
                .map(RedefinableDeclaration.class::cast)
                .filter(declaration -> !declaredNames.contains(NamingUtil.getName(declaration)))
                .toList();
    }

    private List<? extends Declaration> ownMembers(DomainDeclaration domain) {
        return switch (domain) {
            case ClassDeclaration classDeclaration -> classDeclaration.getMembers();
            case FeatureDeclaration feature -> feature.getInnerFeatures();
            default -> List.of();
        };
    }

    private boolean isRedefinableBy(RedefinableDeclaration declaration, RedefinableDeclaration redefinition) {
        return switch (redefinition) {
            case TransitionDeclaration transition ->
                declaration instanceof TransitionDeclaration inherited && inherited.getKind() == transition.getKind();
            case PropertyDeclaration ignored -> declaration instanceof PropertyDeclaration;
            case FeatureDeclaration feature ->
                declaration instanceof FeatureDeclaration inherited
                        && (inherited.getKind() == feature.getKind() || inherited.getKind() == FeatureKind.FEATURE);
            default -> false;
        };
    }

    private boolean isFollowedByDeclarationRest(ContentAssistContext context) {
        var text = context.getRootNode().getText();
        var index = context.getOffset();
        while (index < text.length() && Character.isJavaIdentifierPart(text.charAt(index))) {
            index++;
        }
        while (index < text.length() && (text.charAt(index) == ' ' || text.charAt(index) == '\t')) {
            index++;
        }
        return index < text.length() && "({:".indexOf(text.charAt(index)) >= 0;
    }

    private String keyword(RedefinableDeclaration declaration) {
        return switch (declaration) {
            case TransitionDeclaration transition -> DeclarationSignatures.keyword(transition.getKind());
            case PropertyDeclaration ignored -> "prop";
            case FeatureDeclaration feature -> DeclarationSignatures.keyword(feature.getKind());
            default -> throw new IllegalArgumentException("Unexpected redefinable declaration " + declaration);
        };
    }

    private String header(RedefinableDeclaration declaration) {
        return switch (declaration) {
            case FeatureDeclaration feature ->
                feature.getName() + ": " + DeclarationSignatures.type(feature.getTypeSpecification());
            case ParametricDeclaration parametric -> DeclarationSignatures.signature(parametric);
            default -> throw new IllegalArgumentException("Unexpected redefinable declaration " + declaration);
        };
    }

    private String body(RedefinableDeclaration declaration) {
        return switch (declaration) {
            case TransitionDeclaration ignored -> " {\n\t\n}";
            case PropertyDeclaration ignored -> " {\n\treturn \n}";
            default -> "";
        };
    }

    private int escapeIndex(String header, String body) {
        return header.length() + body.lastIndexOf('\n');
    }

    private String kind(RedefinableDeclaration declaration) {
        return switch (declaration) {
            case TransitionDeclaration ignored -> ContentAssistEntry.KIND_METHOD;
            case PropertyDeclaration ignored -> ContentAssistEntry.KIND_FUNCTION;
            default -> ContentAssistEntry.KIND_PROPERTY;
        };
    }
}

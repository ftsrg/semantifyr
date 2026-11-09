/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.editor.quickfix;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.PropertyTypeHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Annotation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Argument;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParametricDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.xtext.CrossReference;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.ide.editor.quickfix.DiagnosticResolution;
import org.eclipse.xtext.ide.editor.quickfix.DiagnosticResolutionAcceptor;
import org.eclipse.xtext.ide.editor.quickfix.IQuickFixProvider;
import org.eclipse.xtext.ide.editor.quickfix.ISemanticModification;
import org.eclipse.xtext.ide.editor.quickfix.ITextModification;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.codeActions.ICodeActionService2.Options;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.EObjectAtOffsetHelper;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.scoping.IScopeProvider;

public class OxstsQuickFixProvider implements IQuickFixProvider {

    private static final String LINKING_ISSUE = org.eclipse.xtext.diagnostics.Diagnostic.LINKING_DIAGNOSTIC;

    private static final Set<String> HANDLED_ISSUES = Set.of(
            OxstsValidator.DATA_TYPE_NOT_IN_BUILTIN_ISSUE,
            OxstsValidator.ELEMENT_SHADOWED,
            OxstsValidator.REDEFINED_NOT_FOUND_ISSUE,
            LINKING_ISSUE,
            OxstsValidator.TYPE_MISMATCH,
            OxstsValidator.ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED,
            OxstsValidator.MISSING_INITIALIZER,
            OxstsValidator.INVALID_CALL_ARGUMENTS_COUND);

    private static final String INDENTATION = "    ";

    private static final int MAXIMUM_NEAR_MATCHES = 3;

    @Inject
    private Provider<DiagnosticResolutionAcceptor> acceptorProvider;

    @Inject
    private EObjectAtOffsetHelper eObjectAtOffsetHelper;

    @Inject
    private IScopeProvider scopeProvider;

    @Inject
    private IQualifiedNameConverter qualifiedNameConverter;

    @Inject
    private DomainMemberCollectionProvider domainMemberCollectionProvider;

    @Inject
    private MetaConstantExpressionEvaluatorProvider metaConstantExpressionEvaluatorProvider;

    @Inject
    private PropertyTypeHandler propertyTypeHandler;

    @Inject
    private BuiltinSymbolResolver builtinSymbolResolver;

    @Inject
    private BuiltinAnnotationHandler builtinAnnotationHandler;

    @Override
    public boolean handlesDiagnostic(Diagnostic diagnostic) {
        return diagnostic.getCode() != null
                && diagnostic.getCode().isLeft()
                && HANDLED_ISSUES.contains(diagnostic.getCode().getLeft());
    }

    @Override
    public List<DiagnosticResolution> getResolutions(Options options, Diagnostic diagnostic) {
        var acceptor = acceptorProvider.get();
        if (handlesDiagnostic(diagnostic) && options.getResource() instanceof XtextResource resource) {
            var site = new IssueSite(resource, options.getDocument(), diagnostic, acceptor, eObjectAtOffsetHelper);
            switch (diagnostic.getCode().getLeft()) {
                case OxstsValidator.DATA_TYPE_NOT_IN_BUILTIN_ISSUE -> removeDeclaration(site);
                case OxstsValidator.ELEMENT_SHADOWED -> addRedefine(site);
                case OxstsValidator.REDEFINED_NOT_FOUND_ISSUE -> fixRedefinedNotFound(site);
                case LINKING_ISSUE -> changeReferenceToNearMatch(site);
                case OxstsValidator.TYPE_MISMATCH -> insertNarrowingCast(site);
                case OxstsValidator.ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED -> realizeAbstractMembers(site);
                case OxstsValidator.MISSING_INITIALIZER -> addDefaultInitializer(site);
                case OxstsValidator.INVALID_CALL_ARGUMENTS_COUND -> addMissingArguments(site);
                default -> {}
            }
        }
        return acceptor.getDiagnosticResolutions(options, diagnostic);
    }

    private void removeDeclaration(IssueSite site) {
        site.acceptor().accept("Remove", (ISemanticModification) (diagnostic, object) -> EcoreUtil2::remove);
    }

    private void addRedefine(IssueSite site) {
        if (!(site.element() instanceof RedefinableDeclaration declaration)
                || OxstsUtils.isMemberAbstract(declaration)) {
            return;
        }
        var firstLeaf = firstLeafAfterAnnotations(declaration);
        site.accept("Add redefine", firstLeaf.getOffset(), 0, "redefine ");
    }

    private void fixRedefinedNotFound(IssueSite site) {
        if (!(site.element() instanceof RedefinableDeclaration declaration)) {
            return;
        }
        var redefineKeywords = keywordLeaves(declaration, "redefine");
        if (!redefineKeywords.isEmpty()) {
            var redefineKeyword = redefineKeywords.getFirst();
            var nextLeaf = nextVisibleLeaf(declaration, redefineKeyword);
            var end = nextLeaf != null ? nextLeaf.getOffset() : redefineKeyword.getEndOffset();
            site.accept("Remove redefine", redefineKeyword.getOffset(), end - redefineKeyword.getOffset(), "");
        }
        if (declaration.getName() == null || declaration.getRedefined() != null) {
            return;
        }
        var containerDomain = EcoreUtil2.getContainerOfType(declaration.eContainer(), DomainDeclaration.class);
        var candidates = domainMemberCollectionProvider.getParentCollection(containerDomain).getDeclarations().stream()
                .filter(candidate -> {
                    return EcoreUtil2.isAssignableFrom(declaration.eClass(), candidate.eClass());
                })
                .map(NamingUtil::getName)
                .filter(Objects::nonNull)
                .toList();
        var nameNode = NodeModelUtils.findNodesForFeature(declaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME)
                .getFirst();
        for (var match : nearMatches(declaration.getName(), candidates)) {
            site.accept("Change to " + match, nameNode.getOffset(), nameNode.getLength(), match);
        }
    }

    private void changeReferenceToNearMatch(IssueSite site) {
        var rootNode = site.resource().getParseResult().getRootNode();
        INode node = NodeModelUtils.findLeafNodeAtOffset(rootNode, site.offset());
        CrossReference crossReference = null;
        while (node != null && crossReference == null) {
            crossReference = GrammarUtil.containingCrossReference(node.getGrammarElement());
            if (crossReference == null) {
                node = node.getParent();
            }
        }
        if (node == null) {
            return;
        }
        var referencingElement = NodeModelUtils.findActualSemanticObjectFor(node);
        var reference = GrammarUtil.getReference(crossReference, referencingElement.eClass());
        if (reference == null) {
            return;
        }
        var scope = scopeProvider.getScope(referencingElement, reference);
        var candidates = StreamSupport.stream(scope.getAllElements().spliterator(), false)
                .map(description -> {
                    return qualifiedNameConverter.toString(description.getName());
                })
                .toList();
        var written = NodeModelUtils.getTokenText(node);
        for (var match : nearMatches(written, candidates)) {
            site.accept("Change to " + match, node.getOffset(), node.getLength(), match);
        }
    }

    private void insertNarrowingCast(IssueSite site) {
        var data = issueData(site.diagnostic());
        var expression = mismatchedExpression(site.element());
        if (data.isEmpty() || expression == null) {
            return;
        }
        var castClassName = data.getFirst();
        var node = NodeModelUtils.findActualNodeFor(expression);
        var written = site.text(node.getOffset(), node.getEndOffset());
        if (!(expression instanceof ReferenceExpression || expression instanceof LiteralExpression)) {
            written = "(" + written + ")";
        }
        site.accept("Cast to " + castClassName, node.getOffset(), node.getLength(), written + " as " + castClassName);
    }

    private void realizeAbstractMembers(IssueSite site) {
        if (!(site.element() instanceof ClassDeclaration classDeclaration)) {
            return;
        }
        if (!builtinAnnotationHandler.isVerificationCase(classDeclaration)) {
            var classKeyword = keywordLeaves(classDeclaration, "class").getFirst();
            site.accept("Make " + classDeclaration.getName() + " abstract", classKeyword.getOffset(), 0, "abstract ");
        }
        var memberNames = issueData(site.diagnostic());
        var members = domainMemberCollectionProvider.getMemberCollection(classDeclaration).getDeclarations().stream()
                .filter(OxstsUtils::isDeclarationAbstract)
                .filter(member -> {
                    return member.eContainer() != classDeclaration && memberNames.contains(NamingUtil.getName(member));
                })
                .toList();
        if (members.isEmpty()) {
            return;
        }
        var classNode = NodeModelUtils.findActualNodeFor(classDeclaration);
        var classIndentation = site.indentationAt(classNode.getOffset());
        var memberIndentation = classIndentation + INDENTATION;
        var stubs = members.stream()
                .map(member -> {
                    return stub(member, memberIndentation);
                })
                .collect(Collectors.joining("\n"));
        if (!classDeclaration.getMembers().isEmpty()) {
            stubs = "\n" + stubs;
        }
        var label = "Redefine " + members.stream().map(NamingUtil::getName).collect(Collectors.joining(", "));
        var closingBraces = keywordLeaves(classDeclaration, "}");
        if (closingBraces.isEmpty()) {
            var lastLeaf = lastVisibleLeaf(classDeclaration);
            var end = classNode.getEndOffset();
            var start = ";".equals(lastLeaf.getText()) ? lastLeaf.getOffset() : end;
            site.accept(label, start, end - start, " {\n" + stubs + classIndentation + "}");
            return;
        }
        var closingBrace = closingBraces.getLast();
        var lineStart = site.lineStartOf(closingBrace.getOffset());
        if (site.text(lineStart, closingBrace.getOffset()).isBlank()) {
            site.accept(label, lineStart, 0, stubs);
        } else {
            site.accept(label, closingBrace.getOffset(), 0, "\n" + stubs + classIndentation);
        }
    }

    private void addDefaultInitializer(IssueSite site) {
        if (!(site.element() instanceof VariableDeclaration variable)
                || variable.getExpression() != null
                || variable.getTypeSpecification() == null) {
            return;
        }
        var value = defaultValueOrNull(variable.getTypeSpecification());
        if (value == null) {
            return;
        }
        var typeNode = NodeModelUtils.findActualNodeFor(variable.getTypeSpecification());
        site.accept("Initialize with " + value, typeNode.getEndOffset(), 0, " := " + value);
    }

    private void addMissingArguments(IssueSite site) {
        var call = EcoreUtil2.getContainerOfType(site.element(), CallSuffixExpression.class);
        var missingNames = issueData(site.diagnostic());
        if (call == null || missingNames.isEmpty()) {
            return;
        }
        if (!(metaConstantExpressionEvaluatorProvider.evaluate(call.getPrimary())
                instanceof ParametricDeclaration parametricDeclaration)) {
            return;
        }
        var missingArguments = parametricDeclaration.getParameters().stream()
                .filter(parameter -> {
                    return missingNames.contains(parameter.getName());
                })
                .map(this::namedPlaceholder)
                .collect(Collectors.joining(", "));
        var closingParentheses = keywordLeaves(call, ")");
        if (closingParentheses.isEmpty()) {
            return;
        }
        var closingParenthesis = closingParentheses.getLast();
        if (!call.getArguments().isEmpty()) {
            var previousLeaf = previousVisibleLeaf(call, closingParenthesis);
            var separator = previousLeaf != null && ",".equals(previousLeaf.getText()) ? " " : ", ";
            missingArguments = separator + missingArguments;
        }
        site.accept("Add missing arguments", closingParenthesis.getOffset(), 0, missingArguments);
    }

    private String namedPlaceholder(ParameterDeclaration parameter) {
        var value = defaultValueOrNull(parameter.getTypeSpecification());
        return parameter.getName() + " = " + Objects.requireNonNullElse(value, "nothing");
    }

    private String stub(Declaration member, String indentation) {
        if (member instanceof TransitionDeclaration transition) {
            var kind = transition.getKind() == TransitionKind.INIT ? "init" : "tran";
            return indentation + "redefine " + kind + signature(transition) + " {\n" + indentation + "}\n";
        }
        var property = (PropertyDeclaration) member;
        var returnType = property.getReturnType() != null ? ": " + tokenText(property.getReturnType()) : "";
        var value = defaultValueOrNull(propertyTypeHandler.getPropertyReturnType(property));
        return indentation + "redefine prop" + signature(property) + returnType + " {\n"
                + indentation + INDENTATION + "return " + Objects.requireNonNullElse(value, "nothing") + "\n"
                + indentation + "}\n";
    }

    private String signature(ParametricDeclaration declaration) {
        if (declaration.getName() == null) {
            return "";
        }
        var parameters =
                declaration.getParameters().stream().map(this::tokenText).collect(Collectors.joining(", "));
        return " " + declaration.getName() + "(" + parameters + ")";
    }

    private String defaultValueOrNull(TypeSpecification typeSpecification) {
        if (typeSpecification == null || typeSpecification.getMultiplicity() != null) {
            return null;
        }
        var domain = typeSpecification.getDomain();
        if (domain == null || domain.eIsProxy()) {
            return null;
        }
        if (domain == builtinSymbolResolver.boolDatatype(typeSpecification)) {
            return "false";
        }
        if (domain == builtinSymbolResolver.intDatatype(typeSpecification)) {
            return "0";
        }
        if (domain == builtinSymbolResolver.realDatatype(typeSpecification)) {
            return "0.0";
        }
        if (domain == builtinSymbolResolver.stringDatatype(typeSpecification)) {
            return "\"\"";
        }
        if (domain instanceof EnumDeclaration enumDeclaration
                && !enumDeclaration.getLiterals().isEmpty()) {
            return enumDeclaration.getName() + "::"
                    + enumDeclaration.getLiterals().getFirst().getName();
        }
        return null;
    }

    private Expression mismatchedExpression(EObject element) {
        for (var current = element; current != null; current = current.eContainer()) {
            if (current instanceof Argument argument) {
                return argument.getExpression();
            }
            if (current instanceof AssignmentOperation assignment) {
                return assignment.getExpression();
            }
            if (current instanceof VariableDeclaration variable) {
                return variable.getExpression();
            }
            if (current instanceof FeatureDeclaration feature) {
                return feature.getExpression();
            }
            if (current instanceof PropertyDeclaration property) {
                return property.getExpression();
            }
        }
        return null;
    }

    private List<String> nearMatches(String written, List<String> candidates) {
        var threshold = Math.max(1, written.length() / 3);
        var bestDistance = Integer.MAX_VALUE;
        var best = new ArrayList<String>();
        for (var candidate : candidates.stream().distinct().sorted().toList()) {
            var distance = editDistance(written, candidate);
            if (distance == 0 || distance > threshold || distance > bestDistance) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best.clear();
            }
            best.add(candidate);
        }
        return best.stream().limit(MAXIMUM_NEAR_MATCHES).toList();
    }

    private static int editDistance(String first, String second) {
        var previous = new int[second.length() + 1];
        var current = new int[second.length() + 1];
        for (int column = 0; column <= second.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= first.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= second.length(); column++) {
                var substitution = first.charAt(row - 1) == second.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1), previous[column - 1] + substitution);
            }
            var swap = previous;
            previous = current;
            current = swap;
        }
        return previous[second.length()];
    }

    private String tokenText(EObject element) {
        return NodeModelUtils.getTokenText(NodeModelUtils.findActualNodeFor(element));
    }

    private List<ILeafNode> visibleLeaves(EObject element) {
        var leaves = new ArrayList<ILeafNode>();
        for (var leaf : NodeModelUtils.findActualNodeFor(element).getLeafNodes()) {
            if (!leaf.isHidden()) {
                leaves.add(leaf);
            }
        }
        return leaves;
    }

    private ILeafNode firstLeafAfterAnnotations(Declaration declaration) {
        return visibleLeaves(declaration).stream()
                .filter(leaf -> {
                    return EcoreUtil2.getContainerOfType(leaf.getSemanticElement(), Annotation.class) == null;
                })
                .findFirst()
                .orElseThrow();
    }

    private List<ILeafNode> keywordLeaves(EObject element, String keyword) {
        return visibleLeaves(element).stream()
                .filter(leaf -> {
                    return leaf.getGrammarElement() instanceof Keyword grammarKeyword
                            && keyword.equals(grammarKeyword.getValue())
                            && leaf.getSemanticElement() == element;
                })
                .toList();
    }

    private ILeafNode nextVisibleLeaf(EObject element, ILeafNode leaf) {
        var leaves = visibleLeaves(element);
        var index = leaves.indexOf(leaf);
        return index + 1 < leaves.size() ? leaves.get(index + 1) : null;
    }

    private ILeafNode previousVisibleLeaf(EObject element, ILeafNode leaf) {
        var leaves = visibleLeaves(element);
        var index = leaves.indexOf(leaf);
        return index > 0 ? leaves.get(index - 1) : null;
    }

    private ILeafNode lastVisibleLeaf(EObject element) {
        return visibleLeaves(element).getLast();
    }

    private static List<String> issueData(Diagnostic diagnostic) {
        var data = diagnostic.getData();
        if (data instanceof String[] values) {
            return List.of(values);
        }
        if (data instanceof JsonArray values) {
            return values.asList().stream().map(JsonElement::getAsString).toList();
        }
        return List.of();
    }

    private record IssueSite(
            XtextResource resource,
            Document document,
            Diagnostic diagnostic,
            DiagnosticResolutionAcceptor acceptor,
            EObjectAtOffsetHelper eObjectAtOffsetHelper) {

        int offset() {
            return document.getOffSet(diagnostic.getRange().getStart());
        }

        EObject element() {
            return eObjectAtOffsetHelper.resolveContainedElementAt(resource, offset());
        }

        String text(int start, int end) {
            return document.getContents().substring(start, end);
        }

        int lineStartOf(int offset) {
            var line = document.getPosition(offset).getLine();
            return document.getOffSet(new Position(line, 0));
        }

        String indentationAt(int offset) {
            var lineStart = lineStartOf(offset);
            var end = lineStart;
            var contents = document.getContents();
            while (end < contents.length() && (contents.charAt(end) == ' ' || contents.charAt(end) == '\t')) {
                end++;
            }
            return contents.substring(lineStart, end);
        }

        void accept(String label, int offset, int length, String replacement) {
            var range = new Range(document.getPosition(offset), document.getPosition(offset + length));
            var edits = List.of(new TextEdit(range, replacement));
            acceptor.accept(label, (ITextModification) (resolvedDiagnostic, resolvedObject, resolvedDocument) -> edits);
        }
    }
}

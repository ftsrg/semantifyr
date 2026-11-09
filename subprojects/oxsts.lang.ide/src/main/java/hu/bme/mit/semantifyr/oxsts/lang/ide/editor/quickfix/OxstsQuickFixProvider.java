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
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ArrayEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.BooleanEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EnumLiteralEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.InfinityEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.NothingEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RealEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.StringEvaluation;
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsFactory;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParametricDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
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
import org.eclipse.xtext.resource.ILocationInFileProvider;
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
            OxstsValidator.INVALID_CALL_ARGUMENTS_COUND,
            OxstsValidator.COULD_BE_INLINE,
            OxstsValidator.CONSTANT_EXPRESSION);

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

    @Inject
    private ConstantExpressionEvaluatorProvider constantExpressionEvaluatorProvider;

    @Inject
    private ILocationInFileProvider locationInFileProvider;

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
                case OxstsValidator.COULD_BE_INLINE -> makeInline(site);
                case OxstsValidator.CONSTANT_EXPRESSION -> inlineAsConstant(site);
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
        for (var match : nearMatches(declaration.getName(), candidates)) {
            acceptModelChange(site, "Change to " + match, declaration, (redefining, resourceSet) -> {
                redefining.setName(match);
            });
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
        var targets = new LinkedHashMap<String, URI>();
        for (var description :
                scopeProvider.getScope(referencingElement, reference).getAllElements()) {
            targets.putIfAbsent(qualifiedNameConverter.toString(description.getName()), description.getEObjectURI());
        }
        var written = NodeModelUtils.getTokenText(node);
        for (var match : nearMatches(written, List.copyOf(targets.keySet()))) {
            var targetUri = targets.get(match);
            acceptModelChange(site, "Change to " + match, referencingElement, (referencing, resourceSet) -> {
                referencing.eSet(reference, resourceSet.getEObject(targetUri, true));
            });
        }
    }

    private void insertNarrowingCast(IssueSite site) {
        var data = issueData(site.diagnostic());
        var expression = mismatchedExpression(site.element());
        if (data.size() != 2 || expression == null) {
            return;
        }
        var castClassUri = URI.createURI(data.get(1));
        acceptModelChange(site, "Cast to " + data.getFirst(), expression, (body, resourceSet) -> {
            var typeSpecification = OxstsFactory.eINSTANCE.createTypeSpecification();
            typeSpecification.setDomain((DomainDeclaration) resourceSet.getEObject(castClassUri, true));
            var cast = OxstsFactory.eINSTANCE.createCastExpression();
            EcoreUtil.replace(body, cast);
            cast.setBody(body);
            cast.setTypespecification(typeSpecification);
        });
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
        var label = "Redefine " + members.stream().map(NamingUtil::getName).collect(Collectors.joining(", "));
        acceptModelChange(site, label, classDeclaration, (realizing, resourceSet) -> {
            for (var member : members) {
                realizing.getMembers().add(stub(resolve(resourceSet, member)));
            }
        });
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
        acceptModelChange(site, "Initialize with " + value, variable, (initialized, resourceSet) -> {
            initialized.setExpression(defaultExpressionOrNull(initialized.getTypeSpecification()));
        });
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
        var missingParameters = parametricDeclaration.getParameters().stream()
                .filter(parameter -> {
                    return missingNames.contains(parameter.getName());
                })
                .toList();
        acceptModelChange(site, "Add missing arguments", call, (completed, resourceSet) -> {
            for (var missingParameter : missingParameters) {
                var parameter = resolve(resourceSet, missingParameter);
                var argument = OxstsFactory.eINSTANCE.createArgument();
                argument.setParameter(parameter);
                argument.setExpression(placeholderExpression(parameter.getTypeSpecification()));
                completed.getArguments().add(argument);
            }
        });
    }

    private void makeInline(IssueSite site) {
        site.acceptor().accept("Make inline", (ISemanticModification) (diagnostic, object) -> {
            return operation -> {
                if (operation instanceof IfOperation ifOperation) {
                    var inlineIfOperation = OxstsFactory.eINSTANCE.createInlineIfOperation();
                    inlineIfOperation.setGuard(ifOperation.getGuard());
                    inlineIfOperation.setBody(ifOperation.getBody());
                    inlineIfOperation.setElse(ifOperation.getElse());
                    EcoreUtil2.replace(ifOperation, inlineIfOperation);
                } else if (operation instanceof ForOperation forOperation) {
                    var inlineSeqFor = OxstsFactory.eINSTANCE.createInlineSeqFor();
                    inlineSeqFor.setLoopVariable(forOperation.getLoopVariable());
                    inlineSeqFor.setRangeExpression(forOperation.getRangeExpression());
                    inlineSeqFor.setBody(forOperation.getBody());
                    EcoreUtil2.replace(forOperation, inlineSeqFor);
                }
            };
        });
    }

    private void inlineAsConstant(IssueSite site) {
        site.acceptor().accept("Inline as constant", (ISemanticModification) (diagnostic, object) -> {
            var expression = hintedExpression(diagnostic, object);
            var literal = literalOf(constantExpressionEvaluatorProvider.evaluate(expression));
            return element -> {
                EcoreUtil2.replace(expression, literal);
            };
        });
    }

    private Expression hintedExpression(Diagnostic diagnostic, EObject object) {
        var text = ((XtextResource) object.eResource())
                .getParseResult()
                .getRootNode()
                .getText();
        var start = offsetOf(text, diagnostic.getRange().getStart());
        var end = offsetOf(text, diagnostic.getRange().getEnd());
        for (var current = object; current instanceof Expression expression; current = current.eContainer()) {
            var region = locationInFileProvider.getFullTextRegion(expression);
            if (region.getOffset() == start && region.getOffset() + region.getLength() == end) {
                return expression;
            }
        }
        throw new IllegalStateException("No expression covers the constant expression hint");
    }

    private static int offsetOf(String text, Position position) {
        var offset = 0;
        for (int line = 0; line < position.getLine(); line++) {
            offset = text.indexOf('\n', offset) + 1;
        }
        return offset + position.getCharacter();
    }

    private Expression literalOf(ExpressionEvaluation value) {
        var factory = OxstsFactory.eINSTANCE;
        return switch (value) {
            case BooleanEvaluation booleanValue -> {
                var literal = factory.createLiteralBoolean();
                literal.setValue(booleanValue.value());
                yield literal;
            }
            case IntegerEvaluation integerValue -> {
                var literal = factory.createLiteralInteger();
                literal.setValue(Math.abs(integerValue.value()));
                yield integerValue.value() < 0 ? negated(literal) : literal;
            }
            case RealEvaluation realValue -> {
                var literal = factory.createLiteralReal();
                literal.setValue(Math.abs(realValue.value()));
                yield realValue.value() < 0 ? negated(literal) : literal;
            }
            case StringEvaluation stringValue -> {
                var literal = factory.createLiteralString();
                literal.setValue(stringValue.value());
                yield literal;
            }
            case EnumLiteralEvaluation enumValue -> {
                var reference = factory.createElementReference();
                reference.setElement(enumValue.literal());
                yield reference;
            }
            case ArrayEvaluation arrayValue -> {
                var literal = factory.createArrayLiteral();
                for (var element : arrayValue.elements()) {
                    literal.getValues().add(literalOf(element));
                }
                yield literal;
            }
            case NothingEvaluation nothing -> factory.createLiteralNothing();
            case InfinityEvaluation infinity -> factory.createLiteralInfinity();
            default -> throw new IllegalStateException("No literal for " + value);
        };
    }

    private Expression negated(Expression body) {
        var operator = OxstsFactory.eINSTANCE.createArithmeticUnaryOperator();
        operator.setOp(UnaryOp.MINUS);
        operator.setBody(body);
        return operator;
    }

    private Declaration stub(Declaration member) {
        if (member instanceof TransitionDeclaration transition) {
            var stub = OxstsFactory.eINSTANCE.createTransitionDeclaration();
            stub.setAnnotation(OxstsFactory.eINSTANCE.createAnnotationContainer());
            stub.setRedefine(true);
            stub.setKind(transition.getKind());
            stub.setName(transition.getName());
            stub.getParameters().addAll(EcoreUtil.copyAll(transition.getParameters()));
            stub.getBranches().add(OxstsFactory.eINSTANCE.createSequenceOperation());
            return stub;
        }
        var property = (PropertyDeclaration) member;
        var stub = OxstsFactory.eINSTANCE.createPropertyDeclaration();
        stub.setAnnotation(OxstsFactory.eINSTANCE.createAnnotationContainer());
        stub.setRedefine(true);
        stub.setName(property.getName());
        stub.getParameters().addAll(EcoreUtil.copyAll(property.getParameters()));
        if (property.getReturnType() != null) {
            stub.setReturnType(EcoreUtil.copy(property.getReturnType()));
        }
        stub.setExpression(placeholderExpression(propertyTypeHandler.getPropertyReturnType(property)));
        return stub;
    }

    private Expression placeholderExpression(TypeSpecification typeSpecification) {
        var expression = defaultExpressionOrNull(typeSpecification);
        if (expression == null) {
            return OxstsFactory.eINSTANCE.createLiteralNothing();
        }
        return expression;
    }

    private Expression defaultExpressionOrNull(TypeSpecification typeSpecification) {
        if (typeSpecification == null || typeSpecification.getMultiplicity() != null) {
            return null;
        }
        var domain = typeSpecification.getDomain();
        if (domain == null || domain.eIsProxy()) {
            return null;
        }
        if (domain == builtinSymbolResolver.boolDatatype(typeSpecification)) {
            var literal = OxstsFactory.eINSTANCE.createLiteralBoolean();
            literal.setValue(false);
            return literal;
        }
        if (domain == builtinSymbolResolver.intDatatype(typeSpecification)) {
            var literal = OxstsFactory.eINSTANCE.createLiteralInteger();
            literal.setValue(0);
            return literal;
        }
        if (domain == builtinSymbolResolver.realDatatype(typeSpecification)) {
            var literal = OxstsFactory.eINSTANCE.createLiteralReal();
            literal.setValue(0.0);
            return literal;
        }
        if (domain == builtinSymbolResolver.stringDatatype(typeSpecification)) {
            var literal = OxstsFactory.eINSTANCE.createLiteralString();
            literal.setValue("");
            return literal;
        }
        if (domain instanceof EnumDeclaration enumDeclaration
                && !enumDeclaration.getLiterals().isEmpty()) {
            var reference = OxstsFactory.eINSTANCE.createElementReference();
            reference.setElement(enumDeclaration.getLiterals().getFirst());
            return reference;
        }
        return null;
    }

    private <T extends EObject> void acceptModelChange(IssueSite site, String label, T target, ModelChange<T> change) {
        site.acceptor().accept(label, (ISemanticModification) (diagnostic, object) -> context -> {
            var resourceSet = context.eResource().getResourceSet();
            change.apply(resolve(resourceSet, target), resourceSet);
        });
    }

    // The change serializer works on a fresh resource set, so objects of the open resource are looked up there by URI.
    @SuppressWarnings("unchecked")
    private static <T extends EObject> T resolve(ResourceSet resourceSet, T original) {
        return (T) resourceSet.getEObject(EcoreUtil.getURI(original), true);
    }

    @FunctionalInterface
    private interface ModelChange<T extends EObject> {
        void apply(T target, ResourceSet resourceSet);
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

        void accept(String label, int offset, int length, String replacement) {
            var range = new Range(document.getPosition(offset), document.getPosition(offset + length));
            var edits = List.of(new TextEdit(range, replacement));
            acceptor.accept(label, (ITextModification) (resolvedDiagnostic, resolvedObject, resolvedDocument) -> edits);
        }
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.inlayhints;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.lang.ide.server.inlayhints.InlayHintService;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ArrayEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.BooleanEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EnumLiteralEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.InfinityEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.NothingEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RealEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.StringEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.ExpressionModalityEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.Modality;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ImmutableTypeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.VariableTypeEvaluator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AbstractForOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Annotation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Argument;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParametricDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.DocumentExtensions;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.service.OperationCanceledManager;
import org.eclipse.xtext.util.CancelIndicator;

public class OxstsInlayHintService implements InlayHintService {

    @Inject
    private DocumentExtensions documentExtensions;

    @Inject
    private OperationCanceledManager operationCanceledManager;

    @Inject
    private VariableTypeEvaluator variableTypeEvaluator;

    @Inject
    private ExpressionModalityEvaluatorProvider modalityEvaluatorProvider;

    @Inject
    private ExpressionTypeEvaluatorProvider typeEvaluatorProvider;

    @Inject
    private ConstantExpressionEvaluatorProvider constantExpressionEvaluatorProvider;

    @Override
    public List<InlayHint> computeInlayHints(
            Document document, XtextResource resource, InlayHintParams params, CancelIndicator cancelIndicator) {
        var hints = new ArrayList<InlayHint>();
        var contents = EcoreUtil.getAllProperContents(resource, false);
        while (contents.hasNext()) {
            operationCanceledManager.checkCanceled(cancelIndicator);
            switch (contents.next()) {
                case CallSuffixExpression call -> addParameterHints(document, call.getArguments(), callee(call), hints);
                case Annotation annotation ->
                    addParameterHints(document, annotation.getArguments(), annotation.getDeclaration(), hints);
                case AbstractForOperation forOperation -> addLoopVariableTypeHint(document, forOperation, hints);
                case FeatureDeclaration feature -> {
                    addMultiplicityHint(document, feature, hints);
                    addValueHint(document, feature, hints);
                }
                default -> {}
            }
        }
        return hints.stream()
                .filter(hint -> isWithin(hint.getPosition(), params.getRange()))
                .toList();
    }

    private NamedElement callee(CallSuffixExpression call) {
        return switch (call.getPrimary()) {
            case ElementReference reference -> reference.getElement();
            case NavigationSuffixExpression navigation -> navigation.getMember();
            default -> null;
        };
    }

    private void addParameterHints(
            Document document, List<Argument> arguments, NamedElement callee, List<InlayHint> hints) {
        if (!(callee instanceof ParametricDeclaration parametricDeclaration) || callee.eIsProxy()) {
            return;
        }
        var parameters = parametricDeclaration.getParameters();
        for (var position = 0; position < arguments.size() && position < parameters.size(); position++) {
            var argument = arguments.get(position);
            if (argument.getParameter() != null) {
                continue;
            }
            var parameter = parameters.get(position);
            if (isNamedLike(argument.getExpression(), parameter)) {
                continue;
            }
            var label = new InlayHintLabelPart(parameter.getName() + ":");
            label.setLocation(documentExtensions.newLocation(parameter));
            var hint = new InlayHint();
            hint.setPosition(document.getPosition(startOffset(argument)));
            hint.setLabel(List.of(label));
            hint.setKind(InlayHintKind.Parameter);
            hint.setPaddingRight(true);
            hints.add(hint);
        }
    }

    private boolean isNamedLike(Expression expression, NamedElement parameter) {
        return expression instanceof ElementReference reference
                && !reference.getElement().eIsProxy()
                && Objects.equals(reference.getElement().getName(), parameter.getName());
    }

    private void addLoopVariableTypeHint(Document document, AbstractForOperation forOperation, List<InlayHint> hints) {
        var loopVariable = forOperation.getLoopVariable();
        if (loopVariable == null || loopVariable.getTypeSpecification() != null) {
            return;
        }
        if (!(variableTypeEvaluator.evaluate(loopVariable) instanceof ImmutableTypeEvaluation type)) {
            return;
        }
        var domain = type.getDomain();
        var label = new InlayHintLabelPart(NamingUtil.getName(domain));
        label.setLocation(documentExtensions.newLocation(domain));
        var hint = new InlayHint();
        hint.setPosition(document.getPosition(endOffset(loopVariable)));
        hint.setLabel(List.of(new InlayHintLabelPart(": "), label));
        hint.setKind(InlayHintKind.Type);
        hints.add(hint);
    }

    private void addMultiplicityHint(Document document, FeatureDeclaration feature, List<InlayHint> hints) {
        var typeSpecification = feature.getTypeSpecification();
        if (typeSpecification == null || typeSpecification.getMultiplicity() != null) {
            return;
        }
        var hint = new InlayHint();
        hint.setPosition(document.getPosition(endOffset(typeSpecification)));
        hint.setLabel("[1]");
        hint.setKind(InlayHintKind.Type);
        hints.add(hint);
    }

    private void addValueHint(Document document, FeatureDeclaration feature, List<InlayHint> hints) {
        var expression = feature.getExpression();
        if (expression == null || isWrittenValue(expression)) {
            return;
        }
        if (modalityEvaluatorProvider.evaluate(expression) != Modality.CONSTANT
                || !(typeEvaluatorProvider.evaluate(expression) instanceof ImmutableTypeEvaluation)) {
            return;
        }
        ExpressionEvaluation value;
        try {
            value = constantExpressionEvaluatorProvider.evaluate(expression);
        } catch (EvaluationFailureException e) {
            return;
        }
        var hint = new InlayHint();
        hint.setPosition(document.getPosition(endOffset(expression)));
        hint.setLabel("= " + render(value));
        hint.setPaddingLeft(true);
        hints.add(hint);
    }

    private boolean isWrittenValue(Expression expression) {
        return switch (expression) {
            case LiteralExpression _, ElementReference _, RangeExpression _ -> true;
            case ArithmeticUnaryOperator unary -> unary.getBody() instanceof LiteralExpression;
            default -> false;
        };
    }

    private String render(ExpressionEvaluation value) {
        return switch (value) {
            case IntegerEvaluation(var integer) -> String.valueOf(integer);
            case BooleanEvaluation(var bool) -> String.valueOf(bool);
            case RealEvaluation(var real) -> String.valueOf(real);
            case StringEvaluation(var string) -> "\"" + string + "\"";
            case EnumLiteralEvaluation(var literal) ->
                NamingUtil.getName(literal.eContainer()) + "::" + NamingUtil.getName(literal);
            case ArrayEvaluation(var elements) ->
                elements.stream().map(this::render).collect(Collectors.joining(", ", "[", "]"));
            case NothingEvaluation _ -> "nothing";
            case InfinityEvaluation _ -> "*";
            default -> value.toString();
        };
    }

    private int startOffset(EObject element) {
        return NodeModelUtils.findActualNodeFor(element).getOffset();
    }

    private int endOffset(EObject element) {
        return NodeModelUtils.findActualNodeFor(element).getEndOffset();
    }

    private boolean isWithin(Position position, Range range) {
        return !isBefore(position, range.getStart()) && !isBefore(range.getEnd(), position);
    }

    private boolean isBefore(Position first, Position second) {
        if (first.getLine() != second.getLine()) {
            return first.getLine() < second.getLine();
        }
        return first.getCharacter() < second.getCharacter();
    }
}

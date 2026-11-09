/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.signatureHelp;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.hover.OxstsDeclarationRenderer;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Annotation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Argument;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParametricDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple;
import org.eclipse.xtext.documentation.IEObjectDocumentationProvider;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.signatureHelp.ISignatureHelpService;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

@Singleton
public class OxstsSignatureHelpService implements ISignatureHelpService {

    @Inject
    private MetaConstantExpressionEvaluatorProvider metaConstantExpressionEvaluatorProvider;

    @Inject
    private OxstsDeclarationRenderer declarationRenderer;

    @Inject
    private IEObjectDocumentationProvider documentationProvider;

    private record ArgumentList(int openOffset, int closeOffset, List<Integer> commaOffsets) {

        boolean contains(int offset) {
            return openOffset < offset && offset <= closeOffset;
        }

        int argumentIndexAt(int offset) {
            return (int) commaOffsets.stream()
                    .filter(commaOffset -> {
                        return commaOffset < offset;
                    })
                    .count();
        }
    }

    @Override
    public SignatureHelp getSignatureHelp(
            Document document, XtextResource resource, SignatureHelpParams params, CancelIndicator cancelIndicator) {
        var parseResult = resource.getParseResult();
        if (parseResult == null) {
            return EMPTY;
        }
        var offset = document.getOffSet(params.getPosition());
        var leaf = NodeModelUtils.findLeafNodeAtOffset(parseResult.getRootNode(), offset);
        if (leaf == null && offset > 0) {
            leaf = NodeModelUtils.findLeafNodeAtOffset(parseResult.getRootNode(), offset - 1);
        }
        if (leaf == null) {
            return EMPTY;
        }

        var element = NodeModelUtils.findActualSemanticObjectFor(leaf);
        while (element != null) {
            var signatureHelp = getSignatureHelp(element, offset);
            if (signatureHelp != null) {
                return signatureHelp;
            }
            element = element.eContainer();
        }
        return EMPTY;
    }

    protected SignatureHelp getSignatureHelp(EObject element, int offset) {
        return switch (element) {
            case CallSuffixExpression call -> {
                var callee = metaConstantExpressionEvaluatorProvider.evaluate(call.getPrimary());
                yield getSignatureHelp(
                        element,
                        NodeModelUtils.getNode(call.getPrimary()),
                        call.getArguments(),
                        callee,
                        offset);
            }
            case Annotation annotation -> {
                var nameNodes = NodeModelUtils.findNodesForFeature(annotation, OxstsPackage.Literals.ANNOTATION__DECLARATION);
                yield getSignatureHelp(
                        element,
                        nameNodes.isEmpty() ? null : nameNodes.getLast(),
                        annotation.getArguments(),
                        annotation.getDeclaration(),
                        offset);
            }
            default -> null;
        };
    }

    protected SignatureHelp getSignatureHelp(
            EObject element, INode calleeNode, List<Argument> arguments, EObject callee, int offset) {
        if (calleeNode == null || !(callee instanceof ParametricDeclaration declaration) || declaration.eIsProxy()) {
            return null;
        }
        var argumentList = findArgumentList(NodeModelUtils.getNode(element), calleeNode.getEndOffset(), arguments);
        if (argumentList == null || !argumentList.contains(offset)) {
            return null;
        }

        var argumentIndex = argumentList.argumentIndexAt(offset);
        var signature = createSignature(declaration);
        var signatureHelp = new SignatureHelp(List.of(signature), 0, null);
        signatureHelp.setActiveParameter(getActiveParameter(declaration, arguments, argumentIndex));
        return signatureHelp;
    }

    protected ArgumentList findArgumentList(INode node, int calleeEndOffset, List<Argument> arguments) {
        var argumentNodes = arguments.stream()
                .map(NodeModelUtils::getNode)
                .filter(Objects::nonNull)
                .toList();
        var openOffset = -1;
        var closeOffset = Integer.MAX_VALUE;
        var commaOffsets = new ArrayList<Integer>();
        for (ILeafNode leaf : node.getLeafNodes()) {
            if (leaf.isHidden() || leaf.getOffset() < calleeEndOffset || isInside(leaf, argumentNodes)) {
                continue;
            }
            var text = leaf.getText();
            if (openOffset < 0) {
                if ("(".equals(text)) {
                    openOffset = leaf.getOffset();
                }
            } else if (",".equals(text)) {
                commaOffsets.add(leaf.getOffset());
            } else if (")".equals(text)) {
                closeOffset = leaf.getOffset();
            }
        }
        if (openOffset < 0) {
            return null;
        }
        return new ArgumentList(openOffset, closeOffset, commaOffsets);
    }

    protected boolean isInside(ILeafNode leaf, List<? extends INode> argumentNodes) {
        return argumentNodes.stream().anyMatch(argumentNode -> {
            return argumentNode.getOffset() <= leaf.getOffset() && leaf.getEndOffset() <= argumentNode.getEndOffset();
        });
    }

    protected SignatureInformation createSignature(ParametricDeclaration declaration) {
        var label = new StringBuilder(NamingUtil.getName(declaration)).append("(");
        var parameters = new ArrayList<ParameterInformation>();
        for (var parameter : declaration.getParameters()) {
            if (!parameters.isEmpty()) {
                label.append(", ");
            }
            var start = label.length();
            label.append(declarationRenderer.renderParameter(parameter));
            var parameterInformation = new ParameterInformation();
            parameterInformation.setLabel(Either.forRight(Tuple.two(start, label.length())));
            parameters.add(parameterInformation);
        }
        label.append(")");
        if (declaration instanceof PropertyDeclaration property) {
            label.append(": ").append(declarationRenderer.renderTypeSpecification(property.getReturnType()));
        }

        var signature = new SignatureInformation(label.toString());
        signature.setParameters(parameters);
        var documentation = documentationProvider.getDocumentation(declaration);
        if (documentation != null && !documentation.isBlank()) {
            signature.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, documentation));
        }
        return signature;
    }

    // A named argument activates its parameter wherever it stands; a positional one the parameter at its position.
    protected int getActiveParameter(ParametricDeclaration declaration, List<Argument> arguments, int argumentIndex) {
        if (argumentIndex < arguments.size()) {
            var named = arguments.get(argumentIndex).getParameter();
            if (named != null && !named.eIsProxy()) {
                var parameters = declaration.getParameters();
                for (var index = 0; index < parameters.size(); index++) {
                    if (Objects.equals(parameters.get(index).getName(), named.getName())) {
                        return index;
                    }
                }
            }
        }
        return argumentIndex;
    }
}

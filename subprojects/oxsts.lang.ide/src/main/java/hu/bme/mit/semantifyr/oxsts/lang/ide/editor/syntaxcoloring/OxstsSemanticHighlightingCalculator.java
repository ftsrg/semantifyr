/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.editor.syntaxcoloring;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinLibrary;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.xtext.ide.editor.syntaxcoloring.DefaultSemanticHighlightingCalculator;
import org.eclipse.xtext.ide.editor.syntaxcoloring.IHighlightedPositionAcceptor;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.service.OperationCanceledManager;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.Strings;

public class OxstsSemanticHighlightingCalculator extends DefaultSemanticHighlightingCalculator {

    private static final String SEGMENT_SEPARATOR = "::";

    @Inject
    private OperationCanceledManager operationCanceledManager;

    @Inject
    private BuiltinLibrary builtinLibrary;

    @Override
    protected boolean highlightElement(
            EObject object, IHighlightedPositionAcceptor acceptor, CancelIndicator cancelIndicator) {
        highlightElementName(object, acceptor, cancelIndicator);
        highlightElementCrossReferences(object, acceptor, cancelIndicator);
        return false;
    }

    protected void highlightElementName(
            EObject object, IHighlightedPositionAcceptor acceptor, CancelIndicator cancelIndicator) {
        if (!(object instanceof NamedElement)) {
            return;
        }
        operationCanceledManager.checkCanceled(cancelIndicator);
        var styles = getStyles(object, true);
        if (styles == null) {
            return;
        }
        for (var node : NodeModelUtils.findNodesForFeature(object, OxstsPackage.Literals.NAMED_ELEMENT__NAME)) {
            for (var segment : getSegments(node)) {
                highlightNode(acceptor, segment, styles);
            }
        }
    }

    protected void highlightElementCrossReferences(
            EObject object, IHighlightedPositionAcceptor acceptor, CancelIndicator cancelIndicator) {
        for (var reference : object.eClass().getEAllReferences()) {
            if (reference.isContainment() || reference.isContainer()) {
                continue;
            }
            operationCanceledManager.checkCanceled(cancelIndicator);
            var nodes = NodeModelUtils.findNodesForFeature(object, reference);
            var targets = getTargets(object, reference);
            var size = Math.min(nodes.size(), targets.size());
            for (var index = 0; index < size; index++) {
                highlightReference(acceptor, nodes.get(index), targets.get(index));
            }
        }
    }

    protected List<EObject> getTargets(EObject owner, EReference reference) {
        var value = owner.eGet(reference);
        if (value instanceof List<?> values) {
            return values.stream().map(EObject.class::cast).toList();
        }
        if (value instanceof EObject target) {
            return List.of(target);
        }
        return List.of();
    }

    // The written segments are a suffix of the target's qualified name, which follows its named containers.
    protected void highlightReference(IHighlightedPositionAcceptor acceptor, INode node, EObject target) {
        if (target.eIsProxy()) {
            return;
        }
        var segments = getSegments(node);
        var remaining = segments.size();
        var element = target;
        while (element != null && remaining > 0) {
            var consumed = getSegmentCount(element, remaining);
            var styles = getStyles(element, false);
            for (var index = remaining - consumed; index < remaining; index++) {
                if (styles != null) {
                    highlightNode(acceptor, segments.get(index), styles);
                }
            }
            remaining -= consumed;
            element = element.eContainer();
        }
    }

    protected int getSegmentCount(EObject element, int remaining) {
        if (element instanceof OxstsModelPackage) {
            return remaining;
        }
        if (Strings.isEmpty(NamingUtil.getName(element))) {
            return 0;
        }
        return 1;
    }

    protected List<ILeafNode> getSegments(INode node) {
        var segments = new ArrayList<ILeafNode>();
        for (var leaf : node.getLeafNodes()) {
            if (!leaf.isHidden() && !SEGMENT_SEPARATOR.equals(leaf.getText())) {
                segments.add(leaf);
            }
        }
        return segments;
    }

    protected String[] getStyles(EObject element, boolean declaration) {
        var tokenType = getTokenType(element);
        if (tokenType == null) {
            return null;
        }
        var styles = new ArrayList<String>();
        styles.add(tokenType);
        if (declaration) {
            styles.add(SemanticTokenModifiers.Declaration);
        }
        if (isAbstract(element)) {
            styles.add(SemanticTokenModifiers.Abstract);
        }
        if (isReadonly(element)) {
            styles.add(SemanticTokenModifiers.Readonly);
        }
        if (isBuiltin(element)) {
            styles.add(SemanticTokenModifiers.DefaultLibrary);
        }
        return styles.toArray(String[]::new);
    }

    protected String getTokenType(EObject element) {
        return switch (element) {
            case InlinedOxsts ignored -> SemanticTokenTypes.Namespace;
            case OxstsModelPackage ignored -> SemanticTokenTypes.Namespace;
            case DataTypeDeclaration ignored -> SemanticTokenTypes.Type;
            case EnumDeclaration ignored -> SemanticTokenTypes.Enum;
            case EnumLiteral ignored -> SemanticTokenTypes.EnumMember;
            case RecordDeclaration ignored -> SemanticTokenTypes.Struct;
            case ClassDeclaration ignored -> SemanticTokenTypes.Class;
            case AnnotationDeclaration ignored -> SemanticTokenTypes.Decorator;
            case VariableDeclaration ignored -> SemanticTokenTypes.Variable;
            case PropertyDeclaration ignored -> SemanticTokenTypes.Function;
            case TransitionDeclaration ignored -> SemanticTokenTypes.Method;
            case FeatureDeclaration ignored -> SemanticTokenTypes.Property;
            case ParameterDeclaration ignored -> SemanticTokenTypes.Parameter;
            default -> null;
        };
    }

    protected boolean isAbstract(EObject element) {
        return switch (element) {
            case ClassDeclaration classDeclaration -> classDeclaration.isAbstract();
            case PropertyDeclaration property -> property.isAbstract();
            case TransitionDeclaration transition -> transition.isAbstract();
            case FeatureDeclaration feature -> feature.getKind() == FeatureKind.FEATURE;
            default -> false;
        };
    }

    protected boolean isReadonly(EObject element) {
        if (element instanceof FeatureDeclaration feature) {
            return feature.getKind() == FeatureKind.REFERENCE || feature.getKind() == FeatureKind.DERIVED;
        }
        return false;
    }

    protected boolean isBuiltin(EObject element) {
        var resource = element.eResource();
        return resource != null && builtinLibrary.getBuiltinResourceUri().equals(resource.getURI());
    }
}

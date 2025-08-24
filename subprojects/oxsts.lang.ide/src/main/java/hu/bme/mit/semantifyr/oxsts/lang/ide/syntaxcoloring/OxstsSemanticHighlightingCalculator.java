/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.syntaxcoloring;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Package;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.ide.editor.syntaxcoloring.DefaultSemanticHighlightingCalculator;
import org.eclipse.xtext.ide.editor.syntaxcoloring.IHighlightedPositionAcceptor;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.service.OperationCanceledManager;
import org.eclipse.xtext.util.CancelIndicator;

public class OxstsSemanticHighlightingCalculator extends DefaultSemanticHighlightingCalculator {

	@Inject
	private OperationCanceledManager operationCanceledManager;

	@Override
	protected boolean highlightElement(EObject object, IHighlightedPositionAcceptor acceptor, CancelIndicator cancelIndicator) {
		highlightElementName(object, acceptor, cancelIndicator);
		highlightElementCrossReferences(object, acceptor, cancelIndicator);
		return false;
	}

	protected void highlightElementName(EObject object, IHighlightedPositionAcceptor acceptor, CancelIndicator cancelIndicator) {
		if (!(object instanceof NamedElement)) {
			return;
		}
        operationCanceledManager.checkCanceled(cancelIndicator);
		var highlightClass = getHighlightClass(object, null);
		if (highlightClass != null) {
			highlightFeature(acceptor, object, OxstsPackage.Literals.NAMED_ELEMENT__NAME, highlightClass);
		}
	}

	protected void highlightElementCrossReferences(EObject object, IHighlightedPositionAcceptor acceptor, CancelIndicator cancelIndicator) {
		for (var reference : object.eClass().getEAllReferences()) {
			if (reference.isContainment()) {
				continue;
			}
			operationCanceledManager.checkCanceled(cancelIndicator);
			if (reference.isMany()) {
				highlightManyValues(object, reference, acceptor);
			} else {
				highlightSingleValue(object, reference, acceptor);
			}
		}
	}

	protected void highlightSingleValue(EObject owner, EReference reference, IHighlightedPositionAcceptor acceptor) {
		var value = (EObject) owner.eGet(reference);
		var highlightClass = getHighlightClass(value, reference);
		if (highlightClass != null) {
			highlightFeature(acceptor, owner, reference, highlightClass);
		}
	}

	protected void highlightManyValues(EObject owner, EReference reference, IHighlightedPositionAcceptor acceptor) {
		@SuppressWarnings("unchecked")
		var values = (EList<? extends EObject>) owner.eGet(reference);
		var nodes = NodeModelUtils.findNodesForFeature(owner, reference);
		int size = Math.min(values.size(), nodes.size());
		for (var i = 0; i < size; i++) {
			var valueInList = values.get(i);
			var node = nodes.get(i);
			var highlightClass = getHighlightClass(valueInList, reference);
			if (highlightClass != null) {
				highlightNode(acceptor, node, highlightClass);
			}
		}
	}

    // TODO: add modifiers: e.g., 'abstract' for abstract classes, 'defaultLibrary' for builtins, etc
	protected String getHighlightClass(EObject eObject, EReference reference) {
        if (eObject == null) return null;
        return switch (eObject) {
            case Package ignored -> "namespace";
            case DataTypeDeclaration ignored -> "type";
            case EnumDeclaration ignored -> "enum";
            case EnumLiteral ignored -> "enumMember";
            case RecordDeclaration ignored -> "struct";
            case ClassDeclaration ignored -> "class";
            case AnnotationDeclaration ignored -> "decorator";
            case VariableDeclaration ignored -> "variable";
            case PropertyDeclaration ignored -> "function";
            case TransitionDeclaration ignored -> "method";
            case FeatureDeclaration ignored -> "property";
            case Parameter ignored -> "parameter";

            case LiteralString ignored -> "string";
            case LiteralInfinity ignored -> "number";
            case LiteralInteger ignored -> "number";
            case LiteralReal ignored -> "number";

            default -> null;
        };
	}

}

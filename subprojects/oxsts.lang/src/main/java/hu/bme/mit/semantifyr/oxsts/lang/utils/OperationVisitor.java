/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

public abstract class OperationVisitor<T> {

    protected T visit(Operation operation) {
        return switch (operation) {
            case SequenceOperation sequenceOperation -> visit(sequenceOperation);
            case ChoiceOperation choiceOperation -> visit(choiceOperation);
            case LocalVarDeclarationOperation localVarDeclarationOperation -> visit(localVarDeclarationOperation);
            case ForOperation forOperation -> visit(forOperation);
            case IfOperation ifOperation -> visit(ifOperation);
            case HavocOperation havocOperation -> visit(havocOperation);
            case AssumptionOperation assumptionOperation -> visit(assumptionOperation);
            case AssignmentOperation assignmentOperation -> visit(assignmentOperation);
            case InlineOperation inlineOperation -> visit(inlineOperation);
            default -> throw new IllegalStateException("Unexpected value: " + operation);
        };
    }

    protected abstract T visit(SequenceOperation operation);
    protected abstract T visit(ChoiceOperation operation);
    protected abstract T visit(LocalVarDeclarationOperation operation);
    protected abstract T visit(ForOperation operation);
    protected abstract T visit(IfOperation operation);
    protected abstract T visit(HavocOperation operation);
    protected abstract T visit(AssumptionOperation operation);
    protected abstract T visit(AssignmentOperation operation);

    protected T visit(InlineOperation operation) {
        return switch (operation) {
            case InlineCall inlineCall -> visit(inlineCall);
            case InlineIfOperation inlineIfOperation -> visit(inlineIfOperation);
            case InlineForOperation inlineForOperation -> visit(inlineForOperation);
            default -> throw new IllegalStateException("Unexpected value: " + operation);
        };
    }

    protected abstract T visit(InlineCall operation);
    protected abstract T visit(InlineIfOperation operation);

    protected T visit(InlineForOperation operation) {
        return switch (operation) {
            case InlineSeqFor inlineSeqFor -> visit(inlineSeqFor);
            case InlineChoiceFor inlineChoiceFor -> visit(inlineChoiceFor);
            default -> throw new IllegalStateException("Unexpected value: " + operation);
        };
    }

    protected abstract T visit(InlineSeqFor operation);
    protected abstract T visit(InlineChoiceFor operation);

}

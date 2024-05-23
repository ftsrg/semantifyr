/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package hu.bme.mit.semantifyr.oxsts.lang.formatting2;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Type;
import org.eclipse.xtext.formatting2.AbstractJavaFormatter;
import org.eclipse.xtext.formatting2.IFormattableDocument;

public class OxstsFormatter extends AbstractJavaFormatter {

    protected void format(hu.bme.mit.semantifyr.oxsts.model.oxsts.Package _package, IFormattableDocument doc) {
        // TODO: format HiddenRegions around keywords, attributes, cross references, etc.
        for (Type type : _package.getTypes()) {
            doc.format(type);
        }
        for (hu.bme.mit.semantifyr.oxsts.model.oxsts.Enum _enum : _package.getEnums()) {
            doc.format(_enum);
        }
    }

    protected void format(hu.bme.mit.semantifyr.oxsts.model.oxsts.Enum _enum, IFormattableDocument doc) {
        // TODO: format HiddenRegions around keywords, attributes, cross references, etc.
        for (EnumLiteral enumLiteral : _enum.getLiterals()) {
            doc.format(enumLiteral);
        }
    }

    // TODO: implement for Type, Transition, SequenceOperation, HavocOperation, ChoiceOperation, AssumptionOperation, IfOperation, InlineChoice, InlineSeq, InlineCall, InlineIfOperation, AssignmentOperation, Variable, Feature, Instance, InstanceBinding, OrOperator, AndOperator, PlusOperator, MinusOperator, EqualityOperator, InequalityOperator, NotOperator, ChainReferenceExpression
}

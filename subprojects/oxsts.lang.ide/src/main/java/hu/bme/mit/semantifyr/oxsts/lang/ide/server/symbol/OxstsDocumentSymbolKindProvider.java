/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.symbol;

import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AnnotationDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.xtext.ide.server.symbol.DocumentSymbolMapper;

@Singleton
public class OxstsDocumentSymbolKindProvider extends DocumentSymbolMapper.DocumentSymbolKindProvider {

    // A null kind leaves the element out of the outline: parameters, local and loop variables.
    @Override
    public SymbolKind getSymbolKind(EObject object) {
        return switch (object) {
            case OxstsModelPackage ignored -> SymbolKind.Package;
            case ClassDeclaration ignored -> SymbolKind.Class;
            case EnumDeclaration ignored -> SymbolKind.Enum;
            case EnumLiteral ignored -> SymbolKind.EnumMember;
            case RecordDeclaration ignored -> SymbolKind.Struct;
            case DataTypeDeclaration ignored -> SymbolKind.TypeParameter;
            case AnnotationDeclaration ignored -> SymbolKind.Interface;
            case FeatureDeclaration ignored -> SymbolKind.Property;
            case TransitionDeclaration ignored -> SymbolKind.Method;
            case PropertyDeclaration ignored -> SymbolKind.Function;
            case VariableDeclaration variable -> getVariableKind(variable);
            case null, default -> null;
        };
    }

    private SymbolKind getVariableKind(VariableDeclaration variable) {
        return switch (variable.eContainer()) {
            case RecordDeclaration ignored -> SymbolKind.Field;
            case ClassDeclaration ignored -> SymbolKind.Variable;
            case InlinedOxsts ignored -> SymbolKind.Variable;
            case null, default -> null;
        };
    }
}

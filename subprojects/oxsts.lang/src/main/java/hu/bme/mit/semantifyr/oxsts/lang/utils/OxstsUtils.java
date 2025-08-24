/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration;

import java.util.List;

public class OxstsUtils {
    public static final String LIBRARY_EXTENSION = "oxsts";

    private OxstsUtils() {
        throw new IllegalStateException("This is a static utility class and should not be instantiated directly");
    }

    public static Iterable<Declaration> getDirectMembers(Declaration declaration) {
        return switch (declaration) {
            case RecordDeclaration decl -> getDirectMembers(decl);
            case ClassDeclaration decl -> getDirectMembers(decl);
            case FeatureDeclaration decl -> getDirectMembers(decl);
            default -> List.of();
        };
    }

    public static Iterable<Declaration> getDirectMembers(RecordDeclaration declaration) {
        return declaration.getMembers();
    }

    public static Iterable<Declaration> getDirectMembers(ClassDeclaration declaration) {
        return declaration.getMembers();
    }

    public static Iterable<Declaration> getDirectMembers(FeatureDeclaration declaration) {
        return declaration.getInnerFeatures().stream().map(f -> (Declaration) f).toList();
    }

}

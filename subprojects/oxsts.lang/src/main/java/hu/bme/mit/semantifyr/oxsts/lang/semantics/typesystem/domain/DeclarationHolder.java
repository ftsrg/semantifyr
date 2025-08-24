/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.domain;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration;

public class DeclarationHolder {
    protected Declaration declaration;
    
    public DeclarationHolder(Declaration declaration) {
        this.declaration = declaration;
    }
    
    public Declaration getDeclaration() {
        return this.declaration;
    }
    
    public void setDeclaration(Declaration declaration) {
        this.declaration = declaration;
    }
}

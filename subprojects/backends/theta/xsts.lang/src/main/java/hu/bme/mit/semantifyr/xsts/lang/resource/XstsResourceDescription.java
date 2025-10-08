/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.xsts.lang.resource;

import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel;
import org.apache.log4j.Logger;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.IDefaultResourceDescriptionStrategy;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.impl.DefaultResourceDescription;
import org.eclipse.xtext.util.IAcceptor;
import org.eclipse.xtext.util.IResourceScopeCache;

import java.io.IOException;
import java.util.*;

import static com.google.common.collect.Lists.newArrayList;

public class XstsResourceDescription extends DefaultResourceDescription {

    private final static Logger log = Logger.getLogger(XstsResourceDescription.class);

    private final IDefaultResourceDescriptionStrategy strategy;

    public XstsResourceDescription(Resource resource, IDefaultResourceDescriptionStrategy strategy, IResourceScopeCache cache) {
        super(resource, strategy, cache);
        this.strategy = strategy;
    }

    @Override
    protected List<IEObjectDescription> computeExportedObjects() {
        if (!getResource().isLoaded()) {
            try {
                getResource().load(null);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                return Collections.emptyList();
            }
        }

        if (getResource().getContents().isEmpty()) {
            return List.of();
        }

        var rootElement = getResource().getContents().getFirst();
        if (! (rootElement instanceof XstsModel xstsModel)) {
            // delegate to super, we do not know what this resource is...
            return super.computeExportedObjects();
        }

        final List<IEObjectDescription> exportedEObjects = newArrayList();
        IAcceptor<IEObjectDescription> acceptor = exportedEObjects::add;

        for (var enumDeclaration : xstsModel.getEnumDeclarations()) {
            strategy.createEObjectDescriptions(enumDeclaration, acceptor);
            for (var enumLiteral : enumDeclaration.getLiterals()) {
                strategy.createEObjectDescriptions(enumLiteral, acceptor);
            }
        }
        for (var variableDeclaration : xstsModel.getVariableDeclarations()) {
            strategy.createEObjectDescriptions(variableDeclaration, acceptor);
        }

        return exportedEObjects;
    }

}

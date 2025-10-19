/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.resource;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.apache.log4j.Logger;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.IDefaultResourceDescriptionStrategy;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.impl.DefaultResourceDescription;
import org.eclipse.xtext.util.IAcceptor;
import org.eclipse.xtext.util.IResourceScopeCache;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class OxstsResourceDescription extends DefaultResourceDescription {

    private final static Logger log = Logger.getLogger(OxstsResourceDescription.class);

    private final IDefaultResourceDescriptionStrategy strategy;

    public OxstsResourceDescription(Resource resource, IDefaultResourceDescriptionStrategy strategy, IResourceScopeCache cache) {
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
        if (rootElement instanceof InlinedOxsts) {
            return List.of();
        }

        if (rootElement instanceof OxstsModelPackage oxstsModelPackage) {
            return computeExportedObjects(oxstsModelPackage);
        }

        return super.computeExportedObjects();
    }

    protected List<IEObjectDescription> computeExportedObjects(OxstsModelPackage oxstsModelPackage) {
        final List<IEObjectDescription> exportedEObjects = newArrayList();
        IAcceptor<IEObjectDescription> acceptor = exportedEObjects::add;

        strategy.createEObjectDescriptions(oxstsModelPackage, acceptor);

        // only the top-level declarations should be exported
        for (var declaration : oxstsModelPackage.getDeclarations()) {
            computeExportedObjects(declaration, acceptor);
        }

        return exportedEObjects;
    }

    protected void computeExportedObjects(Declaration declaration, IAcceptor<IEObjectDescription> acceptor) {
        strategy.createEObjectDescriptions(declaration, acceptor);

        if (declaration instanceof EnumDeclaration enumDeclaration) {
            for (var literal : enumDeclaration.getLiterals()) {
                strategy.createEObjectDescriptions(literal, acceptor);
            }
        }
    }

}

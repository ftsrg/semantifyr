/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.validation;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.resource.ILocationInFileProvider;
import org.eclipse.xtext.validation.Check;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * This class contains custom validation rules.
 * <p>
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
@SuppressWarnings("unused") // check functions are used by reflection
public class OxstsValidator extends AbstractOxstsValidator {

    @Inject
    private ILocationInFileProvider locationInFileProvider;

    @Inject
    private RedefinitionHandler redefinitionHandler;

    private static final String ISSUE_PREFIX = "hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator.";
    public static final String DUPLICATE_NAME_ISSUE = ISSUE_PREFIX + "DUPLICATE_NAME";
    public static final String DATA_TYPE_NOT_IN_BUILTIN_ISSUE = ISSUE_PREFIX + "DATA_TYPE_NOT_IN_BUILTIN";
    public static final String REDEFINED_NOT_FOUND_ISSUE = ISSUE_PREFIX + "REDEFINED_NOT_FOUND";

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    @Override
    protected void handleExceptionDuringValidation(Throwable targetException) throws RuntimeException {
        // swallow all exceptions!
    }

    @Check
    public void checkNoRedefinedDeclarations(RedefinableDeclaration redefinableDeclaration) {
        if (redefinableDeclaration.isRedefine()) {
            try {
                var redefined = redefinitionHandler.getRedefinedDeclaration(redefinableDeclaration);
                if (redefined == null) {
                    acceptError("Could not find redefined declaration named " + NamingUtil.getName(redefinableDeclaration), redefinableDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, REDEFINED_NOT_FOUND_ISSUE);
                }
            } catch (Exception e) {
                acceptError("Could not find redefined declaration named " + NamingUtil.getName(redefinableDeclaration), redefinableDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, REDEFINED_NOT_FOUND_ISSUE);
            }
        }
    }

    @Check
    public void checkDataTypeOnlyInBuiltin(DataTypeDeclaration dataTypeDeclaration) {
        if (! builtinSymbolResolver.isBuiltin(dataTypeDeclaration)) {
            var message = "Custom data types are not allowed!";
            acceptError(message, dataTypeDeclaration, DATA_TYPE_NOT_IN_BUILTIN_ISSUE);
        }
    }

    @Check
    public void checkUniqueDeclarations(OxstsModelPackage oxstsPackage) {
        checkUniqueSimpleNames(oxstsPackage.getDeclarations());
    }

    @Check
    public void checkUniqueEnumLiterals(EnumDeclaration enumDeclaration) {
        checkUniqueSimpleNames(enumDeclaration.getLiterals());
    }

    @Check
    public void checkUniqueMembers(RecordDeclaration recordDeclaration) {
        checkUniqueSimpleNames(recordDeclaration.getMembers());
    }

    @Check
    public void checkUniqueMembers(ClassDeclaration classDeclaration) {
        checkUniqueSimpleNames(classDeclaration.getMembers());
    }

    @Check
    public void checkUniqueMembers(FeatureDeclaration featureDeclaration) {
        checkUniqueSimpleNames(featureDeclaration.getInnerFeatures());
    }

    protected void checkUniqueSimpleNames(Iterable<? extends NamedElement> namedElements) {
        var names = new LinkedHashMap<String, Set<NamedElement>>();
        for (var namedElement : namedElements) {
            var name = NamingUtil.getName(namedElement);
            var elementsWithName = names.computeIfAbsent(name, ignored -> new LinkedHashSet<>());
            elementsWithName.add(namedElement);
        }
        for (var entry : names.entrySet()) {
            var elementsWithName = entry.getValue();
            if (elementsWithName.size() <= 1) {
                continue;
            }
            var name = entry.getKey();
            var message = "Duplicate name '%s'.".formatted(name);
            for (var namedElement : elementsWithName) {
                acceptError(message, namedElement, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, DUPLICATE_NAME_ISSUE);
            }
        }
    }

    protected void acceptError(String message, EObject object, String code, String... issueData) {
        var region = locationInFileProvider.getFullTextRegion(object);
        acceptError(message, object, region.getOffset(), region.getLength(), code, issueData);
    }

}

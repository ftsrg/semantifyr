/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.validation;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.OppositeHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.resource.ILocationInFileProvider;
import org.eclipse.xtext.validation.Check;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
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

    @Inject
    private OppositeHandler oppositeHandler;

    @Inject
    private DomainMemberCollectionProvider domainMemberCollectionProvider;

    @Inject
    private MetaConstantExpressionEvaluatorProvider metaConstantExpressionEvaluatorProvider;

    private static final String ISSUE_PREFIX = "OXSTS.";
    public static final String DUPLICATE_NAME_ISSUE = ISSUE_PREFIX + "DUPLICATE_NAME";
    public static final String DATA_TYPE_NOT_IN_BUILTIN_ISSUE = ISSUE_PREFIX + "DATA_TYPE_NOT_IN_BUILTIN";
    public static final String REDEFINED_NOT_FOUND_ISSUE = ISSUE_PREFIX + "REDEFINED_NOT_FOUND";
    public static final String ELEMENT_SHADOWED = ISSUE_PREFIX + "ELEMENT_SHADOWED";
    public static final String INVALID_CALL_ARGUMENTS_COUND = ISSUE_PREFIX + "INVALID_CALL_ARGUMENTS_COUNT";
    public static final String INCORRECT_OPPOSITE = ISSUE_PREFIX + "INCORRECT_OPPOSITE";

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
                    acceptError("Could not find redefined declaration.", redefinableDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, REDEFINED_NOT_FOUND_ISSUE);
                }
            } catch (Exception e) {
                acceptError("Could not find redefined declaration.", redefinableDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, REDEFINED_NOT_FOUND_ISSUE);
            }
        }
    }

    @Check
    public void checkShadowedNames(NamedElement namedElement) {
        if (namedElement instanceof RedefinableDeclaration redefinableDeclaration && redefinableDeclaration.isRedefine()) {
            return;
        }

        var parentDomain = EcoreUtil2.getContainerOfType(namedElement.eContainer(), DomainDeclaration.class);
        if (parentDomain == null) {
            return;
        }
        var declarations = domainMemberCollectionProvider.getParentCollection(parentDomain).getDeclarations();

        if (declarations.stream().anyMatch(d -> Objects.equals(NamingUtil.getName(d), namedElement.getName()))) {
            acceptWarning("Name shadows another element.", namedElement, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, ELEMENT_SHADOWED);
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
    public void checkOpposite(FeatureDeclaration featureDeclaration) {
        var opposite = featureDeclaration.getOpposite();

        if (opposite != null) {
            var oppositeOpposite = opposite.getOpposite();

            if (oppositeOpposite == null) {
                var message = String.format("Expected feature to have opposite '%s'.", NamingUtil.getName(featureDeclaration));
                acceptError(message, opposite, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, INCORRECT_OPPOSITE);
            } else if (oppositeOpposite != featureDeclaration) {
                var oppositeMessage = String.format("Expected feature to have opposite '%s', got '%s' instead.", NamingUtil.getName(featureDeclaration), NamingUtil.getName(oppositeOpposite));
                acceptError(oppositeMessage, opposite, OxstsPackage.Literals.FEATURE_DECLARATION__OPPOSITE, 0, INCORRECT_OPPOSITE);
            } else {
                if (featureDeclaration.getKind() == FeatureKind.CONTAINER && opposite.getKind() != FeatureKind.CONTAINMENT) {
                    acceptError("Container's opposite must be containment.", featureDeclaration, OxstsPackage.Literals.FEATURE_DECLARATION__OPPOSITE, 0, INCORRECT_OPPOSITE);
                } else if (featureDeclaration.getKind() == FeatureKind.CONTAINMENT && opposite.getKind() != FeatureKind.CONTAINER) {
                    acceptError("Containment's opposite must be container.", featureDeclaration, OxstsPackage.Literals.FEATURE_DECLARATION__OPPOSITE, 0, INCORRECT_OPPOSITE);
                }
            }
        } else if (featureDeclaration.getKind() == FeatureKind.CONTAINER) {
            acceptError("Container feature must have an opposite.", featureDeclaration, INCORRECT_OPPOSITE);
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

    @Check
    public void checkCallExpressionArgumentCount(CallSuffixExpression callSuffixExpression) {
        var primary = metaConstantExpressionEvaluatorProvider.evaluate(callSuffixExpression.getPrimary());

        if (! (primary instanceof ParametricDeclaration parametricDeclaration)) {
            return;
        }

        if (parametricDeclaration.getParameters().size() != callSuffixExpression.getArguments().size()) {
            acceptError("Expected " + parametricDeclaration.getParameters().size() + " arguments, found " + callSuffixExpression.getArguments().size(), callSuffixExpression, INVALID_CALL_ARGUMENTS_COUND);
        }
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

/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.validation;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.resource.ResourceDescriptionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.imports.ImportCollector;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.imports.PackageSelectableProvider;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.selectables.TrimPrefixSelectable;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.FeatureSubSettersFinder;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.OppositeHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.PropertyTypeHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.ExpressionModalityEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.Modality;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ImmutableTypeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.InvalidTypeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.NothingTypeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.TypeCompatibility;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.TypeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.linking.impl.LinkingHelper;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.ILocationInFileProvider;
import org.eclipse.xtext.resource.ISelectable;
import org.eclipse.xtext.scoping.IGlobalScopeProvider;
import org.eclipse.xtext.scoping.impl.ScopeBasedSelectable;
import org.eclipse.xtext.util.Strings;
import org.eclipse.xtext.validation.Check;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains custom validation rules.
 *
 * <p>See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
@SuppressWarnings("unused") // check functions are used by reflection
public class OxstsValidator extends AbstractOxstsValidator {

    private static final Logger logger = LoggerFactory.getLogger(OxstsValidator.class);

    @Inject
    private ILocationInFileProvider locationInFileProvider;

    @Inject
    private RedefinitionHandler redefinitionHandler;

    @Inject
    private OppositeHandler oppositeHandler;

    @Inject
    private FeatureSubSettersFinder featureSubSettersFinder;

    @Inject
    private DomainMemberCollectionProvider domainMemberCollectionProvider;

    @Inject
    private MetaConstantExpressionEvaluatorProvider metaConstantExpressionEvaluatorProvider;

    @Inject
    private ExpressionModalityEvaluatorProvider modalityEvaluatorProvider;

    @Inject
    private ConstantExpressionEvaluatorProvider constantExpressionEvaluatorProvider;

    @Inject
    private ExpressionTypeEvaluatorProvider typeEvaluatorProvider;

    @Inject
    private TypeCompatibility typeCompatibility;

    @Inject
    private PropertyTypeHandler propertyTypeHandler;

    @Inject
    private MultiplicityRangeEvaluator multiplicityRangeEvaluator;

    @Inject
    private BuiltinAnnotationHandler builtinAnnotationHandler;

    @Inject
    private IGlobalScopeProvider globalScopeProvider;

    @Inject
    private ImportCollector importCollector;

    @Inject
    private PackageSelectableProvider packageSelectableProvider;

    @Inject
    private LinkingHelper linkingHelper;

    @Inject
    private IQualifiedNameConverter qualifiedNameConverter;

    @Inject
    private IQualifiedNameProvider qualifiedNameProvider;

    @Inject
    private ResourceDescriptionProvider resourceDescriptionProvider;

    private static final String ISSUE_PREFIX = "OXSTS.";
    public static final String DUPLICATE_NAME_ISSUE = ISSUE_PREFIX + "DUPLICATE_NAME";
    public static final String DATA_TYPE_NOT_IN_BUILTIN_ISSUE = ISSUE_PREFIX + "DATA_TYPE_NOT_IN_BUILTIN";
    public static final String REDEFINED_NOT_FOUND_ISSUE = ISSUE_PREFIX + "REDEFINED_NOT_FOUND";
    public static final String DUPLICATE_REDEFINITION = ISSUE_PREFIX + "DUPLICATE_REDEFINITION";
    public static final String MEMBER_NOT_IN_ABSTRACT_CLASS = ISSUE_PREFIX + "MEMBER_NOT_IN_ABSTRACT_CLASS";
    public static final String ELEMENT_SHADOWED = ISSUE_PREFIX + "ELEMENT_SHADOWED";
    public static final String INVALID_CALL_ARGUMENTS_COUND = ISSUE_PREFIX + "INVALID_CALL_ARGUMENTS_COUNT";
    public static final String PARAMETER_BOUND_TWICE = ISSUE_PREFIX + "PARAMETER_BOUND_TWICE";
    public static final String POSITIONAL_ARGUMENT_AFTER_NAMED = ISSUE_PREFIX + "POSITIONAL_ARGUMENT_AFTER_NAMED";
    public static final String INCORRECT_OPPOSITE = ISSUE_PREFIX + "INCORRECT_OPPOSITE";
    public static final String INCORRECT_SUBSETS = ISSUE_PREFIX + "INCORRECT_SUBSETS";
    public static final String ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED =
            ISSUE_PREFIX + "ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED";
    public static final String ABSTRACT_FEATURE_INHERITED_MEMBERS_NOT_REDEFINED =
            ISSUE_PREFIX + "ABSTRACT_FEATURE_INHERITED_MEMBERS_NOT_REDEFINED";
    public static final String ABSTRACT_FEATURE_NOT_REALIZED = ISSUE_PREFIX + "ABSTRACT_FEATURE_NOT_REALIZED";
    public static final String INCORRECT_ASSIGNMENT = ISSUE_PREFIX + "INCORRECT_ASSIGNMENT";
    public static final String INCORRECT_CALLED_ELEMENT = ISSUE_PREFIX + "INCORRECT_CALLED_ELEMENT";
    public static final String CALL_THROUGH_MANY_VALUED_FEATURE = ISSUE_PREFIX + "CALL_THROUGH_MANY_VALUED_FEATURE";
    public static final String SELF_OUTSIDE_CLASS = ISSUE_PREFIX + "SELF_OUTSIDE_CLASS";
    public static final String VARIABLE_WITH_IMPLICIT_TYPE = ISSUE_PREFIX + "VARIABLE_WITH_IMPLICIT_TYPE";
    public static final String UNSUPPORTED_FEATURE = ISSUE_PREFIX + "UNSUPPORTED_FEATURE";
    public static final String EXPRESSION_MODALITY_TOO_HIGH = ISSUE_PREFIX + "EXPRESSION_MODALITY_TOO_HIGH";
    public static final String COULD_BE_INLINE = ISSUE_PREFIX + "COULD_BE_INLINE";
    public static final String CONSTANT_EXPRESSION = ISSUE_PREFIX + "CONSTANT_EXPRESSION";
    public static final String TYPE_MISMATCH = ISSUE_PREFIX + "TYPE_MISMATCH";
    public static final String INVALID_CAST = ISSUE_PREFIX + "INVALID_CAST";
    public static final String MULTIPLE_INHERITANCE = ISSUE_PREFIX + "MULTIPLE_INHERITANCE";
    public static final String REDEFINITION_SIGNATURE_MISMATCH = ISSUE_PREFIX + "REDEFINITION_SIGNATURE_MISMATCH";
    public static final String RECURSIVE_RECORD = ISSUE_PREFIX + "RECURSIVE_RECORD";
    public static final String MIXED_BOOLEAN_OPERATORS = ISSUE_PREFIX + "MIXED_BOOLEAN_OPERATORS";
    public static final String MISPLACED_TEMPORAL_OPERATOR = ISSUE_PREFIX + "MISPLACED_TEMPORAL_OPERATOR";
    public static final String MISSING_INITIALIZER = ISSUE_PREFIX + "MISSING_INITIALIZER";
    public static final String ABSTRACT_VERIFICATION_CASE = ISSUE_PREFIX + "ABSTRACT_VERIFICATION_CASE";
    public static final String MISSING_PROPERTY = ISSUE_PREFIX + "MISSING_PROPERTY";
    public static final String INCORRECT_ANNOTATION_TARGET = ISSUE_PREFIX + "INCORRECT_ANNOTATION_TARGET";
    public static final String AMBIGUOUS_REFERENCE = ISSUE_PREFIX + "AMBIGUOUS_REFERENCE";
    public static final String DECLARED_IN_ANOTHER_FILE = ISSUE_PREFIX + "DECLARED_IN_ANOTHER_FILE";

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    @Override
    protected void handleExceptionDuringValidation(Throwable targetException) throws RuntimeException {
        // Swallow all exceptions!
        logger.error("Exception during validation", targetException);
    }

    //    @Check
    //    public void featuresMustBeInAbstractClass(FeatureDeclaration featureDeclaration) {
    //        if (featureDeclaration.getKind() == FeatureKind.FEATURE &&
    // featureDeclaration.eContainer() instanceof ClassDeclaration containerClass && !
    // containerClass.isAbstract()) {
    //            acceptError("Abstract features can only be declared in abstract classes.",
    // featureDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0,
    // MEMBER_NOT_IN_ABSTRACT_CLASS);
    //        }
    //    }
    //
    //    @Check
    //    public void featuresMustBeInAbstractFeatures(FeatureDeclaration featureDeclaration) {
    //        if (featureDeclaration.getKind() == FeatureKind.FEATURE &&
    // featureDeclaration.eContainer() instanceof FeatureDeclaration containerFeature &&
    // featureDeclaration.getKind() != FeatureKind.FEATURE) {
    //            acceptError("Abstract features can only be declared in abstract features.",
    // featureDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0,
    // MEMBER_NOT_IN_ABSTRACT_CLASS);
    //        }
    //    }

    @Check
    public void abstractTransitionMustBeInAbstractClass(TransitionDeclaration transitionDeclaration) {
        var containerClass = EcoreUtil2.getContainerOfType(transitionDeclaration, ClassDeclaration.class);
        if (transitionDeclaration.isAbstract() && containerClass != null && !containerClass.isAbstract()) {
            acceptError(
                    "Abstract transitions can only be declared in abstract classes.",
                    transitionDeclaration,
                    OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                    0,
                    MEMBER_NOT_IN_ABSTRACT_CLASS);
        }
    }

    @Check
    public void abstractPropertyMustBeInAbstractClass(PropertyDeclaration propertyDeclaration) {
        var containerClass = EcoreUtil2.getContainerOfType(propertyDeclaration, ClassDeclaration.class);
        if (propertyDeclaration.isAbstract() && containerClass != null && !containerClass.isAbstract()) {
            acceptError(
                    "Abstract properties can only be declared in abstract classes.",
                    propertyDeclaration,
                    OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                    0,
                    MEMBER_NOT_IN_ABSTRACT_CLASS);
        }
    }

    @Check
    public void allInheritedAbstractMembersMustBeRealizedInNonAbstractClass(ClassDeclaration classDeclaration) {
        if (!classDeclaration.isAbstract()) {
            var memberCollection = domainMemberCollectionProvider.getMemberCollection(classDeclaration);
            var abstractInheritedMembers = memberCollection.getDeclarations().stream()
                    .filter(OxstsUtils::isDeclarationAbstract)
                    .filter(m -> m.eContainer() != classDeclaration)
                    .toList();

            if (!abstractInheritedMembers.isEmpty()) {
                var memberNames = abstractInheritedMembers.stream()
                        .map(NamingUtil::getName)
                        .toList();
                var message = "Class '"
                        + NamingUtil.getName(classDeclaration)
                        + "' must either be declared abstract or redefine abstract members: "
                        + memberNames.stream().map(name -> "'" + name + "'").collect(Collectors.joining(", "));

                acceptError(
                        message,
                        classDeclaration,
                        OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                        0,
                        ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED,
                        memberNames.toArray(String[]::new));
            }
        }
    }

    // the compiler evaluates a `features` slot through its subsetters, and fails when there are none
    @Check
    public void checkAbstractFeaturesRealized(ClassDeclaration classDeclaration) {
        if (classDeclaration.isAbstract()) {
            return;
        }
        var unrealizedNames = new ArrayList<String>();
        for (var member : domainMemberCollectionProvider
                .getMemberCollection(classDeclaration)
                .getDeclarations()) {
            if (member instanceof FeatureDeclaration feature
                    && feature.getKind() == FeatureKind.FEATURE
                    && featureSubSettersFinder
                            .getSubSetters(classDeclaration, feature)
                            .isEmpty()) {
                unrealizedNames.add("'" + feature.getName() + "'");
            }
        }
        if (!unrealizedNames.isEmpty()) {
            acceptError(
                    "Class '"
                            + NamingUtil.getName(classDeclaration)
                            + "' must either be declared abstract or realize the abstract features "
                            + String.join(", ", unrealizedNames)
                            + ", redefining each as `contains` or `refers` or subsetting it.",
                    classDeclaration,
                    OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                    0,
                    ABSTRACT_FEATURE_NOT_REALIZED);
        }
    }

    @Check
    public void checkVerificationCaseNotAbstract(ClassDeclaration classDeclaration) {
        if (classDeclaration.isAbstract() && builtinAnnotationHandler.isVerificationCase(classDeclaration)) {
            acceptError(
                    "Abstract class '"
                            + NamingUtil.getName(classDeclaration)
                            + "' cannot be a verification case, which is instantiated as the root.",
                    classDeclaration,
                    OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                    0,
                    ABSTRACT_VERIFICATION_CASE);
        }
    }

    @Check
    public void checkVerificationCaseHasProperty(ClassDeclaration classDeclaration) {
        if (classDeclaration.isAbstract() || !builtinAnnotationHandler.isVerificationCase(classDeclaration)) {
            return;
        }
        for (var member : domainMemberCollectionProvider
                .getMemberCollection(classDeclaration)
                .getDeclarations()) {
            if (member instanceof PropertyDeclaration && "prop".equals(NamingUtil.getName(member))) {
                return;
            }
        }
        acceptError(
                "Verification case '"
                        + NamingUtil.getName(classDeclaration)
                        + "' has no property to check; declare or inherit one, such as `prop { return AG ... }`.",
                classDeclaration,
                OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                0,
                MISSING_PROPERTY);
    }

    //    @Check
    //    public void
    // allInheritedAbstractMembersMustBeRealizedInNonAbstractFeature(FeatureDeclaration
    // featureDeclaration) {
    //        if (featureDeclaration.getKind() != FeatureKind.FEATURE) {
    //            var memberCollection =
    // domainMemberCollectionProvider.getMemberCollection(featureDeclaration);
    //            var abstractInheritedMembers = memberCollection.getDeclarations().stream()
    //                    .filter(OxstsUtils::isDeclarationAbstract)
    //                    .filter(m -> m.eContainer() != featureDeclaration)
    //                    .toList();
    //
    //            if (! abstractInheritedMembers.isEmpty()) {
    //                var message = "Feature '" + NamingUtil.getName(featureDeclaration) +
    //                        "' must either be declared abstract or redefine abstract members: " +
    //                        abstractInheritedMembers.stream().map(m -> "'" + NamingUtil.getName(m)
    // + "'").collect(Collectors.joining(", "));
    //
    //                acceptError(message, featureDeclaration,
    // OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0,
    // ABSTRACT_FEATURE_INHERITED_MEMBERS_NOT_REDEFINED);
    //            }
    //        }
    //    }

    @Check
    public void checkNoRedefinedDeclarations(RedefinableDeclaration redefinableDeclaration) {
        if (redefinableDeclaration.isRedefine()) {
            try {
                var redefined = redefinitionHandler.getRedefinedDeclaration(redefinableDeclaration);
                if (redefined == null) {
                    acceptError(
                            "Could not find redefined declaration.",
                            redefinableDeclaration,
                            OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                            0,
                            REDEFINED_NOT_FOUND_ISSUE);
                }
            } catch (Exception e) {
                acceptError(
                        "Could not find redefined declaration.",
                        redefinableDeclaration,
                        OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                        0,
                        REDEFINED_NOT_FOUND_ISSUE);
            }
        }
    }

    @Check
    public void checkSingleRedefinitionPerInheritedMember(ClassDeclaration classDeclaration) {
        checkSingleRedefinitionPerInheritedMember(classDeclaration.getMembers());
    }

    @Check
    public void checkSingleRedefinitionPerInheritedMember(FeatureDeclaration featureDeclaration) {
        checkSingleRedefinitionPerInheritedMember(featureDeclaration.getInnerFeatures());
    }

    // the member collection keeps only the last redefinition of an inherited member
    private void checkSingleRedefinitionPerInheritedMember(List<? extends Declaration> members) {
        var redefinitions = new LinkedHashMap<RedefinableDeclaration, List<RedefinableDeclaration>>();
        for (var member : members) {
            if (!(member instanceof RedefinableDeclaration redefinable)) {
                continue;
            }
            RedefinableDeclaration redefined;
            try {
                redefined = redefinitionHandler.getRedefinedDeclaration(redefinable);
            } catch (RuntimeException e) {
                continue;
            }
            if (redefined != null) {
                redefinitions
                        .computeIfAbsent(redefined, ignored -> {
                            return new ArrayList<>();
                        })
                        .add(redefinable);
            }
        }
        for (var entry : redefinitions.entrySet()) {
            var redefiningNames = new LinkedHashSet<String>();
            for (var redefining : entry.getValue()) {
                redefiningNames.add("'" + NamingUtil.getName(redefining) + "'");
            }
            // members of one name are already a duplicate name
            if (redefiningNames.size() < 2) {
                continue;
            }
            var message = "Inherited '"
                    + NamingUtil.getName(entry.getKey())
                    + "' is redefined by "
                    + String.join(" and ", redefiningNames)
                    + ", and only the last redefinition takes effect.";
            for (var redefining : entry.getValue()) {
                acceptError(message, redefining, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, DUPLICATE_REDEFINITION);
            }
        }
    }

    // a parameter or a local variable may share the name of a member, which `self.` then reaches
    @Check
    public void checkShadowedNames(NamedElement namedElement) {
        if (namedElement instanceof RedefinableDeclaration redefinableDeclaration
                && (redefinableDeclaration.isRedefine() || NamingUtil.isAnonymous(redefinableDeclaration))) {
            return;
        }

        if (!(namedElement.eContainer() instanceof DomainDeclaration parentDomain)) {
            return;
        }
        var declarations =
                domainMemberCollectionProvider.getParentCollection(parentDomain).getDeclarations();

        if (declarations.stream().anyMatch(d -> Objects.equals(NamingUtil.getName(d), namedElement.getName()))) {
            acceptWarning(
                    "Name shadows another element.",
                    namedElement,
                    OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                    0,
                    ELEMENT_SHADOWED);
        }
    }

    @Check
    public void checkDataTypeOnlyInBuiltin(DataTypeDeclaration dataTypeDeclaration) {
        if (!builtinSymbolResolver.isBuiltin(dataTypeDeclaration)) {
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
                var message = String.format(
                        "Expected feature to have opposite '%s'.", NamingUtil.getName(featureDeclaration));
                acceptError(message, opposite, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, INCORRECT_OPPOSITE);
            } else if (oppositeOpposite != featureDeclaration) {
                var oppositeMessage = String.format(
                        "Expected feature to have opposite '%s', got '%s' instead.",
                        NamingUtil.getName(featureDeclaration), NamingUtil.getName(oppositeOpposite));
                acceptError(
                        oppositeMessage,
                        opposite,
                        OxstsPackage.Literals.FEATURE_DECLARATION__OPPOSITE,
                        0,
                        INCORRECT_OPPOSITE);
            } else {
                if (featureDeclaration.getKind() == FeatureKind.CONTAINER
                        && opposite.getKind() != FeatureKind.CONTAINMENT) {
                    acceptError(
                            "Container's opposite must be containment.",
                            featureDeclaration,
                            OxstsPackage.Literals.FEATURE_DECLARATION__OPPOSITE,
                            0,
                            INCORRECT_OPPOSITE);
                } else if (featureDeclaration.getKind() == FeatureKind.CONTAINMENT
                        && opposite.getKind() != FeatureKind.CONTAINER) {
                    acceptError(
                            "Containment's opposite must be container.",
                            featureDeclaration,
                            OxstsPackage.Literals.FEATURE_DECLARATION__OPPOSITE,
                            0,
                            INCORRECT_OPPOSITE);
                }
            }
        } else if (featureDeclaration.getKind() == FeatureKind.CONTAINER) {
            acceptError("Container feature must have an opposite.", featureDeclaration, INCORRECT_OPPOSITE);
        }
    }

    @Check
    public void checkDerivedFeatureHasOpposite(FeatureDeclaration featureDeclaration) {
        if (featureDeclaration.getKind() == FeatureKind.DERIVED
                && featureDeclaration.getOpposite() == null
                && oppositeHandler.getOppositeFeature(featureDeclaration) == null) {
            acceptError(
                    "A `derived refers` is computed from its opposite, and '"
                            + featureDeclaration.getName()
                            + "' has none; other derivations are not yet supported by the compiler.",
                    featureDeclaration,
                    OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                    0,
                    UNSUPPORTED_FEATURE);
        }
    }

    @Check
    public void checkSubsetsTarget(FeatureDeclaration featureDeclaration) {
        var superset = featureDeclaration.getSuperset();
        if (superset == null || superset.eIsProxy()) {
            return;
        }
        String reason;
        if (superset.getKind() == FeatureKind.DERIVED) {
            reason = "a `derived refers` holds the instances its opposite gives it";
        } else if (superset.getExpression() != null) {
            reason = "a bound feature holds the instances its expression gives it";
        } else {
            return;
        }
        acceptError(
                "'"
                        + featureDeclaration.getName()
                        + "' cannot subset '"
                        + superset.getName()
                        + "': "
                        + reason
                        + ", so the instances of '"
                        + featureDeclaration.getName()
                        + "' would not reach it.",
                featureDeclaration,
                OxstsPackage.Literals.FEATURE_DECLARATION__SUPERSET,
                0,
                INCORRECT_SUBSETS);
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
    public void checkRecordNotRecursive(RecordDeclaration recordDeclaration) {
        if (containsRecord(recordDeclaration, recordDeclaration, new LinkedHashSet<>())) {
            acceptError(
                    "Record '" + recordDeclaration.getName() + "' contains itself, so it has no finite value.",
                    recordDeclaration,
                    OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                    0,
                    RECURSIVE_RECORD);
        }
    }

    private static boolean containsRecord(
            RecordDeclaration holder, RecordDeclaration target, Set<RecordDeclaration> visited) {
        if (!visited.add(holder)) {
            return false;
        }
        for (var member : holder.getMembers()) {
            if (member instanceof VariableDeclaration field
                    && field.getTypeSpecification() != null
                    && field.getTypeSpecification().getDomain() instanceof RecordDeclaration fieldRecord
                    && (fieldRecord == target || containsRecord(fieldRecord, target, visited))) {
                return true;
            }
        }
        return false;
    }

    @Check
    public void checkRecordFieldSupported(VariableDeclaration variableDeclaration) {
        var typeSpecification = variableDeclaration.getTypeSpecification();
        if (!(variableDeclaration.eContainer() instanceof RecordDeclaration) || typeSpecification == null) {
            return;
        }
        var domain = typeSpecification.getDomain();
        if (!(domain instanceof DataTypeDeclaration
                || domain instanceof EnumDeclaration
                || domain instanceof RecordDeclaration)) {
            acceptError(
                    "Record fields must have a data, enum or record type; '"
                            + variableDeclaration.getName()
                            + "' has type '"
                            + NamingUtil.getName(domain)
                            + "'.",
                    variableDeclaration,
                    OxstsPackage.Literals.VARIABLE_DECLARATION__TYPE_SPECIFICATION,
                    0,
                    UNSUPPORTED_FEATURE);
        } else if (!(domain instanceof RecordDeclaration) && isFixedSizeArray(typeSpecification)) {
            acceptError(
                    "Array fields in records are not yet supported by the compiler; '"
                            + variableDeclaration.getName()
                            + "' is an array.",
                    variableDeclaration,
                    OxstsPackage.Literals.VARIABLE_DECLARATION__TYPE_SPECIFICATION,
                    0,
                    UNSUPPORTED_FEATURE);
        }
    }

    @Check
    public void checkRecordMultiplicity(TypeSpecification typeSpecification) {
        if (!(typeSpecification.getDomain() instanceof RecordDeclaration record)
                || typeSpecification.getMultiplicity() == null) {
            return;
        }
        RangeEvaluation range;
        try {
            range = multiplicityRangeEvaluator.evaluate(typeSpecification);
        } catch (RuntimeException e) {
            return;
        }
        if (range.getLowerBound() == 1 && range.getUpperBound() == 1) {
            return;
        }
        var message = isFixedSizeArray(typeSpecification)
                ? "Arrays of records are not yet supported by the compiler."
                : "A value of record type '" + record.getName() + "' must hold exactly one record.";
        acceptError(message, typeSpecification, UNSUPPORTED_FEATURE);
    }

    @Check
    public void checkRecordLiteralFields(RecordLiteral recordLiteral) {
        var record = recordLiteral.getRecord();
        if (record == null || record.eIsProxy()) {
            return;
        }
        var assigned = new LinkedHashSet<VariableDeclaration>();
        for (var fieldAssignment : recordLiteral.getFields()) {
            var field = fieldAssignment.getField();
            if (field != null && !field.eIsProxy() && !assigned.add(field)) {
                acceptError(
                        "Field '" + field.getName() + "' is assigned more than once.",
                        fieldAssignment,
                        OxstsPackage.Literals.FIELD_ASSIGNMENT__FIELD,
                        0,
                        DUPLICATE_NAME_ISSUE);
            }
        }
    }

    @Check
    public void checkFieldAssignmentType(FieldAssignment fieldAssignment) {
        var field = fieldAssignment.getField();
        var expression = fieldAssignment.getExpression();
        if (field == null || field.eIsProxy() || field.getTypeSpecification() == null || expression == null) {
            return;
        }
        var declared = typeEvaluatorProvider.getEvaluator(field).fromTypeSpecification(field.getTypeSpecification());
        var actual = evaluateTypeSafely(expression);
        if (actual == null) {
            return;
        }
        if (!typeCompatibility.isAssignable(declared, actual, fieldAssignment)) {
            acceptError(
                    "Value of type "
                            + formatType(actual)
                            + " is not assignable to field '"
                            + field.getName()
                            + "' of type "
                            + formatType(declared)
                            + ".",
                    fieldAssignment,
                    OxstsPackage.Literals.FIELD_ASSIGNMENT__EXPRESSION,
                    0,
                    TYPE_MISMATCH);
        }
    }

    @Check
    public void checkRecordLiteralNotEvaluatedAtCompileTime(RecordLiteral recordLiteral) {
        EObject current = recordLiteral;
        while (current.eContainer() instanceof Expression
                || current.eContainer() instanceof FieldAssignment
                || (current.eContainer() instanceof Argument argument
                        && argument.eContainer() instanceof CallSuffixExpression)) {
            current = current.eContainer();
        }
        var position = switch (current.eContainer()) {
            case InlineIfOperation ignored -> "an `inline if` guard";
            case InlineForOperation ignored -> "an `inline for` range";
            case FeatureDeclaration ignored -> "a feature binding";
            case DefiniteMultiplicity ignored -> "a multiplicity";
            case Argument ignored -> "an annotation argument";
            case null, default -> null;
        };
        if (position != null) {
            acceptError(
                    "Record literals are not yet evaluated at compile time, so one cannot appear in " + position + ".",
                    recordLiteral,
                    UNSUPPORTED_FEATURE);
        }
    }

    // `p := Point {}` parses as `p := Point` followed by an empty sequence
    @Check
    public void checkRecordNameNotUsedAsValue(ElementReference reference) {
        if (reference.getElement() instanceof RecordDeclaration record
                && !record.eIsProxy()
                && !(reference.eContainer() instanceof CallSuffixExpression call && call.getPrimary() == reference)) {
            acceptError(
                    "'"
                            + record.getName()
                            + "' is a record type, not a value; write a literal that names at least one field, such as '"
                            + record.getName()
                            + " { ... }'.",
                    reference,
                    TYPE_MISMATCH);
        }
    }

    @Check
    public void checkFeatureNotRecordTyped(FeatureDeclaration featureDeclaration) {
        var typeSpecification = featureDeclaration.getTypeSpecification();
        if (typeSpecification != null && typeSpecification.getDomain() instanceof RecordDeclaration) {
            acceptError(
                    "Record-typed features are not yet supported by the compiler; declare '"
                            + featureDeclaration.getName()
                            + "' as a variable.",
                    featureDeclaration,
                    OxstsPackage.Literals.FEATURE_DECLARATION__TYPE_SPECIFICATION,
                    0,
                    UNSUPPORTED_FEATURE);
        }
    }

    @Check
    public void checkSingleSupertype(ClassDeclaration classDeclaration) {
        var superTypes = classDeclaration.getSuperTypes();
        for (int i = 1; i < superTypes.size(); i++) {
            acceptError(
                    "Multiple inheritance is not supported; class '"
                            + NamingUtil.getName(classDeclaration)
                            + "' may extend only '"
                            + NamingUtil.getName(superTypes.getFirst())
                            + "', not also '"
                            + NamingUtil.getName(superTypes.get(i))
                            + "'.",
                    classDeclaration,
                    OxstsPackage.Literals.CLASS_DECLARATION__SUPER_TYPES,
                    i,
                    MULTIPLE_INHERITANCE);
        }
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
    public void checkUniqueParameters(ParametricDeclaration parametricDeclaration) {
        checkUniqueSimpleNames(parametricDeclaration.getParameters());
    }

    // a reference resolves to the first of two locals of one sequence
    @Check
    public void checkUniqueLocalVariables(SequenceOperation sequenceOperation) {
        checkUniqueSimpleNames(sequenceOperation.getSteps().stream()
                .filter(LocalVarDeclarationOperation.class::isInstance)
                .map(LocalVarDeclarationOperation.class::cast)
                .toList());
    }

    @Check
    public void checkCallExpressionArgumentCount(CallSuffixExpression callSuffixExpression) {
        // a half-written navigation carries its own syntax or linking diagnostic
        if (callSuffixExpression.getPrimary() instanceof NavigationSuffixExpression navigation
                && (navigation.getMember() == null || navigation.getMember().eIsProxy())) {
            return;
        }
        var primary = metaConstantExpressionEvaluatorProvider.evaluate(callSuffixExpression.getPrimary());

        if (!(primary instanceof ParametricDeclaration parametricDeclaration)) {
            return;
        }

        var parameters = parametricDeclaration.getParameters();
        var arguments = callSuffixExpression.getArguments();

        if (arguments.size() > parameters.size()) {
            acceptError(
                    "Expected at most " + parameters.size() + " arguments, found " + arguments.size() + ".",
                    callSuffixExpression,
                    INVALID_CALL_ARGUMENTS_COUND);
            return;
        }

        // a positional argument after a named one has its own error and binds nothing here
        var positionalAfterNamed = false;
        var seenNamed = false;
        for (var argument : arguments) {
            seenNamed |= argument.getParameter() != null;
            positionalAfterNamed |= seenNamed && argument.getParameter() == null;
        }
        if (positionalAfterNamed) {
            return;
        }

        var unbound = new ArrayList<String>();
        for (int i = 0; i < parameters.size(); i++) {
            var parameter = parameters.get(i);
            if (!isParameterOptional(parameter) && OxstsUtils.getArgumentFor(arguments, parameter, i) == null) {
                unbound.add(parameter.getName());
            }
        }
        if (!unbound.isEmpty()) {
            acceptError(
                    "Missing argument(s) for non-optional parameter(s): " + String.join(", ", unbound) + ".",
                    callSuffixExpression,
                    INVALID_CALL_ARGUMENTS_COUND,
                    unbound.toArray(String[]::new));
        }
    }

    @Check
    public void checkCallArgumentBindings(CallSuffixExpression callSuffixExpression) {
        if (callSuffixExpression.getPrimary() instanceof NavigationSuffixExpression navigation
                && (navigation.getMember() == null || navigation.getMember().eIsProxy())) {
            return;
        }
        var primary = metaConstantExpressionEvaluatorProvider.evaluate(callSuffixExpression.getPrimary());
        if (primary instanceof ParametricDeclaration parametricDeclaration) {
            checkArgumentBindings(callSuffixExpression.getArguments(), parametricDeclaration.getParameters());
        }
    }

    @Check
    public void checkAnnotationArgumentBindings(Annotation annotation) {
        var declaration = annotation.getDeclaration();
        if (declaration != null && !declaration.eIsProxy()) {
            checkArgumentBindings(annotation.getArguments(), declaration.getParameters());
        }
    }

    private void checkArgumentBindings(List<Argument> arguments, List<ParameterDeclaration> parameters) {
        var boundNames = new LinkedHashSet<String>();
        Argument firstNamed = null;
        for (int i = 0; i < arguments.size(); i++) {
            var argument = arguments.get(i);
            var named = argument.getParameter();
            if (named == null) {
                if (firstNamed != null) {
                    acceptError(
                            "Positional argument follows the named argument '"
                                    + firstNamed.getParameter().getName()
                                    + "'. Every named argument must follow all positional arguments.",
                            argument,
                            POSITIONAL_ARGUMENT_AFTER_NAMED);
                } else if (i < parameters.size()) {
                    boundNames.add(parameters.get(i).getName());
                }
                continue;
            }
            if (named.eIsProxy()) {
                continue;
            }
            if (firstNamed == null) {
                firstNamed = argument;
            }
            if (!boundNames.add(named.getName())) {
                acceptError(
                        "Parameter '"
                                + named.getName()
                                + "' is already bound by an earlier argument. A parameter is bound at most once, by position or by name.",
                        argument,
                        PARAMETER_BOUND_TWICE,
                        named.getName());
            }
        }
    }

    @Check
    public void checkParameterOrder(ParametricDeclaration parametricDeclaration) {
        ParameterDeclaration firstOptional = null;
        for (var parameter : parametricDeclaration.getParameters()) {
            boolean optional = isParameterOptional(parameter);
            if (optional) {
                if (firstOptional == null) {
                    firstOptional = parameter;
                }
            } else if (firstOptional != null) {
                var optionalName = firstOptional.getName() != null ? firstOptional.getName() : "<unnamed>";
                var paramName = parameter.getName() != null ? parameter.getName() : "<unnamed>";
                acceptError(
                        "Non-optional parameter '"
                                + paramName
                                + "' cannot follow optional parameter '"
                                + optionalName
                                + "'. Declare required parameters before optional ones.",
                        parameter,
                        OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                        0,
                        INVALID_CALL_ARGUMENTS_COUND);
            }
        }
    }

    /**
     * A parameter is optional iff its declared multiplicity's lower bound is 0 (i.e. {@code
     * [0..1]}, {@code [0..*]}, or {@code []}). Parameters with no explicit multiplicity default to
     * ONE and are therefore required.
     */
    private boolean isParameterOptional(ParameterDeclaration parameter) {
        var typeSpec = parameter.getTypeSpecification();
        if (typeSpec == null) {
            return false;
        }
        try {
            var range = multiplicityRangeEvaluator.evaluate(typeSpec);
            return range != null && range.getLowerBound() == 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Check
    public void checkCallExpressionArgumentTypes(CallSuffixExpression callSuffixExpression) {
        // a half-written navigation carries its own syntax or linking diagnostic
        if (callSuffixExpression.getPrimary() instanceof NavigationSuffixExpression navigation
                && (navigation.getMember() == null || navigation.getMember().eIsProxy())) {
            return;
        }
        var primary = metaConstantExpressionEvaluatorProvider.evaluate(callSuffixExpression.getPrimary());
        if (!(primary instanceof ParametricDeclaration parametricDeclaration)) {
            return;
        }
        var parameters = parametricDeclaration.getParameters();
        var arguments = callSuffixExpression.getArguments();
        for (int i = 0; i < parameters.size(); i++) {
            var argument = OxstsUtils.getArgumentFor(arguments, parameters.get(i), i);
            if (argument != null) {
                checkArgumentAssignableToParameter(argument, parameters.get(i));
            }
        }
    }

    @Check
    public void checkAnnotationArgumentTypes(Annotation annotation) {
        var declaration = annotation.getDeclaration();
        if (declaration == null || declaration.eIsProxy()) {
            return;
        }
        var parameters = declaration.getParameters();
        var arguments = annotation.getArguments();
        for (int i = 0; i < parameters.size(); i++) {
            var argument = OxstsUtils.getArgumentFor(arguments, parameters.get(i), i);
            if (argument != null) {
                checkArgumentAssignableToParameter(argument, parameters.get(i));
            }
        }
    }

    @Check
    public void checkAnnotationArgumentModality(Annotation annotation) {
        for (var argument : annotation.getArguments()) {
            if (argument.getExpression() != null) {
                checkModalityAtMost(
                        argument.getExpression(),
                        Modality.CONSTANT,
                        "Annotation argument must be a constant, annotations have no instance context");
            }
        }
    }

    @Check
    public void checkBuiltinAnnotationTarget(Annotation annotation) {
        var declaration = annotation.getDeclaration();
        if (declaration == null
                || declaration.eIsProxy()
                || !builtinSymbolResolver.isBuiltin(declaration)
                || !(annotation.eContainer() instanceof AnnotationContainer container)) {
            return;
        }
        var annotated = container.eContainer();
        var name = qualifiedNameProvider.getFullyQualifiedName(declaration);
        String target;
        boolean targeted;
        if (BuiltinSymbolResolver.VERIFICATION_CASE_ANNOTATION_NAME.equals(name)
                || BuiltinSymbolResolver.TAG_ANNOTATION_NAME.equals(name)) {
            target = "classes";
            targeted = annotated instanceof ClassDeclaration;
        } else if (BuiltinSymbolResolver.TRACE_ANNOTATION_NAME.equals(name)) {
            target = "transitions";
            targeted = annotated instanceof TransitionDeclaration;
        } else if (BuiltinSymbolResolver.SHARED_ANNOTATION_NAME.equals(name)) {
            target = "containment features";
            targeted = annotated instanceof FeatureDeclaration feature && feature.getKind() == FeatureKind.CONTAINMENT;
        } else if (BuiltinSymbolResolver.CONTROL_ANNOTATION_NAME.equals(name)
                || BuiltinSymbolResolver.CLOCK_ANNOTATION_NAME.equals(name)
                || BuiltinSymbolResolver.NON_OPTIMIZABLE_ANNOTATION_NAME.equals(name)) {
            target = "variables";
            targeted = annotated instanceof VariableDeclaration;
        } else {
            return;
        }
        if (!targeted) {
            acceptError(
                    "@" + declaration.getName() + " applies to " + target + " only, so here it has no effect.",
                    annotation,
                    OxstsPackage.Literals.ANNOTATION__DECLARATION,
                    0,
                    INCORRECT_ANNOTATION_TARGET);
        }
    }

    @Check
    public void checkTracedCallArgumentModality(CallSuffixExpression callSuffixExpression) {
        // a half-written navigation carries its own syntax or linking diagnostic
        if (callSuffixExpression.getPrimary() instanceof NavigationSuffixExpression navigation
                && (navigation.getMember() == null || navigation.getMember().eIsProxy())) {
            return;
        }
        var primary = metaConstantExpressionEvaluatorProvider.evaluate(callSuffixExpression.getPrimary());
        if (!(primary instanceof TransitionDeclaration transitionDeclaration)
                || !builtinAnnotationHandler.isTransitionTraced(transitionDeclaration)) {
            return;
        }
        for (var argument : callSuffixExpression.getArguments()) {
            var argumentType = argument.getExpression() != null ? evaluateTypeSafely(argument.getExpression()) : null;
            if (argumentType != null && argumentType.getDomain() instanceof RecordDeclaration) {
                acceptError(
                        "Record arguments of the traced transition '"
                                + NamingUtil.getName(transitionDeclaration)
                                + "' are not yet supported by the compiler, the witness cannot record a record value.",
                        argument,
                        UNSUPPORTED_FEATURE);
            } else if (argument.getExpression() != null) {
                checkModalityAtMost(
                        argument.getExpression(),
                        Modality.COMPILE_TIME,
                        "Argument of a call to the traced transition '"
                                + NamingUtil.getName(transitionDeclaration)
                                + "' must be evaluable at compile time, the witness records its value");
            }
        }
    }

    private void checkArgumentAssignableToParameter(Argument argument, ParameterDeclaration parameter) {
        if (parameter == null || parameter.eIsProxy() || parameter.getTypeSpecification() == null) {
            return;
        }
        Expression expression = argument.getExpression();
        if (expression == null) {
            return;
        }
        TypeEvaluation expressionType = evaluateTypeSafely(expression);
        if (expressionType == null) {
            return;
        }
        TypeEvaluation parameterType =
                typeEvaluatorProvider.getEvaluator(parameter).fromTypeSpecification(parameter.getTypeSpecification());
        if (!typeCompatibility.isAssignable(parameterType, expressionType, argument)) {
            acceptError(
                    "Argument of type "
                            + formatType(expressionType)
                            + " is not assignable to parameter '"
                            + parameter.getName()
                            + "' of type "
                            + formatType(parameterType)
                            + ".",
                    argument,
                    TYPE_MISMATCH,
                    narrowingCastData(parameterType, expressionType, argument));
        }
    }

    private String[] narrowingCastData(TypeEvaluation expected, TypeEvaluation actual, EObject context) {
        if (!(actual instanceof ImmutableTypeEvaluation)
                || !(typeCompatibility.unwrappedDomain(expected) instanceof ClassDeclaration castClass)) {
            return new String[0];
        }
        var castType = new ImmutableTypeEvaluation(castClass);
        if (!typeCompatibility.isAssignable(actual, castType, context)
                || !typeCompatibility.isAssignable(expected, castType, context)) {
            return new String[0];
        }
        return new String[] {castClass.getName(), EcoreUtil.getURI(castClass).toString()};
    }

    @Check
    public void checkHavocTargetIsVariable(HavocOperation havocOperation) {
        var reference = havocOperation.getReference();
        if (reference == null) {
            return;
        }
        var written = evaluateWriteTarget(reference);
        if (written instanceof ParameterDeclaration parameter) {
            rejectParameterWrite(parameter, havocOperation, OxstsPackage.Literals.HAVOC_OPERATION__REFERENCE);
        } else if (!(written instanceof VariableDeclaration)) {
            acceptError(
                    "havoc target must be a variable reference.",
                    havocOperation,
                    OxstsPackage.Literals.HAVOC_OPERATION__REFERENCE,
                    0,
                    INCORRECT_ASSIGNMENT);
        }
    }

    @Check
    public void checkPropertyReturnType(PropertyDeclaration propertyDeclaration) {
        var expression = propertyDeclaration.getExpression();
        if (expression == null) {
            return;
        }
        var returnTypeSpec = propertyTypeHandler.getPropertyReturnType(propertyDeclaration);
        if (returnTypeSpec == null) {
            return;
        }
        var expected = typeEvaluatorProvider.getEvaluator(propertyDeclaration).fromTypeSpecification(returnTypeSpec);
        var actual = evaluateTypeSafely(expression);
        if (actual == null) {
            return;
        }
        if (!typeCompatibility.isAssignable(expected, actual, expression)) {
            acceptError(
                    "Property body has type "
                            + formatType(actual)
                            + " but is expected to return "
                            + formatType(expected)
                            + ".",
                    propertyDeclaration,
                    OxstsPackage.Literals.PROPERTY_DECLARATION__EXPRESSION,
                    0,
                    TYPE_MISMATCH,
                    narrowingCastData(expected, actual, expression));
        }
    }

    @Check
    public void checkVariableInitializerType(VariableDeclaration variableDeclaration) {
        if (OxstsUtils.isLoopVariable(variableDeclaration)) {
            return;
        }
        var initializer = variableDeclaration.getExpression();
        if (initializer == null || variableDeclaration.getTypeSpecification() == null) {
            return;
        }
        var declared = typeEvaluatorProvider
                .getEvaluator(variableDeclaration)
                .fromTypeSpecification(variableDeclaration.getTypeSpecification());
        var actual = evaluateTypeSafely(initializer);
        if (actual == null) {
            return;
        }
        if (!typeCompatibility.isAssignable(declared, actual, variableDeclaration)) {
            acceptError(
                    "Initializer of type "
                            + formatType(actual)
                            + " is not assignable to variable '"
                            + variableDeclaration.getName()
                            + "' of type "
                            + formatType(declared)
                            + ".",
                    variableDeclaration,
                    OxstsPackage.Literals.VARIABLE_DECLARATION__EXPRESSION,
                    0,
                    TYPE_MISMATCH,
                    narrowingCastData(declared, actual, variableDeclaration));
        }
    }

    @Check
    public void checkFeatureBoundExpressionType(FeatureDeclaration featureDeclaration) {
        var expression = featureDeclaration.getExpression();
        if (expression == null || featureDeclaration.getTypeSpecification() == null) {
            return;
        }
        var declared = typeEvaluatorProvider
                .getEvaluator(featureDeclaration)
                .fromTypeSpecification(featureDeclaration.getTypeSpecification());
        var actual = evaluateTypeSafely(expression);
        if (actual == null) {
            return;
        }
        if (!typeCompatibility.isAssignable(declared, actual, featureDeclaration)) {
            acceptError(
                    "Feature bound expression of type "
                            + formatType(actual)
                            + " is not assignable to '"
                            + featureDeclaration.getName()
                            + "' of type "
                            + formatType(declared)
                            + ".",
                    featureDeclaration,
                    OxstsPackage.Literals.FEATURE_DECLARATION__EXPRESSION,
                    0,
                    TYPE_MISMATCH,
                    narrowingCastData(declared, actual, featureDeclaration));
        }
    }

    @Check
    public void checkForRangeType(AbstractForOperation forOperation) {
        var range = forOperation.getRangeExpression();
        if (range == null) {
            return;
        }
        var type = evaluateTypeSafely(range);
        if (type == null || type instanceof InvalidTypeEvaluation) {
            return;
        }
        if (typeCompatibility.isFeatureType(type) || typeCompatibility.isClassType(type)) {
            return;
        }
        var rangeEvaluation = type.getRange();
        boolean isCollection = rangeEvaluation != null
                && !(rangeEvaluation.getLowerBound() == 1 && rangeEvaluation.getUpperBound() == 1);
        boolean isIntRange = typeCompatibility.isNumeric(type, forOperation)
                && rangeEvaluation != null
                && rangeEvaluation.getLowerBound() != rangeEvaluation.getUpperBound();
        if (!isCollection && !isIntRange) {
            acceptError(
                    "`for` range must be a collection or an integer range (got " + formatType(type) + ").",
                    forOperation,
                    TYPE_MISMATCH);
        }
    }

    @Check
    public void checkRedefinitionSignatureCompatible(RedefinableDeclaration declaration) {
        if (!declaration.isRedefine()) {
            return;
        }
        RedefinableDeclaration inherited;
        try {
            inherited = redefinitionHandler.getRedefinedDeclaration(declaration);
        } catch (Exception e) {
            return;
        }
        if (inherited == null) {
            return;
        }
        var declaredSpec = typeSpecificationOf(declaration);
        var inheritedSpec = typeSpecificationOf(inherited);
        if (declaredSpec == null || inheritedSpec == null) {
            return;
        }
        var declaredType = typeEvaluatorProvider.getEvaluator(declaration).fromTypeSpecification(declaredSpec);
        var inheritedType = typeEvaluatorProvider.getEvaluator(inherited).fromTypeSpecification(inheritedSpec);
        if (!typeCompatibility.isAssignable(inheritedType, declaredType, declaration)) {
            acceptError(
                    "Redefinition has type "
                            + formatType(declaredType)
                            + " which is not assignable to the inherited type "
                            + formatType(inheritedType)
                            + ".",
                    declaration,
                    OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                    0,
                    TYPE_MISMATCH);
        }
    }

    // Parameter modality is uniform until `static` parameters exist, so only the arity can differ.
    @Check
    public void checkRedefinitionArity(RedefinableDeclaration declaration) {
        if (!declaration.isRedefine() || !(declaration instanceof ParametricDeclaration parametricDeclaration)) {
            return;
        }
        RedefinableDeclaration inherited;
        try {
            inherited = redefinitionHandler.getRedefinedDeclaration(declaration);
        } catch (RuntimeException e) {
            return;
        }
        if (!(inherited instanceof ParametricDeclaration inheritedParametricDeclaration)) {
            return;
        }
        var parameters = parametricDeclaration.getParameters();
        var inheritedParameters = inheritedParametricDeclaration.getParameters();
        if (parameters.size() != inheritedParameters.size()) {
            acceptError(
                    "Redefinition of '"
                            + NamingUtil.getName(declaration)
                            + "' takes "
                            + parameterCount(parameters.size())
                            + ", the inherited declaration takes "
                            + parameterCount(inheritedParameters.size())
                            + ".",
                    declaration,
                    OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                    0,
                    REDEFINITION_SIGNATURE_MISMATCH);
        }
    }

    private static String parameterCount(int count) {
        return count == 1 ? "1 parameter" : count + " parameters";
    }

    private TypeSpecification typeSpecificationOf(RedefinableDeclaration declaration) {
        return switch (declaration) {
            case VariableDeclaration variable -> variable.getTypeSpecification();
            case FeatureDeclaration feature -> feature.getTypeSpecification();
            case PropertyDeclaration property -> propertyTypeHandler.getPropertyReturnType(property);
            default -> null;
        };
    }

    @Check
    public void noAssignmentsToConstants(AssignmentOperation assignmentOperation) {
        // Indexing on the LHS (e.g. `slots[0] := ...`) targets an array element; the meta-evaluator
        // can't reduce an indexing expression on its own, so resolve down to the array variable
        // first. Same for navigation chains - we want the leaf reference's element.
        var reference = unwrapAssignmentTarget(assignmentOperation.getReference());

        var written = evaluateWriteTarget(reference);
        if (written instanceof ParameterDeclaration parameter) {
            rejectParameterWrite(parameter, assignmentOperation, OxstsPackage.Literals.ASSIGNMENT_OPERATION__REFERENCE);
        } else if (!(written instanceof VariableDeclaration)) {
            acceptError(
                    "Only variables can be assigned to!",
                    assignmentOperation,
                    OxstsPackage.Literals.ASSIGNMENT_OPERATION__REFERENCE,
                    0,
                    INCORRECT_ASSIGNMENT);
        }
    }

    // a runtime argument binds to a local before the body and a compile-time one is substituted, so the write is lost
    private void rejectParameterWrite(ParameterDeclaration parameter, EObject host, EStructuralFeature feature) {
        acceptError(
                "Cannot write parameter '" + parameter.getName() + "': arguments are passed by value.",
                host,
                feature,
                0,
                INCORRECT_ASSIGNMENT);
    }

    // a literal or another value that names no declaration is not a write target
    private NamedElement evaluateWriteTarget(Expression target) {
        try {
            return metaConstantExpressionEvaluatorProvider.evaluate(target);
        } catch (EvaluationFailureException e) {
            return null;
        }
    }

    @Check
    public void checkAssignmentTargetIsRuntime(AssignmentOperation assignmentOperation) {
        checkWriteTargetIsRuntime(
                assignmentOperation.getReference(),
                assignmentOperation,
                OxstsPackage.Literals.ASSIGNMENT_OPERATION__REFERENCE);
    }

    @Check
    public void checkHavocTargetIsRuntime(HavocOperation havocOperation) {
        checkWriteTargetIsRuntime(
                havocOperation.getReference(), havocOperation, OxstsPackage.Literals.HAVOC_OPERATION__REFERENCE);
    }

    @Check
    public void checkNoFieldWriteThroughParameter(AssignmentOperation assignmentOperation) {
        rejectFieldWriteThroughValue(
                assignmentOperation.getReference(),
                assignmentOperation,
                OxstsPackage.Literals.ASSIGNMENT_OPERATION__REFERENCE);
    }

    @Check
    public void checkNoFieldHavocThroughParameter(HavocOperation havocOperation) {
        rejectFieldWriteThroughValue(
                havocOperation.getReference(), havocOperation, OxstsPackage.Literals.HAVOC_OPERATION__REFERENCE);
    }

    private void rejectFieldWriteThroughValue(Expression target, EObject host, EStructuralFeature feature) {
        var current = target;
        while (current instanceof NavigationSuffixExpression navigation
                && navigation.getMember() instanceof VariableDeclaration member
                && member.eContainer() instanceof RecordDeclaration) {
            current = navigation.getPrimary();
        }
        if (current == target) {
            return;
        }
        if (current instanceof ElementReference reference
                && reference.getElement() instanceof ParameterDeclaration parameter) {
            acceptError(
                    "Cannot write a field of parameter '" + parameter.getName() + "': parameters are passed by value.",
                    host,
                    feature,
                    0,
                    INCORRECT_ASSIGNMENT);
        } else if (!(current instanceof ElementReference || current instanceof NavigationSuffixExpression)) {
            acceptError(
                    "Cannot write a field of a record value, only a field of a record variable.",
                    host,
                    feature,
                    0,
                    INCORRECT_ASSIGNMENT);
        }
    }

    @Check
    public void checkNoLoopVariableWrite(Operation operation) {
        Expression target;
        EStructuralFeature feature;
        if (operation instanceof AssignmentOperation assignmentOperation) {
            target = assignmentOperation.getReference();
            feature = OxstsPackage.Literals.ASSIGNMENT_OPERATION__REFERENCE;
        } else if (operation instanceof HavocOperation havocOperation) {
            target = havocOperation.getReference();
            feature = OxstsPackage.Literals.HAVOC_OPERATION__REFERENCE;
        } else {
            return;
        }
        if (target == null) {
            return;
        }
        if (evaluateWriteTarget(unwrapAssignmentTarget(target)) instanceof VariableDeclaration variable
                && OxstsUtils.isLoopVariable(variable)) {
            acceptError(
                    "Cannot write loop variable '" + variable.getName() + "': it takes its values from the range.",
                    operation,
                    feature,
                    0,
                    INCORRECT_ASSIGNMENT);
        }
    }

    // The right-hand side is at most RUNTIME, so it always fits a runtime target.
    private void checkWriteTargetIsRuntime(Expression target, EObject host, EStructuralFeature feature) {
        if (target == null) {
            return;
        }
        EObject written;
        Modality modality;
        try {
            written = metaConstantExpressionEvaluatorProvider.evaluate(unwrapAssignmentTarget(target));
            modality = modalityEvaluatorProvider.evaluate(target);
        } catch (RuntimeException e) {
            return;
        }
        if (written instanceof VariableDeclaration variable
                && modality != Modality.RUNTIME
                && !OxstsUtils.isLoopVariable(variable)) {
            acceptError(
                    "Cannot write '"
                            + variable.getName()
                            + "': it has modality "
                            + modality
                            + " and is substituted at compile time, only runtime variables can be written.",
                    host,
                    feature,
                    0,
                    INCORRECT_ASSIGNMENT);
        }
    }

    private static Expression unwrapAssignmentTarget(Expression reference) {
        // Strip outer indexing layers so the meta-evaluator sees the variable being indexed.
        // Navigation chains are left alone - the evaluator already follows them via the member
        // reference and returns the leaf variable.
        var current = reference;
        while (current instanceof IndexingSuffixExpression indexing) {
            current = indexing.getPrimary();
        }
        return current;
    }

    @Check
    public void checkValidCalledElements(CallSuffixExpression callSuffixExpression) {
        // a half-written navigation carries its own syntax or linking diagnostic
        if (callSuffixExpression.getPrimary() instanceof NavigationSuffixExpression navigation
                && (navigation.getMember() == null || navigation.getMember().eIsProxy())) {
            return;
        }
        var called = metaConstantExpressionEvaluatorProvider.evaluate(callSuffixExpression.getPrimary());

        if (!OxstsUtils.isCallable(called)) {
            acceptError(
                    "Element " + called.getName() + " is not callable!",
                    callSuffixExpression,
                    OxstsPackage.Literals.POSTFIX_UNARY_EXPRESSION__PRIMARY,
                    0,
                    INCORRECT_CALLED_ELEMENT);
        }
    }

    @Check
    public void checkCallThroughSingleValuedFeature(CallSuffixExpression callSuffixExpression) {
        if (!(callSuffixExpression.getPrimary() instanceof NavigationSuffixExpression navigation)) {
            return;
        }
        var holder = navigation.getPrimary();
        FeatureDeclaration feature = null;
        if (holder instanceof ElementReference reference
                && reference.getElement() instanceof FeatureDeclaration referenced) {
            feature = referenced;
        } else if (holder instanceof NavigationSuffixExpression holderNavigation
                && holderNavigation.getMember() instanceof FeatureDeclaration member) {
            feature = member;
        }
        if (feature == null || feature.getTypeSpecification() == null) {
            return;
        }
        RangeEvaluation range;
        try {
            range = multiplicityRangeEvaluator.evaluate(feature.getTypeSpecification());
        } catch (RuntimeException e) {
            return;
        }
        var upperBound = range.getUpperBound();
        if (upperBound == RangeEvaluation.INFINITY || upperBound > 1) {
            var upperText = upperBound == RangeEvaluation.INFINITY ? "*" : String.valueOf(upperBound);
            acceptError(
                    "Cannot call through feature '"
                            + feature.getName()
                            + "', whose upper multiplicity is "
                            + upperText
                            + "; a call needs a holder with at most one instance, so call each instance in an `inline for` over the feature.",
                    navigation,
                    CALL_THROUGH_MANY_VALUED_FEATURE);
        }
    }

    @Check
    public void checkNoStringAnnotationArgumentOnVariable(Annotation annotation) {
        if (!(annotation.eContainer() instanceof AnnotationContainer container)
                || !(container.eContainer() instanceof VariableDeclaration variable)) {
            return;
        }
        for (var argument : annotation.getArguments()) {
            var type = argument.getExpression() == null ? null : evaluateTypeSafely(argument.getExpression());
            if (type != null && typeCompatibility.isString(type, argument)) {
                var declaration = annotation.getDeclaration();
                var annotationName = declaration == null || declaration.eIsProxy() ? "" : declaration.getName();
                acceptError(
                        "Annotation '@"
                                + annotationName
                                + "' on variable '"
                                + variable.getName()
                                + "' cannot take a string argument; a variable keeps its annotations in the verified model, which holds no strings.",
                        annotation,
                        UNSUPPORTED_FEATURE);
                return;
            }
        }
    }

    @Check
    public void checkSelfInsideClass(SelfReference selfReference) {
        if (selfReference.getClass_() == null
                && EcoreUtil2.getContainerOfType(selfReference, ClassDeclaration.class) == null
                && EcoreUtil2.getRootContainer(selfReference) instanceof OxstsModelPackage) {
            acceptError(
                    "'self' is the instance of the enclosing class, and this expression is in no class.",
                    selfReference,
                    SELF_OUTSIDE_CLASS);
        }
    }

    @Check
    public void checkFeatureBoundExpressionModality(FeatureDeclaration featureDeclaration) {
        var expression = featureDeclaration.getExpression();
        if (expression == null) {
            return;
        }
        checkModalityAtMost(
                expression, Modality.COMPILE_TIME, "Feature bound expression must be evaluable at compile time");
    }

    @Check
    public void checkMultiplicityNotBareInfinity(DefiniteMultiplicity definiteMultiplicity) {
        if (definiteMultiplicity.getExpression() instanceof LiteralInfinity) {
            acceptError(
                    "`[*]` is not a valid multiplicity. Use `[]` for unbounded or `[lb..*]` for a lower-bounded unbounded range.",
                    definiteMultiplicity,
                    TYPE_MISMATCH);
        }
    }

    @Check
    public void checkMultiplicityBoundModality(DefiniteMultiplicity definiteMultiplicity) {
        Expression expression = definiteMultiplicity.getExpression();
        if (expression == null) {
            return;
        }
        checkModalityAtMost(expression, Modality.COMPILE_TIME, "Multiplicity bound must be evaluable at compile time");
    }

    @Check
    public void checkVariableInitializerModality(VariableDeclaration variableDeclaration) {
        if (OxstsUtils.isLocalVariable(variableDeclaration) || OxstsUtils.isLoopVariable(variableDeclaration)) {
            return;
        }
        var expression = variableDeclaration.getExpression();
        if (expression == null) {
            return;
        }
        checkModalityAtMost(
                expression, Modality.COMPILE_TIME, "Variable initializer must be evaluable at compile time");
    }

    @Check
    public void checkInlineIfGuardModality(InlineIfOperation inlineIfOperation) {
        var guard = inlineIfOperation.getGuard();
        if (guard == null) {
            return;
        }
        checkModalityAtMost(guard, Modality.COMPILE_TIME, "`inline if` guard must be evaluable at compile time");
    }

    @Check
    public void checkInlineForRangeModality(InlineForOperation inlineForOperation) {
        var range = inlineForOperation.getRangeExpression();
        if (range == null) {
            return;
        }
        checkModalityAtMost(
                range, Modality.COMPILE_TIME, "`inline for` range expression must be evaluable at compile time");
    }

    @Check
    public void checkRuntimeOperationCouldBeInline(Operation operation) {
        Expression compileTimeCandidate;
        String candidateName;
        if (operation instanceof IfOperation ifOperation) {
            compileTimeCandidate = ifOperation.getGuard();
            candidateName = "guard";
        } else if (operation instanceof ForOperation forOperation) {
            compileTimeCandidate = forOperation.getRangeExpression();
            candidateName = "range";
        } else {
            return;
        }
        if (compileTimeCandidate == null || EcoreUtil2.getContainerOfType(operation, InlinedOxsts.class) != null) {
            return;
        }
        Modality modality;
        try {
            modality = modalityEvaluatorProvider.evaluate(compileTimeCandidate);
        } catch (RuntimeException e) {
            return;
        }
        if (!modality.isAtMost(Modality.COMPILE_TIME)) {
            return;
        }
        for (var leaf : NodeModelUtils.findActualNodeFor(operation).getLeafNodes()) {
            if (!leaf.isHidden()) {
                var keyword = leaf.getText();
                acceptInfo(
                        "`%s` could be `inline %s`: its %s is evaluable at compile time."
                                .formatted(keyword, keyword, candidateName),
                        operation,
                        leaf.getOffset(),
                        leaf.getLength(),
                        COULD_BE_INLINE);
                return;
            }
        }
    }

    @Check
    public void checkConstantExpression(Expression expression) {
        if (!isFoldableConstant(expression)
                || (expression.eContainer() instanceof Expression container && isFoldableConstant(container))
                || EcoreUtil2.getContainerOfType(expression, InlinedOxsts.class) != null) {
            return;
        }
        var region = locationInFileProvider.getFullTextRegion(expression);
        acceptInfo(
                "The expression is constant, it can be inlined as its value.",
                expression,
                region.getOffset(),
                region.getLength(),
                CONSTANT_EXPRESSION);
    }

    private boolean isFoldableConstant(Expression expression) {
        if (expression instanceof LiteralExpression
                || expression instanceof ElementReference
                || expression instanceof RangeExpression
                || (expression instanceof ArithmeticUnaryOperator unary
                        && unary.getBody() instanceof LiteralExpression)) {
            return false;
        }
        try {
            return modalityEvaluatorProvider.evaluate(expression) == Modality.CONSTANT
                    && !(typeEvaluatorProvider.evaluate(expression) instanceof InvalidTypeEvaluation)
                    && !(constantExpressionEvaluatorProvider.evaluate(expression) instanceof RangeEvaluation);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Check
    public void checkAssignmentTypes(AssignmentOperation assignmentOperation) {
        var lhs = assignmentOperation.getReference();
        var rhs = assignmentOperation.getExpression();
        if (lhs == null || rhs == null) {
            return;
        }
        var lhsType = evaluateTypeSafely(lhs);
        var rhsType = evaluateTypeSafely(rhs);
        if (lhsType == null || rhsType == null) {
            return;
        }
        if (!typeCompatibility.isAssignable(lhsType, rhsType, assignmentOperation)) {
            acceptError(
                    "Cannot assign value of type "
                            + formatType(rhsType)
                            + " to target of type "
                            + formatType(lhsType)
                            + ".",
                    assignmentOperation,
                    TYPE_MISMATCH,
                    narrowingCastData(lhsType, rhsType, assignmentOperation));
        }
    }

    @Check
    public void checkAssumptionType(AssumptionOperation assumptionOperation) {
        requireBoolean(assumptionOperation.getExpression(), "`assume` expression must be boolean");
    }

    @Check
    public void checkIfGuardType(IfOperation ifOperation) {
        requireBoolean(ifOperation.getGuard(), "`if` guard must be boolean");
    }

    @Check
    public void checkInlineIfGuardType(InlineIfOperation inlineIfOperation) {
        requireBoolean(inlineIfOperation.getGuard(), "`inline if` guard must be boolean");
    }

    @Check
    public void checkNegationOperandType(NegationOperator negationOperator) {
        requireBoolean(negationOperator.getBody(), "negation operand must be boolean");
    }

    @Check
    public void checkArithmeticUnaryOperandType(ArithmeticUnaryOperator operator) {
        requireNumeric(operator.getBody(), "unary arithmetic operand must be numeric");
    }

    @Check
    public void checkArithmeticBinaryOperandTypes(ArithmeticBinaryOperator operator) {
        requireNumeric(operator.getLeft(), "left arithmetic operand must be numeric");
        requireNumeric(operator.getRight(), "right arithmetic operand must be numeric");
    }

    @Check
    public void checkBooleanOperandTypes(BooleanOperator operator) {
        requireBoolean(operator.getLeft(), "left boolean operand must be boolean");
        requireBoolean(operator.getRight(), "right boolean operand must be boolean");
    }

    // Temporary, for one release: until 2026-11-01 the three operators shared one level and read left to right.
    @Check
    public void checkMixedBooleanOperators(BooleanOperator operator) {
        var leftNode = NodeModelUtils.findActualNodeFor(operator.getLeft());
        var rightNode = NodeModelUtils.findActualNodeFor(operator.getRight());
        if (leftNode == null || rightNode == null) {
            return;
        }
        var leftMixed = isMixedWithoutParentheses(operator.getLeft(), leftNode, operator.getOp());
        var rightMixed = isMixedWithoutParentheses(operator.getRight(), rightNode, operator.getOp());
        if (!leftMixed && !rightMixed) {
            return;
        }
        var reading = operandText(leftNode, leftMixed)
                + " "
                + operatorText(operator.getOp())
                + " "
                + operandText(rightNode, rightMixed);
        acceptWarning(
                "`&&` binds tighter than `^^`, which binds tighter than `||`, so this reads as `"
                        + reading
                        + "`; until November 2026 the three read left to right. Add parentheses to say which reading you mean.",
                operator,
                OxstsPackage.Literals.BOOLEAN_OPERATOR__OP,
                -1,
                MIXED_BOOLEAN_OPERATORS);
    }

    private static boolean isMixedWithoutParentheses(Expression operand, ICompositeNode node, BooleanOp op) {
        return operand instanceof BooleanOperator child && child.getOp() != op && !isWrappedInParentheses(node);
    }

    private static boolean isWrappedInParentheses(ICompositeNode node) {
        var depth = 0;
        var first = true;
        for (var leaf : node.getLeafNodes()) {
            if (leaf.isHidden()) {
                continue;
            }
            var keyword = leaf.getGrammarElement() instanceof Keyword k ? k.getValue() : null;
            if (first) {
                if (!"(".equals(keyword)) {
                    return false;
                }
                first = false;
            } else if (depth == 0) {
                return false;
            }
            if ("(".equals(keyword)) {
                depth++;
            } else if (")".equals(keyword)) {
                depth--;
            }
        }
        return !first;
    }

    private static String operandText(ICompositeNode node, boolean parenthesize) {
        var text = NodeModelUtils.getTokenText(node);
        return parenthesize ? "(" + text + ")" : text;
    }

    private static String operatorText(BooleanOp op) {
        return switch (op) {
            case AND -> "&&";
            case OR -> "||";
            case XOR -> "^^";
        };
    }

    @Check
    public void checkComparisonOperandTypes(ComparisonOperator operator) {
        var left = operator.getLeft();
        var right = operator.getRight();
        if (left == null || right == null) {
            return;
        }
        var leftType = evaluateTypeSafely(left);
        var rightType = evaluateTypeSafely(right);
        if (leftType == null || rightType == null) {
            return;
        }
        if (!typeCompatibility.isComparable(leftType, rightType, operator)) {
            acceptError(
                    "Cannot compare values of type " + formatType(leftType) + " and " + formatType(rightType) + ".",
                    operator,
                    TYPE_MISMATCH);
        } else if ((leftType.getDomain() instanceof RecordDeclaration
                        || rightType.getDomain() instanceof RecordDeclaration)
                && (leftType instanceof NothingTypeEvaluation
                        || rightType instanceof NothingTypeEvaluation
                        || (operator.getOp() != ComparisonOp.EQ && operator.getOp() != ComparisonOp.NOT_EQ))) {
            acceptError(
                    "Records can only be compared with == and != to a record of the same type.",
                    operator,
                    TYPE_MISMATCH);
        } else if ((isFixedSizeArray(leftType) || isFixedSizeArray(rightType))
                && (!isFixedSizeArray(leftType)
                        || !isFixedSizeArray(rightType)
                        || leftType.getRange().getUpperBound()
                                != rightType.getRange().getUpperBound()
                        || (operator.getOp() != ComparisonOp.EQ && operator.getOp() != ComparisonOp.NOT_EQ))) {
            acceptError(
                    "Arrays can only be compared with == and != to an array of the same size.",
                    operator,
                    TYPE_MISMATCH);
        } else if (operator.getOp() != ComparisonOp.EQ
                && operator.getOp() != ComparisonOp.NOT_EQ
                && (!typeCompatibility.isNumeric(leftType, operator)
                        || !typeCompatibility.isNumeric(rightType, operator))) {
            acceptError(
                    "Ordering comparisons need numeric operands, found "
                            + formatType(leftType)
                            + " and "
                            + formatType(rightType)
                            + "; only == and != compare other values.",
                    operator,
                    TYPE_MISMATCH);
        }
    }

    private static boolean isFixedSizeArray(TypeEvaluation type) {
        var range = type.getRange();
        return range != null
                && range.getUpperBound() != RangeEvaluation.INFINITY
                && range.getUpperBound() > 1
                && !(type.getDomain() instanceof ClassDeclaration || type.getDomain() instanceof FeatureDeclaration);
    }

    @Check
    public void checkIndexingTypes(IndexingSuffixExpression expression) {
        requireNumeric(expression.getIndex(), "array index must be numeric");
        var primary = expression.getPrimary();
        if (primary == null) {
            return;
        }
        var primaryType = evaluateTypeSafely(primary);
        if (primaryType == null || primaryType.getRange() == null) {
            return;
        }
        var upper = primaryType.getRange().getUpperBound();
        if (upper != RangeEvaluation.INFINITY
                && upper <= 1
                && primaryType.getRange().getLowerBound() <= 1) {
            acceptError(
                    "Cannot index a non-array value (multiplicity "
                            + primaryType.getRange().getLowerBound()
                            + ".."
                            + upper
                            + ").",
                    expression,
                    TYPE_MISMATCH);
        }
    }

    @Check
    public void checkArrayLiteralHomogeneous(ArrayLiteral arrayLiteral) {
        var values = arrayLiteral.getValues();
        if (values.size() < 2) {
            return;
        }
        var firstType = evaluateTypeSafely(values.getFirst());
        if (firstType == null) {
            return;
        }
        for (int i = 1; i < values.size(); i++) {
            var elementType = evaluateTypeSafely(values.get(i));
            if (elementType == null) {
                continue;
            }
            if (!typeCompatibility.isComparable(firstType, elementType, arrayLiteral)) {
                acceptError(
                        "Array literal elements must share a common type; element "
                                + i
                                + " has type "
                                + formatType(elementType)
                                + " but the first has type "
                                + formatType(firstType)
                                + ".",
                        arrayLiteral,
                        TYPE_MISMATCH);
                return;
            }
        }
    }

    @Check
    public void checkVariableInitialized(VariableDeclaration variableDeclaration) {
        // a nameless variable is half-parsed, and its syntax error is the diagnostic
        if (OxstsUtils.isLoopVariable(variableDeclaration) || variableDeclaration.getName() == null) {
            return;
        }
        var initializer = variableDeclaration.getExpression();
        if (initializer == null) {
            acceptError(
                    "Variable '"
                            + variableDeclaration.getName()
                            + "' has no initializer; an arbitrary initial value is a havoc in init.",
                    variableDeclaration,
                    OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                    0,
                    MISSING_INITIALIZER);
            return;
        }
        var typeSpecification = variableDeclaration.getTypeSpecification();
        if (!(initializer instanceof ArrayLiteral arrayLiteral)
                || typeSpecification == null
                || !isFixedSizeArray(typeSpecification)) {
            return;
        }
        var range = multiplicityRangeEvaluator.evaluate(typeSpecification);
        var valueCount = arrayLiteral.getValues().size();
        // a literal outside the multiplicity is already a type mismatch
        if (valueCount >= range.getLowerBound() && valueCount < range.getUpperBound()) {
            acceptError(
                    "Array literal gives "
                            + valueCount
                            + " of the "
                            + range.getUpperBound()
                            + " slots of '"
                            + variableDeclaration.getName()
                            + "'.",
                    arrayLiteral,
                    MISSING_INITIALIZER);
        }
    }

    @Check
    public void checkVariableMultiplicityBounded(VariableDeclaration variableDeclaration) {
        if (OxstsUtils.isLoopVariable(variableDeclaration)) {
            return;
        }
        var typeSpec = variableDeclaration.getTypeSpecification();
        if (typeSpec == null) {
            return;
        }
        rejectIfUnboundedMultiplicity(typeSpec.getMultiplicity(), variableDeclaration);
    }

    @Check
    public void checkParameterNotArray(ParameterDeclaration parameterDeclaration) {
        var typeSpecification = parameterDeclaration.getTypeSpecification();
        if (typeSpecification != null && isFixedSizeArray(typeSpecification)) {
            acceptError(
                    "Array parameters are not yet supported by the compiler; '"
                            + parameterDeclaration.getName()
                            + "' is an array.",
                    parameterDeclaration,
                    OxstsPackage.Literals.PARAMETER_DECLARATION__TYPE_SPECIFICATION,
                    0,
                    UNSUPPORTED_FEATURE);
        }
    }

    @Check
    public void checkNoWriteOrOptionalNavigationThroughVariable(NavigationSuffixExpression expression) {
        // the unresolved member carries its own linking diagnostic
        if (expression.getMember() == null || expression.getMember().eIsProxy()) {
            return;
        }
        if (expression.eContainer() instanceof CallSuffixExpression call
                && call.getPrimary() == expression
                && !expression.isOptional()) {
            return;
        }
        if (expression.getMember() instanceof VariableDeclaration member
                && member.eContainer() instanceof RecordDeclaration) {
            return;
        }
        EObject holder;
        Modality holderModality;
        try {
            holder = metaConstantExpressionEvaluatorProvider.evaluate(unwrapAssignmentTarget(expression.getPrimary()));
            holderModality = modalityEvaluatorProvider.evaluate(expression.getPrimary());
        } catch (RuntimeException e) {
            return;
        }
        if (!(holder instanceof VariableDeclaration variable) || holderModality != Modality.RUNTIME) {
            return;
        }
        if (isWriteTarget(expression)) {
            acceptError(
                    "Writing through variable '"
                            + variable.getName()
                            + "' is not yet supported by the compiler; only a read or a call through a variable dispatches over the instances it may hold.",
                    expression,
                    UNSUPPORTED_FEATURE);
        } else if (expression.isOptional()) {
            acceptError(
                    "Optional navigation '?.' through variable '"
                            + variable.getName()
                            + "' is not yet supported by the compiler; a read or a call with '.' dispatches over the instances the variable may hold.",
                    expression,
                    UNSUPPORTED_FEATURE);
        }
    }

    private static boolean isWriteTarget(Expression expression) {
        EObject current = expression;
        while (current.eContainer() instanceof PostfixUnaryExpression postfix && postfix.getPrimary() == current) {
            current = postfix;
        }
        var container = current.eContainer();
        return (container instanceof AssignmentOperation assignment && assignment.getReference() == current)
                || (container instanceof HavocOperation havoc && havoc.getReference() == current);
    }

    private boolean isFixedSizeArray(TypeSpecification typeSpecification) {
        if (typeSpecification.getMultiplicity() == null) {
            return false;
        }
        RangeEvaluation range;
        try {
            range = multiplicityRangeEvaluator.evaluate(typeSpecification);
        } catch (RuntimeException e) {
            return false;
        }
        var upperBound = range.getUpperBound();
        return upperBound != RangeEvaluation.INFINITY && upperBound > 1;
    }

    private void rejectIfUnboundedMultiplicity(Multiplicity multiplicity, EObject host) {
        if (multiplicity instanceof UnboundedMultiplicity unboundedMultiplicity) {
            acceptError(
                    "Array size must have a compile-time upper bound (got unbounded multiplicity `[]`).",
                    host,
                    TYPE_MISMATCH);
        } else if (multiplicity instanceof DefiniteMultiplicity definite
                && definite.getExpression() != null
                && containsInfinity(definite.getExpression())) {
            acceptError(
                    "Array size must have a compile-time upper bound (got `*` / infinity in multiplicity).",
                    host,
                    TYPE_MISMATCH);
        }
    }

    private boolean containsInfinity(EObject expression) {
        if (expression instanceof LiteralInfinity) {
            return true;
        }
        for (var child : expression.eContents()) {
            if (containsInfinity(child)) {
                return true;
            }
        }
        return false;
    }

    @Check
    public void checkCastCompatibility(CastExpression castExpression) {
        if (castExpression.getBody() == null || castExpression.getTypespecification() == null) {
            return;
        }
        var bodyType = evaluateTypeSafely(castExpression.getBody());
        if (bodyType == null) {
            return;
        }
        var castType = typeEvaluatorProvider
                .getEvaluator(castExpression)
                .fromTypeSpecification(castExpression.getTypespecification());
        if (castType == null || castType.getDomain() == null) {
            return;
        }

        if (!typeCompatibility.isAssignable(bodyType, castType, castExpression)) {
            acceptError(
                    "Cast from "
                            + formatType(bodyType)
                            + " to "
                            + formatType(castType)
                            + " is not a valid narrowing cast (widening or unrelated types are rejected).",
                    castExpression,
                    INVALID_CAST);
        }
    }

    @Check
    public void checkIfThenElseGuardType(IfThenElse expression) {
        requireBoolean(expression.getGuard(), "if-then-else guard must be boolean");
    }

    @Check
    public void checkIfThenElseBranchCompatibility(IfThenElse expression) {
        if (expression.getThen() == null || expression.getElse() == null) {
            return;
        }
        var thenType = evaluateTypeSafely(expression.getThen());
        var elseType = evaluateTypeSafely(expression.getElse());
        if (thenType == null || elseType == null) {
            return;
        }
        if (!typeCompatibility.isJoinable(thenType, elseType, expression)) {
            acceptError(
                    "Branches of if-then-else expression have incompatible types: "
                            + formatType(thenType)
                            + " and "
                            + formatType(elseType)
                            + ".",
                    expression,
                    TYPE_MISMATCH);
        }
    }

    @Check
    public void checkTemporalOperatorPlacement(TemporalOperator operator) {
        var keyword = operator instanceof AG ? "AG" : "EF";
        if (EcoreUtil2.getContainerOfType(operator.eContainer(), TemporalOperator.class) != null) {
            acceptError(
                    "Temporal operator `"
                            + keyword
                            + "` cannot be nested in another temporal operator; a temporal operator is only allowed as the whole expression a property returns.",
                    operator,
                    MISPLACED_TEMPORAL_OPERATOR);
            return;
        }
        EObject root = operator;
        while (root.eContainer() instanceof CastExpression cast && cast.getBody() == root) {
            root = cast;
        }
        if (root.eContainmentFeature() != OxstsPackage.Literals.PROPERTY_DECLARATION__EXPRESSION) {
            acceptError(
                    "Temporal operator `" + keyword + "` is only allowed as the whole expression a property returns.",
                    operator,
                    MISPLACED_TEMPORAL_OPERATOR);
        }
    }

    @Check
    public void checkAGBodyType(AG expression) {
        requireBoolean(expression.getBody(), "`AG` body must be boolean");
    }

    @Check
    public void checkEFBodyType(EF expression) {
        requireBoolean(expression.getBody(), "`EF` body must be boolean");
    }

    private void requireBoolean(Expression expression, String messagePrefix) {
        if (expression == null) {
            return;
        }
        var type = evaluateTypeSafely(expression);
        if (type == null) {
            return;
        }
        if (!typeCompatibility.isBoolean(type, expression)) {
            acceptError(messagePrefix + " (got " + formatType(type) + ").", expression, TYPE_MISMATCH);
        }
    }

    private void requireNumeric(Expression expression, String messagePrefix) {
        if (expression == null) {
            return;
        }
        var type = evaluateTypeSafely(expression);
        if (type == null) {
            return;
        }
        if (!typeCompatibility.isNumeric(type, expression)) {
            acceptError(messagePrefix + " (got " + formatType(type) + ").", expression, TYPE_MISMATCH);
        }
    }

    private TypeEvaluation evaluateTypeSafely(Expression expression) {
        try {
            return typeEvaluatorProvider.evaluate(expression);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String formatType(TypeEvaluation type) {
        if (type == null) {
            return "<unknown>";
        }
        var domain = type.getDomain();
        var name = domain != null ? domain.getName() : "<invalid>";
        return name != null ? name : "<anonymous>";
    }

    private void checkModalityAtMost(Expression expression, Modality upperBound, String messagePrefix) {
        Modality actual;
        try {
            actual = modalityEvaluatorProvider.evaluate(expression);
        } catch (RuntimeException e) {
            return;
        }
        if (!actual.isAtMost(upperBound)) {
            acceptError(messagePrefix + " (got modality " + actual + ").", expression, EXPRESSION_MODALITY_TOO_HIGH);
        }
    }

    // FIXME: this should be removed along with solving the problem in
    // semantics#OxstsInflator#pullDownVariables()
    @Check
    public void checkNoImplicitlyTypeVariables(VariableDeclaration variableDeclaration) {
        // a nameless variable is half-parsed, and its syntax error is the diagnostic
        if (OxstsUtils.isLoopVariable(variableDeclaration) || variableDeclaration.getName() == null) {
            return;
        }

        if (variableDeclaration.getTypeSpecification() == null) {
            acceptError("Variables may not have implicit types!", variableDeclaration, VARIABLE_WITH_IMPLICIT_TYPE);
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

    @Check
    public void checkAmbiguousReference(EObject element) {
        if (!(EcoreUtil2.getRootContainer(element) instanceof OxstsModelPackage modelPackage)) {
            return;
        }
        for (var reference : element.eClass().getEAllReferences()) {
            if (reference.isContainment() || reference.isContainer() || reference.isDerived()) {
                continue;
            }
            var value = element.eGet(reference, false);
            List<?> values;
            if (reference.isMany()) {
                values = (List<?>) value;
            } else {
                values = Collections.singletonList(value);
            }
            List<INode> nodes = null;
            for (var index = 0; index < values.size(); index++) {
                if (!(values.get(index) instanceof EObject target)
                        || target.eIsProxy()
                        || !(target.eContainer() instanceof OxstsModelPackage || target instanceof EnumLiteral)) {
                    continue;
                }
                if (nodes == null) {
                    nodes = NodeModelUtils.findNodesForFeature(element, reference);
                }
                if (index >= nodes.size()) {
                    break;
                }
                var text = linkingHelper.getCrossRefNodeAsString(nodes.get(index), true);
                if (Strings.isEmpty(text)) {
                    continue;
                }
                var candidates = getPackageLevelCandidates(
                        modelPackage, reference, qualifiedNameConverter.toQualifiedName(text));
                if (candidates.size() < 2
                        || !candidates.contains(qualifiedNameProvider.getFullyQualifiedName(target))) {
                    continue;
                }
                var qualifiedNames = candidates.stream()
                        .map(qualifiedNameConverter::toString)
                        .toList();
                var message = "Ambiguous reference '%s': it names both %s; write the qualified name %s."
                        .formatted(text, String.join(" and ", qualifiedNames), String.join(" or ", qualifiedNames));
                int issueIndex;
                if (reference.isMany()) {
                    issueIndex = index;
                } else {
                    issueIndex = INSIGNIFICANT_INDEX;
                }
                acceptError(message, element, reference, issueIndex, AMBIGUOUS_REFERENCE);
            }
        }
    }

    // outside its own file a name of the package resolves to the first file of the resource set declaring it
    @Check
    public void checkDeclaredInAnotherFileOfThePackage(OxstsModelPackage modelPackage) {
        if (Strings.isEmpty(modelPackage.getName())) {
            return;
        }
        var resource = modelPackage.eResource();
        var resources = List.copyOf(resource.getResourceSet().getResources());
        for (var declaration : modelPackage.getDeclarations()) {
            var qualifiedName = qualifiedNameProvider.getFullyQualifiedName(declaration);
            if (qualifiedName == null) {
                continue;
            }
            for (var other : resources) {
                if (other == resource) {
                    break;
                }
                var description = resourceDescriptionProvider.getResourceDescription(other);
                if (description == null
                        || !description
                                .getExportedObjects(OxstsPackage.Literals.DECLARATION, qualifiedName, false)
                                .iterator()
                                .hasNext()) {
                    continue;
                }
                var message = "'%s' is also declared in %s, which other files of the package resolve it to."
                        .formatted(
                                qualifiedNameConverter.toString(qualifiedName),
                                other.getURI().lastSegment());
                acceptWarning(
                        message,
                        declaration,
                        OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                        INSIGNIFICANT_INDEX,
                        DECLARED_IN_ANOTHER_FILE);
                break;
            }
        }
    }

    // a named package and every explicit import compete for a simple name, an unnamed package's own declarations win
    protected Set<QualifiedName> getPackageLevelCandidates(
            OxstsModelPackage modelPackage, EReference reference, QualifiedName name) {
        var resource = modelPackage.eResource();
        var index = new ScopeBasedSelectable(globalScopeProvider.getScope(resource, reference, null));
        var packageName = qualifiedNameProvider.getFullyQualifiedName(modelPackage);
        var selectables = new ArrayList<ISelectable>();
        if (packageName != null) {
            selectables.add(packageSelectableProvider.getFileSelectable(modelPackage));
            selectables.add(new TrimPrefixSelectable(index, packageName));
        }
        for (var importEntry : importCollector.getDirectImports(resource)) {
            if (!importEntry.isImplicit() && !importEntry.packageName().equals(packageName)) {
                selectables.add(new TrimPrefixSelectable(index, importEntry.packageName()));
            }
        }
        var candidates = new LinkedHashSet<QualifiedName>();
        for (var selectable : selectables) {
            for (var description : selectable.getExportedObjects(reference.getEReferenceType(), name, false)) {
                var candidate = EcoreUtil.resolve(description.getEObjectOrProxy(), modelPackage);
                candidates.add(qualifiedNameProvider.getFullyQualifiedName(candidate));
            }
        }
        return candidates;
    }

    protected void acceptError(String message, EObject object, String code, String... issueData) {
        var region = locationInFileProvider.getFullTextRegion(object);
        acceptError(message, object, region.getOffset(), region.getLength(), code, issueData);
    }
}

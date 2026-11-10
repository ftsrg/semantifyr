/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.WrappedOxstsPackage;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class PackageScopingTest {

    @Inject
    private ParseHelper<OxstsModelPackage> parseHelper;

    @Inject
    private ParseHelper<InlinedOxsts> inlinedOxstsParseHelper;

    @Inject
    private Provider<XtextResourceSet> resourceSetProvider;

    @Inject
    private IResourceValidator resourceValidator;

    @Inject
    private IResourceDescription.Manager resourceDescriptionManager;

    @Inject
    private OxstsQualifiedNameProvider qualifiedNameProvider;

    @Test
    void declarationOfAnotherFileOfThePackageResolvesBySimpleName() throws Exception {
        var resourceSet = resourceSetProvider.get();
        var lamps = parse(resourceSet, """
            package example::house
            class Lamp {
                var on: bool := false
            }
            """);
        var rooms = parse(resourceSet, """
            package example::house
            class Room {
                contains lamp: Lamp
            }
            """);

        var room = validate(rooms);

        room.assertNoResourceErrors();
        room.assertNoValidationErrors();
        assertThat(room.classByName("Room").featureByName("lamp").typeDomain())
                .isSameAs(lamps.classByName("Lamp").eObject());
    }

    @Test
    void declarationOfTheFileShadowsTheSameNameInAnotherFileOfThePackage() throws Exception {
        var resourceSet = resourceSetProvider.get();
        parse(resourceSet, """
            package example::house
            class Lamp {
                var on: bool := false
            }
            """);
        var rooms = parse(resourceSet, """
            package example::house
            class Lamp {
                var lit: bool := false
            }
            class Room {
                contains lamp: Lamp
            }
            """);

        var room = validate(rooms);

        room.assertNoResourceErrors();
        room.assertNoValidationErrors();
        assertThat(room.classByName("Room").featureByName("lamp").typeDomain())
                .isSameAs(room.classByName("Lamp").eObject());
    }

    @Test
    void nameDeclaredInTwoFilesOfThePackageWarnsAtTheDeclarationThatDoesNotWin() throws Exception {
        var resourceSet = resourceSetProvider.get();
        var first = parse(resourceSet, """
            package example::house
            class Lamp {
                var on: bool := false
            }
            """);
        var second = parse(resourceSet, """
            package example::house
            class Lamp {
                var lit: bool := false
            }
            """);
        var rooms = parse(resourceSet, """
            package example::house
            class Room {
                contains lamp: Lamp
            }
            """);

        var validatedFirst = validate(first);
        var validatedSecond = validate(second);
        var validatedRooms = validate(rooms);

        assertThat(validatedFirst.getIssues()).isEmpty();
        assertThat(validatedSecond.getIssues()).singleElement().satisfies(issue -> {
            assertThat(issue.getSeverity()).isEqualTo(Severity.WARNING);
            assertThat(issue.getCode()).isEqualTo(OxstsValidator.DECLARED_IN_ANOTHER_FILE);
            assertThat(issue.getLineNumber()).isEqualTo(2);
            assertThat(issue.getMessage())
                    .isEqualTo("'example::house::Lamp' is also declared in "
                            + first.getOxstsPackage().eResource().getURI().lastSegment()
                            + ", which other files of the package resolve it to.");
        });
        assertThat(validatedRooms.getIssues()).isEmpty();
        assertThat(validatedRooms.classByName("Room").featureByName("lamp").typeDomain())
                .isSameAs(first.classByName("Lamp").eObject());
    }

    @Test
    void importOfAPackageReachesTheDeclarationsOfEveryFile() throws Exception {
        var resourceSet = resourceSetProvider.get();
        var lamps = parse(resourceSet, """
            package example::house
            class Lamp {
                var on: bool := false
            }
            """);
        var doors = parse(resourceSet, """
            package example::house
            class Door {
                var open: bool := false
            }
            """);
        var street = parse(resourceSet, """
            package example::street
            import example::house
            class Street {
                contains lamp: Lamp
                contains door: Door
            }
            """);

        var validatedStreet = validate(street);

        validatedStreet.assertNoResourceErrors();
        validatedStreet.assertNoValidationErrors();
        var streetClass = validatedStreet.classByName("Street");
        assertThat(streetClass.featureByName("lamp").typeDomain())
                .isSameAs(lamps.classByName("Lamp").eObject());
        assertThat(streetClass.featureByName("door").typeDomain())
                .isSameAs(doors.classByName("Door").eObject());
    }

    @Test
    void qualifiedNameReachesTheDeclarationOfASecondFileOfThePackage() throws Exception {
        var resourceSet = resourceSetProvider.get();
        parse(resourceSet, """
            package example::house
            class Lamp {
                var on: bool := false
            }
            """);
        var doors = parse(resourceSet, """
            package example::house
            class Door {
                var open: bool := false
            }
            """);
        var street = parse(resourceSet, """
            package example::street
            class Street {
                contains door: example::house::Door
            }
            """);

        var validatedStreet = validate(street);

        validatedStreet.assertNoResourceErrors();
        assertThat(validatedStreet.classByName("Street").featureByName("door").typeDomain())
                .isSameAs(doors.classByName("Door").eObject());
    }

    @Test
    void unnamedPackageResolvesItsOwnDeclarationsBySimpleName() throws Exception {
        var resourceSet = resourceSetProvider.get();
        var snippet = parse(resourceSet, """
            enum Color {
                Red,
                Green,
            }
            class Lamp {
                var color: Color := Color::Red
            }
            class Room {
                contains lamp: Lamp
            }
            """);

        var validatedSnippet = validate(snippet);

        validatedSnippet.assertNoResourceErrors();
        validatedSnippet.assertNoValidationErrors();
        var lamp = validatedSnippet.classByName("Lamp");
        assertThat(validatedSnippet.classByName("Room").featureByName("lamp").typeDomain())
                .isSameAs(lamp.eObject());
        assertThat(((ElementReference) lamp.variableByName("color").initializer()).getElement())
                .isSameAs(validatedSnippet.enumByName("Color").literalByName("Red"));
        assertThat(qualifiedNameProvider.getFullyQualifiedNameString(lamp.eObject()))
                .isEqualTo("Lamp");
    }

    @Test
    void inlinedModelResolvesAClassOfAnUnnamedPackageBySimpleName() throws Exception {
        var resourceSet = resourceSetProvider.get();
        var snippet = parse(resourceSet, """
            @VerificationCase
            class Switch {
                var on: bool := false
            }
            """);
        var inlinedOxsts = inlinedOxstsParseHelper.parse("""
            inlined oxsts of Switch
            init {
            }
            tran {
            }
            prop {
                true
            }
            """, resourceSet);

        EcoreUtil.resolveAll(resourceSet);

        assertThat(inlinedOxsts.eResource().getErrors()).isEmpty();
        assertThat(inlinedOxsts.getClassDeclaration())
                .isSameAs(snippet.classByName("Switch").eObject());
    }

    @Test
    void unnamedPackageExportsNothingToTheIndex() throws Exception {
        var resourceSet = resourceSetProvider.get();
        var snippet = parse(resourceSet, """
            class Lamp {
                var on: bool := false
            }
            """);

        var description = resourceDescriptionManager.getResourceDescription(
                snippet.getOxstsPackage().eResource());

        assertThat(description.getExportedObjects()).isEmpty();
    }

    @Test
    void unnamedPackageIsNotVisibleFromANamedPackage() throws Exception {
        var resourceSet = resourceSetProvider.get();
        parse(resourceSet, """
            class Lamp {
                var on: bool := false
            }
            """);
        var house = parse(resourceSet, """
            package example::house
            class Room {
                contains lamp: Lamp
            }
            """);

        var validatedHouse = validate(house);

        assertThat(validatedHouse.getResourceErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getMessage()).contains("'Lamp'"));
    }

    @Test
    void unnamedPackageIsNotVisibleFromAnotherUnnamedPackage() throws Exception {
        var resourceSet = resourceSetProvider.get();
        parse(resourceSet, """
            class Lamp {
                var on: bool := false
            }
            """);
        var other = parse(resourceSet, """
            class Room {
                contains lamp: Lamp
            }
            """);

        var validatedOther = validate(other);

        assertThat(validatedOther.getResourceErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getMessage()).contains("'Lamp'"));
    }

    @Test
    void declarationOfAnUnnamedPackageWinsOverAnImport() throws Exception {
        var resourceSet = resourceSetProvider.get();
        parse(resourceSet, """
            package example::lights
            class Lamp {
                var on: bool := false
            }
            """);
        var snippet = parse(resourceSet, """
            import example::lights
            class Lamp {
                var lit: bool := false
            }
            class Room {
                contains lamp: Lamp
            }
            """);

        var validatedSnippet = validate(snippet);

        validatedSnippet.assertNoResourceErrors();
        validatedSnippet.assertNoValidationErrors();
        assertThat(validatedSnippet.classByName("Room").featureByName("lamp").typeDomain())
                .isSameAs(validatedSnippet.classByName("Lamp").eObject());
    }

    @Test
    void simpleNameDeclaredByTwoImportedPackagesIsAmbiguous() throws Exception {
        var resourceSet = resourceSetProvider.get();
        parse(resourceSet, """
            package example::lights
            class Lamp {
                var on: bool := false
            }
            """);
        parse(resourceSet, """
            package example::signals
            class Lamp {
                var lit: bool := false
            }
            """);
        var street = parse(resourceSet, """
            package example::street
            import example::lights
            import example::signals
            class Street {
                contains lamp: Lamp
            }
            """);

        var validatedStreet = validate(street);

        validatedStreet.assertNoResourceErrors();
        assertThat(validatedStreet.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.AMBIGUOUS_REFERENCE);
                    assertThat(issue.getLineNumber()).isEqualTo(5);
                    assertThat(issue.getMessage())
                            .isEqualTo("Ambiguous reference 'Lamp': it names both example::lights::Lamp and"
                                    + " example::signals::Lamp; write the qualified name example::lights::Lamp or"
                                    + " example::signals::Lamp.");
                });
    }

    @Test
    void simpleNameDeclaredByAnImportAndTheCurrentPackageIsAmbiguous() throws Exception {
        var resourceSet = resourceSetProvider.get();
        parse(resourceSet, """
            package example::lights
            class Lamp {
                var on: bool := false
            }
            """);
        parse(resourceSet, """
            package example::street
            class Lamp {
                var lit: bool := false
            }
            """);
        var street = parse(resourceSet, """
            package example::street
            import example::lights
            class Street {
                contains lamp: Lamp
            }
            """);

        var validatedStreet = validate(street);

        validatedStreet.assertHasValidationIssue(
                OxstsValidator.AMBIGUOUS_REFERENCE, "example::street::Lamp and example::lights::Lamp");
    }

    @Test
    void qualifiedReferenceResolvesTheAmbiguity() throws Exception {
        var resourceSet = resourceSetProvider.get();
        parse(resourceSet, """
            package example::lights
            class Lamp {
                var on: bool := false
            }
            """);
        var signals = parse(resourceSet, """
            package example::signals
            class Lamp {
                var lit: bool := false
            }
            """);
        var street = parse(resourceSet, """
            package example::street
            import example::lights
            import example::signals
            class Street {
                contains lamp: example::signals::Lamp
            }
            """);

        var validatedStreet = validate(street);

        validatedStreet.assertNoResourceErrors();
        validatedStreet.assertNoValidationErrors();
        assertThat(validatedStreet.classByName("Street").featureByName("lamp").typeDomain())
                .isSameAs(signals.classByName("Lamp").eObject());
    }

    private WrappedOxstsPackage parse(ResourceSet resourceSet, String text) throws Exception {
        return new WrappedOxstsPackage(parseHelper.parse(text, resourceSet), null);
    }

    private WrappedOxstsPackage validate(WrappedOxstsPackage parsedPackage) {
        var resource = parsedPackage.getOxstsPackage().eResource();
        EcoreUtil.resolveAll(resource.getResourceSet());
        var issues = resourceValidator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl);
        return new WrappedOxstsPackage(parsedPackage.getOxstsPackage(), issues);
    }
}

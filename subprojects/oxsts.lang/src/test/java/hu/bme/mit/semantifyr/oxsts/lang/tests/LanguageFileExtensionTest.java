/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.oxsts.lang.library.ClasspathBasedOxstsLibrary;
import hu.bme.mit.semantifyr.oxsts.lang.library.PathBasedOxstsLibrary;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.resource.FileExtensionProvider;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@InjectWithOxsts
public class LanguageFileExtensionTest {

    @Inject
    private FileExtensionProvider fileExtensionProvider;

    @Inject
    private ParseHelper<OxstsModelPackage> parseHelper;

    @Inject
    private Provider<XtextResourceSet> resourceSetProvider;

    @Inject
    private IResourceValidator resourceValidator;

    @Inject
    private Injector injector;

    @TempDir
    private Path temporaryDirectory;

    @Test
    void theLanguageHasThreeExtensionsAndTheFirstIsPrimary() {
        assertThat(fileExtensionProvider.getFileExtensions()).containsExactly("semantifyr", "sem", "oxsts");
        assertThat(fileExtensionProvider.getPrimaryFileExtension()).isEqualTo("semantifyr");
    }

    @Test
    void aFileOfEveryExtensionParsesAndValidates() throws Exception {
        for (var extension : fileExtensionProvider.getFileExtensions()) {
            var resourceSet = resourceSetProvider.get();

            var parsed = parseHelper.parse("""
                    package example::house
                    class Lamp {
                        var on: bool := false
                    }
                    """, URI.createURI("file:///workspace/lamp." + extension), resourceSet);

            assertThat(parsed.eResource().getErrors()).as(extension).isEmpty();
            var issues = resourceValidator.validate(parsed.eResource(), CheckMode.ALL, CancelIndicator.NullImpl);
            assertThat(issues).as(extension).isEmpty();
        }
    }

    @Test
    void anImportResolvesBetweenFilesOfAnyTwoExtensions() throws Exception {
        for (var declaringExtension : fileExtensionProvider.getFileExtensions()) {
            for (var importingExtension : fileExtensionProvider.getFileExtensions()) {
                var resourceSet = resourceSetProvider.get();
                parseHelper.parse("""
                        package example::house
                        class Lamp {
                            var on: bool := false
                        }
                        """, URI.createURI("file:///workspace/lamp." + declaringExtension), resourceSet);
                var street = parseHelper.parse(
                        """
                        package example::street
                        import example::house
                        class Street {
                            contains lamp: Lamp
                        }
                        """, URI.createURI("file:///workspace/street." + importingExtension), resourceSet);

                EcoreUtil.resolveAll(resourceSet);
                var issues = resourceValidator.validate(street.eResource(), CheckMode.ALL, CancelIndicator.NullImpl);

                assertThat(issues)
                        .as(declaringExtension + " into " + importingExtension)
                        .isEmpty();
            }
        }
    }

    @Test
    void aLibraryFolderContributesTheFilesOfEveryExtension() throws Exception {
        var library = new PathBasedOxstsLibrary(fileExtensionProvider) {
            @Override
            protected Path getLibraryPath() {
                return temporaryDirectory;
            }

            @Override
            public Iterable<URI> getImplicitImports() {
                return List.of();
            }
        };
        injector.injectMembers(library);
        writeLibraryFiles(temporaryDirectory);
        Files.writeString(temporaryDirectory.resolve("notes.txt"), "not a model");
        var resourceSet = resourceSetProvider.get();

        library.loadLibrary(resourceSet);

        assertThat(resourceSet.getResources())
                .extracting(resource -> resource.getURI().lastSegment())
                .containsAll(libraryFileNames())
                .doesNotContain("notes.txt");
    }

    @Test
    void aClasspathLibraryContributesTheFilesOfEveryExtension() throws Exception {
        var classes = Files.createDirectory(temporaryDirectory.resolve("classes"));
        writeLibraryFiles(Files.createDirectories(classes.resolve("library")));
        Files.writeString(classes.resolve("library").resolve("notes.txt"), "not a model");

        try (var classLoader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, null)) {
            var library = new ClasspathBasedOxstsLibrary(
                    fileExtensionProvider, classLoader, "library", temporaryDirectory.resolve("extracted"));
            injector.injectMembers(library);
            var resourceSet = resourceSetProvider.get();

            library.loadLibrary(resourceSet);

            assertThat(resourceSet.getResources())
                    .extracting(resource -> resource.getURI().lastSegment())
                    .containsAll(libraryFileNames())
                    .doesNotContain("notes.txt");
        }
    }

    private void writeLibraryFiles(Path directory) throws Exception {
        for (var extension : fileExtensionProvider.getFileExtensions()) {
            Files.writeString(directory.resolve("library_" + extension + "." + extension), """
                    package library::from_%s
                    class Element
                    """.formatted(extension));
        }
    }

    private Iterable<String> libraryFileNames() {
        return fileExtensionProvider.getFileExtensions().stream()
                .map(extension -> "library_" + extension + "." + extension)
                .toList();
    }
}

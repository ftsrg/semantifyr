/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.formatting;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsIdeSetup;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsFactory;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.formatting2.regionaccess.ITextReplacement;
import org.eclipse.xtext.ide.serializer.IChangeSerializer;
import org.eclipse.xtext.ide.serializer.IEmfResourceChange;
import org.eclipse.xtext.ide.serializer.ITextDocumentChange;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InsertedMemberFormattingTest {

    private static Injector injector;

    @BeforeAll
    static void createInjector() {
        injector = new OxstsIdeSetup().createInjectorAndDoEMFRegistration();
    }

    @Test
    void aMemberInsertedIntoAnEmptyClassIsIndented() throws IOException {
        var text = """
                package example::shapes

                class Shape {
                }

                class Square : Shape {
                }
                """;
        var resourceSet = injector.getInstance(XtextResourceSet.class);
        var resource = resourceSet.createResource(URI.createURI("shapes.oxsts"));
        resource.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), Map.of());
        var square =
                (ClassDeclaration) ((OxstsModelPackage) resource.getContents().getFirst())
                        .getDeclarations()
                        .getLast();

        var changeSerializer = injector.getInstance(IChangeSerializer.class);
        changeSerializer.addModification(square, inserting -> {
            var draw = OxstsFactory.eINSTANCE.createTransitionDeclaration();
            draw.setAnnotation(OxstsFactory.eINSTANCE.createAnnotationContainer());
            draw.setKind(TransitionKind.TRAN);
            draw.setName("draw");
            draw.getBranches().add(OxstsFactory.eINSTANCE.createSequenceOperation());
            inserting.getMembers().add(draw);
        });
        var changes = new ArrayList<IEmfResourceChange>();
        changeSerializer.applyModifications(changes::add);

        assertThat(applyReplacements(text, changes)).isEqualTo("""
                package example::shapes

                class Shape {
                }

                class Square : Shape {
                    tran draw() {
                    }
                }
                """);
    }

    private static String applyReplacements(String text, Iterable<IEmfResourceChange> changes) {
        var replacements = new ArrayList<ITextReplacement>();
        for (var change : changes) {
            if (change instanceof ITextDocumentChange textChange) {
                replacements.addAll(textChange.getReplacements());
            }
        }
        replacements.sort(Comparator.comparingInt(ITextReplacement::getOffset).reversed());
        var result = new StringBuilder(text);
        for (var replacement : replacements) {
            result.replace(replacement.getOffset(), replacement.getEndOffset(), replacement.getReplacementText());
        }
        return result.toString();
    }
}

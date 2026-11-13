/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.website;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.validation.InternalIssue;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.EnumLiteralDeclaration;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.IGrammarAccess;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ReferenceCompletenessTest {

    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z ]*");

    private static final Pattern ISSUE_CODE = Pattern.compile("OXSTS\\.[A-Z_]+");

    private static final String ISSUE_PREFIX = "OXSTS.";

    @Inject
    private IGrammarAccess grammarAccess;

    @Inject
    private ParseHelper<EObject> parseHelper;

    @Inject
    private Provider<XtextResourceSet> resourceSetProvider;

    @Test
    void everyKeywordIsInACheckedExample() throws Exception {
        var keywords = new TreeSet<String>();
        var grammar = grammarAccess.getGrammar();
        var rules = new ArrayList<EObject>();
        rules.addAll(GrammarUtil.allParserRules(grammar));
        rules.addAll(GrammarUtil.allEnumRules(grammar));
        for (var rule : rules) {
            for (var keyword : EcoreUtil2.eAllOfType(rule, Keyword.class)) {
                if (WORD.matcher(keyword.getValue()).matches()) {
                    keywords.add(keyword.getValue());
                }
            }
        }

        var examples = new ArrayList<String>();
        for (var fence : DocumentationFences.allFences()) {
            if (!DocumentationFences.SYNTAX.equals(fence.issue())) {
                examples.add(fence.text());
            }
        }
        for (var model : DocumentationFences.importedModels()) {
            examples.add(Files.readString(model));
        }
        var shown = new TreeSet<String>();
        for (var example : examples) {
            var parsed = parseHelper.parse(example, resourceSetProvider.get());
            for (var leaf : NodeModelUtils.getNode(parsed).getLeafNodes()) {
                if (leaf.getGrammarElement() instanceof Keyword keyword) {
                    shown.add(keyword.getValue());
                } else if (leaf.getGrammarElement() instanceof EnumLiteralDeclaration literal) {
                    shown.add(literal.getLiteral().getValue());
                }
            }
        }

        keywords.removeAll(shown);
        assertThat(keywords)
                .as("keywords of the grammar in no checked oxsts fence and no imported model")
                .isEmpty();
    }

    @Test
    void everyIssueCodeIsOnAPageOrInternal() throws Exception {
        var codes = new TreeSet<String>();
        for (var field : OxstsValidator.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && field.get(null) instanceof String code
                    && code.startsWith(ISSUE_PREFIX)
                    && !field.isAnnotationPresent(InternalIssue.class)) {
                codes.add(code);
            }
        }

        var shown = new TreeSet<String>();
        var texts = new ArrayList<String>();
        for (var page : DocumentationFences.pages()) {
            texts.add(Files.readString(page));
        }
        for (var model : DocumentationFences.importedModels()) {
            texts.add(Files.readString(model));
        }
        for (var text : texts) {
            var matcher = ISSUE_CODE.matcher(text);
            while (matcher.find()) {
                shown.add(matcher.group());
            }
        }

        codes.removeAll(shown);
        assertThat(codes)
                .as("issue codes of the validator on no page and not marked @InternalIssue where they are declared")
                .isEmpty();
    }
}

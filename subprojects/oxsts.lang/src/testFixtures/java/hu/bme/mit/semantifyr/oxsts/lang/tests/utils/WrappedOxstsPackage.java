/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.Issue;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public record WrappedOxstsPackage(OxstsModelPackage oxstsModelPackage, List<Issue> issues) {

    public OxstsModelPackage getOxstsPackage() {
        return oxstsModelPackage;
    }

    public List<Resource.Diagnostic> getResourceErrors() {
        return oxstsModelPackage.eResource().getErrors();
    }

    public List<Issue> getIssues() {
        return issues;
    }

    public List<WrappedClass> classes() {
        return oxstsModelPackage.getDeclarations().stream()
            .filter(ClassDeclaration.class::isInstance)
            .map(d -> new WrappedClass((ClassDeclaration) d))
            .toList();
    }

    public WrappedClass classByName(String name) {
        return classes().stream()
            .filter(c -> name.equals(c.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No class named '" + name + "' in package " + oxstsModelPackage.getName()));
    }

    public List<WrappedEnum> enums() {
        return oxstsModelPackage.getDeclarations().stream()
            .filter(EnumDeclaration.class::isInstance)
            .map(d -> new WrappedEnum((EnumDeclaration) d))
            .toList();
    }

    public WrappedEnum enumByName(String name) {
        return enums().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No enum named '" + name + "' in package " + oxstsModelPackage.getName()));
    }

    public List<WrappedFeature> globalFeatures() {
        return oxstsModelPackage.getDeclarations().stream()
            .filter(FeatureDeclaration.class::isInstance)
            .map(d -> new WrappedFeature((FeatureDeclaration) d))
            .toList();
    }

    public WrappedFeature globalFeatureByName(String name) {
        return globalFeatures().stream()
            .filter(f -> name.equals(f.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No global feature named '" + name + "' in package " + oxstsModelPackage.getName()));
    }

    public List<WrappedDataType> externDatatypes() {
        return oxstsModelPackage.getDeclarations().stream()
            .filter(DataTypeDeclaration.class::isInstance)
            .map(d -> new WrappedDataType((DataTypeDeclaration) d))
            .toList();
    }

    public void assertNoResourceErrors() {
        assertThat(getResourceErrors())
            .as("package should have no parse/link errors: " + summariseResourceErrors())
            .isEmpty();
    }

    public void assertNoValidationIssues() {
        assertThat(issues)
            .as("package should have no validation issues: " + summariseIssues())
            .isEmpty();
    }

    public void assertNoValidationErrors() {
        List<Issue> errors = issues.stream().filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(errors)
            .as("package should have no validation errors: " + errors.stream().map(Issue::getMessage).collect(Collectors.joining("\n  ")))
            .isEmpty();
    }

    public void assertHasValidationIssue(String issueCode) {
        assertHasValidationIssue(issueCode, null);
    }

    public void assertHasValidationIssue(String issueCode, String messageSubstring) {
        List<Issue> matching = issues.stream()
            .filter(i -> issueCode.equals(i.getCode()))
            .filter(i -> messageSubstring == null || (i.getMessage() != null && i.getMessage().contains(messageSubstring)))
            .toList();
        assertThat(matching)
            .as("expected at least one issue with code '" + issueCode + "'"
                + (messageSubstring != null ? " and message containing '" + messageSubstring + "'" : "")
                + ", got: " + summariseIssues())
            .isNotEmpty();
    }

    private String summariseResourceErrors() {
        return getResourceErrors().stream()
            .map(d -> d.getLocation() + ":" + d.getLine() + ": " + d.getMessage())
            .collect(Collectors.joining("\n  ", "\n  ", ""));
    }

    private String summariseIssues() {
        return issues.stream()
            .map(i -> i.getSeverity() + " " + i.getCode() + ": " + i.getMessage())
            .collect(Collectors.joining("\n  ", "\n  ", ""));
    }
}

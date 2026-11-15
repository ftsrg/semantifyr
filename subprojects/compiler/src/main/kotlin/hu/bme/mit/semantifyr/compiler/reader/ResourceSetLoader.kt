/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.reader

import com.google.inject.Inject
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.diagnostics.Severity
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode
import org.eclipse.xtext.validation.IResourceValidator
import org.eclipse.xtext.validation.Issue

class ResourceSetLoader @Inject constructor(
    private val resourceValidator: IResourceValidator,
) {

    private val logger by loggerFactory()

    fun resolveAllAndValidate(resourceSet: ResourceSet) {
        EcoreUtil2.resolveAll(resourceSet)
        for (resource in resourceSet.resources) {
            validateResource(resource)
        }
    }

    fun validateResource(resource: Resource) {
        val issues = resourceValidator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl)
        val fileLabel = resource.uri.toFileString() ?: resource.uri.toString()

        if (resource.errors.any()) {
            val parseErrors = resource.errors.joinToString("\n  ") {
                "$fileLabel:${it.line}:${it.column}: ${it.message}"
            }
            error("$fileLabel does not parse:\n  $parseErrors")
        }
        for (warning in resource.warnings) {
            logger.warn { "$fileLabel:${warning.line}:${warning.column}: ${warning.message}" }
        }
        // an error reaches the user once, in the exception, so the log keeps it below the default level
        for (issue in issues) {
            when (issue.severity) {
                Severity.WARNING -> {
                    logger.warn { issueLine(issue, fileLabel) }
                }
                Severity.ERROR -> {
                    logger.debug { issueLine(issue, fileLabel) }
                }
                Severity.INFO -> {
                    logger.info { issueLine(issue, fileLabel) }
                }
                Severity.IGNORE -> {}
            }
        }

        val errors = issues.filter {
            it.severity == Severity.ERROR
        }
        if (errors.isNotEmpty()) {
            val errorLines = errors.joinToString("\n  ") {
                issueLine(it, fileLabel)
            }
            error("$fileLabel does not validate:\n  $errorLines")
        }
    }

    private fun issueLine(issue: Issue, fileLabel: String): String {
        val location = issue.uriToProblem?.toFileString() ?: fileLabel
        val code = issue.code?.let {
            " [$it]"
        } ?: ""
        return "$location:${issue.lineNumber}:${issue.column}: ${issue.message}$code"
    }

}

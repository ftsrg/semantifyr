/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.reader

import com.google.inject.Inject
import hu.bme.mit.semantifyr.logging.error
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
            val parseErrorSummary = resource.errors.joinToString("\n  ") {
                "$fileLabel:${it.line}:${it.column}: ${it.message}"
            }
            error("Parse errors in $fileLabel:\n  $parseErrorSummary")
        }
        if (resource.warnings.any()) {
            logger.warn { "Warnings found in file $fileLabel" }

            for (warning in resource.warnings) {
                logger.warn(warning.message)
            }
        }
        if (issues.any()) {
            logger.info { "Issues found in file $fileLabel" }

            for (issue in issues) {
                when (issue.severity) {
                    Severity.INFO -> {
                        logger.info { "${issue.uriToProblem.toFileString()}[${issue.lineNumber}:${issue.column}] ${issue.message}" }
                    }
                    Severity.WARNING -> {
                        logger.warn { "${issue.uriToProblem.toFileString()}[${issue.lineNumber}:${issue.column}] ${issue.message}" }
                    }
                    Severity.ERROR -> {
                        logger.error { "${issue.uriToProblem.toFileString()}[${issue.lineNumber}:${issue.column}] ${issue.message}" }
                    }
                    Severity.IGNORE -> {}
                }
            }

            val blockingIssues = issues.filter {
                it.severity == Severity.ERROR
            }
            if (blockingIssues.isNotEmpty()) {
                val summary = blockingIssues.joinToString("\n  ") {
                    val loc = it.uriToProblem?.toFileString() ?: fileLabel
                    "$loc:${it.lineNumber}:${it.column}: ${it.severity} ${it.code ?: ""} ${it.message}".trim()
                }
                error(
                    "${blockingIssues.size} validation issue(s) in $fileLabel:\n  $summary",
                )
            }
        }
    }

}

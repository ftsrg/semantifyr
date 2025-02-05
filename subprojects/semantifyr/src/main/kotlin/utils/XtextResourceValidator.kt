/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.utils

import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.diagnostics.Severity
import org.eclipse.xtext.resource.XtextResource
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode

object XtextResourceValidator {
    val logger by loggerFactory()

    fun validateAndLoadResource(resource: Resource) {
        resource.load(emptyMap<Any, Any>())

        if (resource.errors.any()) {
            logger.error { "Errors found in file (${resource.uri.toFileString()})" }

            for (error in resource.errors) {
                logger.error(error.message)
            }

            error("Errors found in file (${resource.uri.toFileString()})}")
        }
        if (resource.warnings.any()) {
            logger.warn { "Warnings found in file (${resource.uri.toFileString()})" }

            for (warning in resource.warnings) {
                logger.warn(warning.message)
            }
        }
        val validator = (resource as XtextResource).resourceServiceProvider.resourceValidator
        val issues = validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl)
        if (issues.any()) {
            logger.info { "Issues found in file (${resource.uri.toFileString()})" }

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

                    else -> {}
                }
            }

            if (issues.any { it.severity == Severity.ERROR || it.severity == Severity.WARNING }) {
                error("Issues found in file!")
            }
        }
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.document

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.exceptions.WorkspaceUriException
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServices
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionContext
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionScoped
import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.xtext.diagnostics.Severity
import org.eclipse.xtext.findReferences.IReferenceFinder
import org.eclipse.xtext.resource.IResourceDescriptions
import org.eclipse.xtext.resource.XtextResource
import org.eclipse.xtext.resource.impl.ResourceDescriptionsData
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.util.concurrent.IUnitOfWork
import org.eclipse.xtext.validation.CheckMode
import org.eclipse.xtext.validation.Issue
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import org.eclipse.emf.common.util.URI as EmfURI

private const val CLIENT_PREFIX = "file:///workspace/"

@SessionScoped
class SessionDocumentManager @Inject constructor(
    private val languageServices: LanguageServices,
    sessionContext: SessionContext,
) {

    private val logger by loggerFactory()

    val workspaceRoot = sessionContext.workingDirectoryPath.toAbsolutePath().normalize()

    private val serverPrefix = ensureTrailingSlash(sessionContext.workingDirectoryPath.toUri().toString())

    private val resourceSet = languageServices.newResourceSet()

    private val openDocuments = ConcurrentHashMap<String, SessionDocument>()

    fun findByClient(clientUri: String): SessionDocument? {
        return openDocuments[clientToServer(clientUri)]
    }

    fun findByServer(serverUri: String): SessionDocument? {
        return openDocuments[serverUri]
    }

    fun find(uri: String): SessionDocument? {
        return findByServer(uri) ?: findByClient(uri)
    }

    fun openByClient(clientUri: String, text: String): SessionDocument {
        return openByServer(clientToServer(clientUri), text)
    }

    fun openByServer(serverUri: String, text: String): SessionDocument {
        val diskPath = resolveWithinWorkspace(serverUri)
        diskPath.toFile().parentFile.mkdirs()
        openDocuments.remove(serverUri)?.let {
            it.unload()
            resourceSet.resources.remove(it.resource)
        }
        val emfUri = EmfURI.createURI(serverUri)
        val resource = resourceSet.createResource(emfUri) as XtextResource
        val document = SessionDocument(serverUri, emfUri, resource, diskPath, text)
        openDocuments[serverUri] = document
        return document
    }

    fun openExistingByClient(clientUri: String): SessionDocument {
        return openExistingByServer(clientToServer(clientUri))
    }

    fun openExistingByServer(serverUri: String): SessionDocument {
        val diskPath = resolveWithinWorkspace(serverUri)
        require(Files.isRegularFile(diskPath)) {
            "Cannot open document, file does not exist: $diskPath"
        }
        return openByServer(serverUri, Files.readString(diskPath))
    }

    fun existsOnDisk(clientUri: String): Boolean {
        val diskPath = diskPathOrNull(clientUri) ?: return false
        return Files.isRegularFile(diskPath)
    }

    fun readDocumentText(clientUri: String): String? {
        find(clientUri)?.let {
            return it.text()
        }
        val diskPath = diskPathOrNull(clientUri) ?: return null
        return if (Files.isRegularFile(diskPath)) {
            Files.readString(diskPath)
        } else {
            null
        }
    }

    fun closeByClient(clientUri: String): Boolean {
        val serverUri = clientToServer(clientUri)
        val document = openDocuments.remove(serverUri) ?: return false
        document.unload()
        resourceSet.resources.remove(document.resource)
        return true
    }

    fun flushAllToDisk() {
        for (document in openDocuments.values.toList()) {
            document.flushToDisk()
        }
    }

    fun validateAll(client: LanguageClient, cancelIndicator: CancelIndicator) {
        for (document in openDocuments.values.toList()) {
            validateAndPublish(document, client, cancelIndicator)
        }
    }

    fun loadLibraryDirectory(libraryDir: Path) {
        require(Files.isDirectory(libraryDir)) {
            "Library directory does not exist: $libraryDir"
        }
        Files.walk(libraryDir).use {
            it.filter {
                Files.isRegularFile(it)
            }.forEach {
                loadLibraryFile(it)
            }
        }
    }

    fun referenceResourceAccess(): IReferenceFinder.IResourceAccess {
        return object : IReferenceFinder.IResourceAccess {
            override fun <R> readOnly(
                targetURI: EmfURI,
                work: IUnitOfWork<R, ResourceSet>,
            ): R {
                return work.exec(resourceSet)
            }
        }
    }

    fun referenceIndex(): IResourceDescriptions {
        val descriptions = resourceSet.resources.mapNotNull { resource ->
            val provider = languageServices.resourceServiceProviderRegistry.getResourceServiceProvider(resource.uri)
                ?: return@mapNotNull null
            provider.resourceDescriptionManager?.getResourceDescription(resource)
        }
        return ResourceDescriptionsData(descriptions)
    }

    fun serverToClient(serverUri: String): String {
        return serverUri.replace(serverPrefix, CLIENT_PREFIX)
    }

    fun toClientUri(serverUri: String): String {
        val path = fileUriToPathOrNull(serverUri) ?: return serverUri
        val normalized = path.toAbsolutePath().normalize()
        if (!normalized.startsWith(workspaceRoot)) {
            return serverUri
        }
        val relative = workspaceRoot.relativize(normalized).joinToString("/")
        return "$CLIENT_PREFIX$relative"
    }

    fun unloadAll() {
        for (document in openDocuments.values.toList()) {
            document.unload()
        }
        openDocuments.clear()
        try {
            for (resource in resourceSet.resources.toList()) {
                resource.unload()
            }
            resourceSet.resources.clear()
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to clear resource set" }
        }
    }

    private fun loadLibraryFile(path: Path) {
        val emfUri = EmfURI.createFileURI(path.toAbsolutePath().toString())
        if (resourceSet.getResource(emfUri, false) == null) {
            resourceSet.createResource(emfUri)?.load(emptyMap<Any, Any>())
        }
    }

    private fun clientToServer(clientUri: String): String {
        return clientUri.replace(CLIENT_PREFIX, serverPrefix)
    }

    private fun diskPathOrNull(clientUri: String): Path? {
        return try {
            resolveWithinWorkspace(clientToServer(clientUri))
        } catch (_: WorkspaceUriException) {
            null
        }
    }

    private fun fileUriToPathOrNull(uri: String): Path? {
        return try {
            when {
                uri.startsWith("file://") -> Path.of(URI.create(uri))
                uri.startsWith("file:/") -> Path.of(URI.create(uri).schemeSpecificPart)
                uri.startsWith("/") -> Path.of(uri)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveWithinWorkspace(serverUri: String): Path {
        val uri = try {
            URI.create(serverUri)
        } catch (e: IllegalArgumentException) {
            throw WorkspaceUriException("malformed uri: $serverUri", e)
        }
        if (uri.scheme != "file") {
            throw WorkspaceUriException("non-file uri: $serverUri")
        }
        val candidate = try {
            Path.of(uri).toAbsolutePath().normalize()
        } catch (e: Exception) {
            throw WorkspaceUriException("uri not resolvable: $serverUri", e)
        }
        if (!candidate.startsWith(workspaceRoot)) {
            throw WorkspaceUriException("uri escapes workspace: $serverUri")
        }
        return candidate
    }

    private fun validateAndPublish(
        document: SessionDocument,
        client: LanguageClient,
        cancelIndicator: CancelIndicator,
    ) {
        try {
            val provider = languageServices.resourceServiceProviderRegistry.getResourceServiceProvider(document.emfUri)
            if (provider == null) {
                logger.warn { "No IResourceServiceProvider for ${document.uri}" }
                return
            }
            val issues = provider.resourceValidator.validate(document.resource, CheckMode.ALL, cancelIndicator)
            val publishUri = serverToClient(document.uri)
            val diagnostics = PublishDiagnosticsParams(publishUri, issues.map { toDiagnostic(it) })
            client.publishDiagnostics(diagnostics)
        } catch (e: Throwable) {
            logger.error(e) { "Validation failed uri=${document.uri}" }
        }
    }

    private fun toDiagnostic(issue: Issue): Diagnostic {
        val startLine = (issue.lineNumber ?: 1) - 1
        val startColumn = (issue.column ?: 1) - 1
        val endLine = (issue.lineNumberEnd ?: issue.lineNumber ?: 1) - 1
        val endColumn = (issue.columnEnd ?: issue.column ?: 1) - 1
        return Diagnostic(
            Range(Position(startLine, startColumn), Position(endLine, endColumn)),
            issue.message ?: "",
            issueSeverity(issue.severity),
            "semantifyr",
        )
    }

    private fun issueSeverity(severity: Severity): DiagnosticSeverity {
        return when (severity) {
            Severity.ERROR -> DiagnosticSeverity.Error
            Severity.WARNING -> DiagnosticSeverity.Warning
            Severity.INFO -> DiagnosticSeverity.Information
            else -> DiagnosticSeverity.Hint
        }
    }

    private fun ensureTrailingSlash(uri: String): String {
        return if (uri.endsWith("/")) {
            uri
        } else {
            "$uri/"
        }
    }
}

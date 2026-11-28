/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import hu.bme.mit.semantifyr.frontends.gamma.lang.ide.GammaIdeSetup;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson;
import hu.bme.mit.semantifyr.lang.ide.server.wire.CompiledOxstsResult;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseSpecification;
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationRequest;
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationResult;
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationStatus;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup;
import hu.bme.mit.semantifyr.portfolios.Portfolios;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandCapabilities;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GammaTraceLanguageServerTest {

    private static final Path SMOKE_MODEL = Path.of("..", "models", "smoke", "smoke.gamma");

    @TempDir
    private Path workspace;

    private final DiagnosticsCollector client = new DiagnosticsCollector();

    private LanguageServerImpl languageServer;

    @BeforeEach
    void startLanguageServer() {
        var oxstsInjector = new OxstsStandaloneSetup().createInjectorAndDoEMFRegistration();
        var injector = new GammaIdeSetup(oxstsInjector).createInjectorAndDoEMFRegistration();
        languageServer = injector.getInstance(LanguageServerImpl.class);
        languageServer.connect(client);
    }

    @AfterEach
    void stopLanguageServer() throws Exception {
        languageServer.shutdown().get(1, TimeUnit.MINUTES);
    }

    @Test
    void aValidationNamingNoWitnessReturnsAnErroredResult() throws Exception {
        var location = smokeCase("OnReachable");

        var result = (WitnessValidationResult) executeCommand(
                "gamma.witness.validate",
                CommandGson.INSTANCE.toJsonTree(VerificationCaseRequest.fromLocation(location, null)));

        assertThat(result.status()).isEqualTo(WitnessValidationStatus.ERRORED);
        assertThat(result.report()).isNull();
        assertThat(result.message()).isEqualTo("The validation of case smoke.OnReachable names no witness");
        assertThat(result.portfolioId())
                .isEqualTo(Portfolios.INSTANCE.getDefault().getId());
    }

    @Test
    void aValidationNamingAFileOutsideTheDirectoryOfItsCaseReturnsAnErroredResult() throws Exception {
        var location = smokeCase("OnReachable");

        var result = (WitnessValidationResult) executeCommand(
                "gamma.witness.validate",
                CommandGson.INSTANCE.toJsonTree(
                        new WitnessValidationRequest(location.getUri(), location.getRange(), null, location.getUri())));

        assertThat(result.status()).isEqualTo(WitnessValidationStatus.ERRORED);
        assertThat(result.report()).isNull();
    }

    private Location smokeCase(String name) throws Exception {
        var model = copy(SMOKE_MODEL, "smoke.gamma");
        initialize();
        client.awaitDiagnostics(model);
        var cases = (List<?>) executeCommand("gamma.case.discover", new JsonPrimitive(uri(model)));
        return cases.stream()
                .map(VerificationCaseSpecification.class::cast)
                .filter(it -> it.label().equals(name))
                .findFirst()
                .orElseThrow()
                .location();
    }

    @Test
    void theCompiledOxstsOfAModelIsItsOxstsText() throws Exception {
        var model = copy(SMOKE_MODEL, "smoke.gamma");
        initialize();
        client.awaitDiagnostics(model);

        var result = (CompiledOxstsResult) executeCommand("gamma.compiled.oxsts", new JsonPrimitive(uri(model)));

        assertThat(result.status()).isEqualTo(CompiledOxstsResult.COMPILED);
        assertThat(result.oxsts()).startsWith("package _smoke");
        assertThat(result.message()).isNull();
    }

    @Test
    void theCompiledOxstsOfAModelWithASyntaxErrorIsAnErroredResult() throws Exception {
        initialize();
        var model = workspace.resolve("Broken.gamma");
        languageServer.didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem(uri(model), "gamma", 1, "package Broken\n\n} component ;; {\n")));
        client.awaitDiagnostics(model);

        var result = (CompiledOxstsResult) executeCommand("gamma.compiled.oxsts", new JsonPrimitive(uri(model)));

        assertThat(result.status()).isEqualTo(CompiledOxstsResult.ERRORED);
        assertThat(result.oxsts()).isNull();
        assertThat(result.message()).startsWith("The model has a syntax error");
    }

    private Object executeCommand(String command, Object argument) throws Exception {
        return languageServer
                .executeCommand(new ExecuteCommandParams(command, List.of(argument)))
                .get(1, TimeUnit.MINUTES);
    }

    private void initialize() throws Exception {
        var params = new InitializeParams();
        params.setRootUri(uri(workspace));
        params.setInitializationOptions(JsonParser.parseString("{\"artifacts\": {\"location\": \"workspace\"}}"));
        var workspaceCapabilities = new WorkspaceClientCapabilities();
        workspaceCapabilities.setExecuteCommand(new ExecuteCommandCapabilities());
        params.setCapabilities(new ClientCapabilities(workspaceCapabilities, null, null));
        languageServer.initialize(params).get(1, TimeUnit.MINUTES);
        languageServer.initialized(new InitializedParams());
    }

    private Path copy(Path source, String name) throws Exception {
        return Files.copy(source, workspace.resolve(name));
    }

    private static String uri(Path path) {
        return path.toAbsolutePath().toUri().toString();
    }

    private static final class DiagnosticsCollector implements LanguageClient {

        private final Map<String, BlockingQueue<List<Diagnostic>>> diagnostics = new ConcurrentHashMap<>();

        void awaitDiagnostics(Path path) throws Exception {
            var published = queue(path.toAbsolutePath().toUri().getPath()).poll(1, TimeUnit.MINUTES);
            assertThat(published)
                    .describedAs("Diagnostics published for %s", path)
                    .isNotNull();
        }

        private BlockingQueue<List<Diagnostic>> queue(String path) {
            return diagnostics.computeIfAbsent(path, it -> new LinkedBlockingQueue<>());
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams params) {
            queue(URI.create(params.getUri()).getPath()).add(params.getDiagnostics());
        }

        @Override
        public void telemetryEvent(Object object) {}

        @Override
        public void showMessage(MessageParams messageParams) {}

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {}

        @Override
        public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void notifyProgress(ProgressParams params) {}
    }
}

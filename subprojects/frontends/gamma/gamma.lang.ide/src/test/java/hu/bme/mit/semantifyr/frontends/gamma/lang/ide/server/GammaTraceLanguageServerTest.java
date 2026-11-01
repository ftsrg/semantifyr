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
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseSpecification;
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationResult;
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationStatus;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup;
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
import java.util.function.Predicate;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandCapabilities;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GammaTraceLanguageServerTest {

    private static final Path MODELS = Path.of("..", "models");

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
    void aTraceNextToItsModelLinksAgainstTheModelInTheIndex() throws Exception {
        var model = copy(MODELS.resolve("examples/Crossroads.gamma"), "Crossroads.gamma");
        var trace =
                copy(MODELS.resolve("etc/crossroads-ACanBeGreen.trace.gamma"), "Crossroads-ACanBeGreen.trace.gamma");

        initialize();

        assertThat(errors(client.awaitDiagnostics(model))).isEmpty();
        assertThat(errors(client.awaitDiagnostics(trace))).isEmpty();
    }

    @Test
    void anOpenedTraceLinksAgainstTheModelInTheIndex() throws Exception {
        copy(MODELS.resolve("examples/Crossroads.gamma"), "Crossroads.gamma");
        initialize();
        var trace = workspace.resolve("trace.gamma");
        var text = Files.readString(MODELS.resolve("etc/crossroads-ACanBeGreen.trace.gamma"));

        languageServer.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri(trace), "gamma", 1, text)));

        assertThat(errors(client.awaitDiagnostics(trace))).isEmpty();
    }

    @Test
    void aTraceFollowsTheCaseItTracesWhenTheModelChanges() throws Exception {
        var model = copy(MODELS.resolve("examples/Crossroads.gamma"), "Crossroads.gamma");
        var trace =
                copy(MODELS.resolve("etc/crossroads-ACanBeGreen.trace.gamma"), "Crossroads-ACanBeGreen.trace.gamma");
        initialize();
        client.awaitDiagnostics(model);
        client.awaitDiagnostics(trace);
        var text = Files.readString(model);
        languageServer.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri(model), "gamma", 1, text)));
        client.awaitDiagnostics(model);

        change(model, 2, text.replace("case ACanBeGreen", "case ACanBeGreenRenamed"));
        client.awaitDiagnostics(trace, it -> !errors(it).isEmpty());

        change(model, 3, text);
        client.awaitDiagnostics(trace, it -> errors(it).isEmpty());
    }

    @Test
    void aTraceOfAnUnknownCaseReportsAnError() throws Exception {
        copy(MODELS.resolve("examples/Crossroads.gamma"), "Crossroads.gamma");
        var trace = workspace.resolve("Unknown.trace.gamma");
        Files.writeString(trace, """
                package Crossroads

                trace UnknownTrace of NoSuchCase {
                }
                """);

        initialize();

        assertThat(errors(client.awaitDiagnostics(trace))).isNotEmpty();
    }

    @Test
    void validatingTheWitnessOfACaseNotVerifiedYetReturnsAnErroredResult() throws Exception {
        var model = copy(MODELS.resolve("examples/Crossroads.gamma"), "Crossroads.gamma");
        initialize();
        client.awaitDiagnostics(model);
        var cases = (List<?>) executeCommand("gamma.case.discover", new JsonPrimitive(uri(model)));
        var location = cases.stream()
                .map(VerificationCaseSpecification.class::cast)
                .filter(it -> it.label().equals("ACanBeGreen"))
                .findFirst()
                .orElseThrow()
                .location();

        var result = (WitnessValidationResult) executeCommand(
                "gamma.witness.validate",
                CommandGson.INSTANCE.toJsonTree(VerificationCaseRequest.fromLocation(location, null)));

        assertThat(result.status()).isEqualTo(WitnessValidationStatus.ERRORED);
        assertThat(result.verdict()).isNull();
        assertThat(result.message()).isEqualTo("Case Crossroads.ACanBeGreen has no witness yet, verify it first");
        assertThat(result.portfolioId()).isEqualTo(ServerSettings.DEFAULT_PORTFOLIO_ID);
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

    private void change(Path path, int version, String text) {
        languageServer.didChange(new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri(path), version),
                List.of(new TextDocumentContentChangeEvent(text))));
    }

    private Path copy(Path source, String name) throws Exception {
        return Files.copy(source, workspace.resolve(name));
    }

    private static String uri(Path path) {
        return path.toAbsolutePath().toUri().toString();
    }

    private static List<Diagnostic> errors(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
                .filter(it -> it.getSeverity() == DiagnosticSeverity.Error)
                .toList();
    }

    private static final class DiagnosticsCollector implements LanguageClient {

        private final Map<String, BlockingQueue<List<Diagnostic>>> diagnostics = new ConcurrentHashMap<>();

        List<Diagnostic> awaitDiagnostics(Path path) throws Exception {
            return awaitDiagnostics(path, it -> true);
        }

        List<Diagnostic> awaitDiagnostics(Path path, Predicate<List<Diagnostic>> condition) throws Exception {
            var queue = queue(path.toAbsolutePath().toUri().getPath());
            while (true) {
                var published = queue.poll(1, TimeUnit.MINUTES);
                assertThat(published)
                        .describedAs("Diagnostics published for %s", path)
                        .isNotNull();
                if (condition.test(published)) {
                    return published;
                }
            }
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

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationStatus;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsIdeSetup;
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.junit.jupiter.api.Test;

class VerifyClassCommandHandlerCancelTest {

    private static final Path SMOKE_MODEL = Path.of("../../oxsts-test-models/smoke/smoke.semantifyr");

    private static final String SMOKE_CASE = "semantifyr::verification::smoke::CounterReachesThree";

    @Test
    void aVerificationCancelledThroughItsProgressIsAnsweredCancelled() {
        var injector = new OxstsIdeSetup().createInjectorAndDoEMFRegistration();
        var context = injector.getInstance(SemantifyrLoader.class).loadStandaloneModel(SMOKE_MODEL);
        var classDeclaration = injector.getInstance(VerificationCaseDiscoverer.class)
                .findByQualifiedName(context, SMOKE_CASE)
                .getClassDeclaration();
        var handler = new VerifyClassCommandHandler() {
            @Override
            protected <T> T performBackgroundWork(Supplier<T> work) {
                return work.get();
            }
        };
        injector.injectMembers(handler);
        var progressContext = mock(CommandProgressContext.class);
        doThrow(new CancellationException()).when(progressContext).checkIsCancelled();

        var result = (VerificationCaseResult) handler.execute(
                new VerifyClassCommandParams(classDeclaration, null),
                mock(ILanguageServerAccess.class),
                progressContext);

        assertThat(result.status()).isEqualTo(VerificationStatus.CANCELLED);
        assertThat(result.message()).isEqualTo(VerificationStatus.CANCELLED_MESSAGE);
        assertThat(result.report()).isNull();
    }
}

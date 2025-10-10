/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.backends.theta.verification.ThetaVerifier;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.PausableRequestManager;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import hu.bme.mit.semantifyr.semantics.verification.VerificationCaseRunResult;
import hu.bme.mit.semantifyr.semantics.verification.VerificationResult;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Singleton
public class VerifyOxstsCommandHandler extends AbstractCommandHandler<ClassDeclaration> {

    @Inject
    protected Provider<ThetaVerifier> oxstsVerifierProvider;

    @Inject
    protected PausableRequestManager pausableRequestManager;

    @Override
    public String getId() {
        return "oxsts.case.verify";
    }

    @Override
    public String getTitle() {
        return "Verify";
    }

    @Override
    public List<Object> serializeArguments(ClassDeclaration arguments) {
        return List.of(arguments.eResource().getURI().toString(), arguments.getName());
    }

    @Override
    protected ClassDeclaration parseArguments(List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var uriJson = (JsonPrimitive) arguments.get(0);
        var classNameJson = (JsonPrimitive) arguments.get(1);

        var uri = uriJson.getAsString();
        var className = classNameJson.getAsString();

        var classDeclaration = access.doRead(uri, context -> {
            var resource = context.getResource();
            var oxstsPackage = (OxstsModelPackage) resource.getContents().getFirst();
            return (ClassDeclaration) oxstsPackage.getDeclarations().stream().filter(c -> className.equals(c.getName())).findFirst().get();
        });

        try {
            return classDeclaration.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Object execute(ClassDeclaration arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        progressContext.begin("Verifying class " + arguments.getName(), "Initializing");

        pausableRequestManager.pause();

        var result = new CompletableFuture<VerificationCaseRunResult>();

        try {
            compilationScopeRunnable(() -> {
                progressContext.checkIsCancelled();

                result.complete(oxstsVerifierProvider.get().verify(progressContext, arguments));
            });

            progressContext.end("Verification done!");
        } catch (Exception e) {
            result.completeExceptionally(e);
        } finally {
            pausableRequestManager.resume();
        }

        try {
            var runResult = result.get();
            var res = "passed";
            if (runResult.component1().equals(VerificationResult.Passed)) {
                res = "passed";
            } else if (runResult.component1().equals(VerificationResult.Failed)) {
                res = "failed";
            }

            return new VerificationCaseRunResultDto(res, runResult.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

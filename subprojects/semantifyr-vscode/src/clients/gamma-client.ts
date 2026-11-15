/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import path from "path";
import { commands, type ExtensionContext, type Uri, window } from "vscode";
import { ExecuteCommandRequest, type LanguageClient } from "vscode-languageclient/node";
import { executablePostfix } from "../runner-utils.js";
import { createLspClient, createRemoteLspClient, registerSemantifyrSettingsSync } from "./client-utils.js";
import { VerificationTestController } from "./verification-test-controller.js";
import type { WitnessView } from "../witness.js";

let client: LanguageClient;

export async function startGammaClient(context: ExtensionContext, witnesses: WitnessView) {
    const gammaIdeExecutable = path.join(context.extensionPath, 'bin', 'gamma.lang.ide', 'bin', `gamma.lang.ide${executablePostfix}`);

    if (process.env.DEBUG_GAMMA_LSP) {
        const port = Number(process.env.DEBUG_GAMMA_LSP)
        client = createRemoteLspClient(port, "gamma");
    } else {
        client = createLspClient(gammaIdeExecutable, "gamma");
    }

    await client.start();

    const settingsSubscription = registerSemantifyrSettingsSync(client);
    context.subscriptions.push(settingsSubscription);

    const gammaTestController = new VerificationTestController(client, {
        languageId: 'gamma',
        fileGlob: '**/*.{gamma,gcd}',
        controllerId: 'gamma.verification',
        controllerLabel: 'Gamma Verification Cases',
        discoverCommand: 'gamma.case.discover',
        verifyCommand: 'gamma.case.verify',
        validateWitnessCommand: 'gamma.witness.validate',
        validateWitnessByCase: true,
    }, witnesses);
    gammaTestController.registerTestProvider(context);
    await gammaTestController.refreshWorkspaceTests();

    context.subscriptions.push(
        commands.registerCommand('semantifyr.gamma.compile', async (resource?: Uri) => {
            const uri = resource ?? window.activeTextEditor?.document.uri;
            if (!uri) {
                void window.showErrorMessage('Compile Gamma File: no Gamma file selected.');
                return;
            }
            await client.sendRequest(ExecuteCommandRequest.type, {
                command: 'gamma.compile',
                arguments: [uri.toString()],
            });
        })
    );
}

export async function stopGammaClient() {
    await client.stop();
}

/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import vscode, { commands, type ExtensionContext, Location, Position, Range, Uri, ViewColumn, window } from "vscode";
import path from "path";
import type { LanguageClient, Middleware } from "vscode-languageclient/node";
import { createRemoteLspClient, createLspClient, registerSemantifyrSettingsSync } from "./client-utils.js";
import { executablePostfix } from "../runner-utils.js";
import { VerificationTestController } from "./verification-test-controller.js";
import type { WitnessView } from "../witness.js";

interface NavigateToParams {
    locations: Location[]
}

let client: LanguageClient;

const inlineCommand = 'oxsts.class.inline';

const middleware: Middleware = {
    executeCommand: async (command, commandArguments, next) => {
        const result: unknown = await next(command, commandArguments);
        if (command === inlineCommand && typeof result === 'string') {
            await window.showTextDocument(Uri.parse(result), { viewColumn: ViewColumn.Beside, preview: false });
            await commands.executeCommand('workbench.action.files.setActiveEditorReadonlyInSession');
        }
        return result;
    },
};

export async function startOxstsClient(context: ExtensionContext, witnesses: WitnessView) {
    const oxstsIdeExecutable = path.join(context.extensionPath, 'bin', 'oxsts.lang.ide', 'bin', `oxsts.lang.ide${executablePostfix}`);

    if (process.env.DEBUG_OXSTS_LSP) {
        const port = Number(process.env.DEBUG_OXSTS_LSP)
        client = createRemoteLspClient(port, "oxsts", middleware);
    } else {
        client = createLspClient(oxstsIdeExecutable, "oxsts", middleware);
    }

    client.onNotification('workspace/navigateTo', (params: NavigateToParams) => {
        void navigateToLocation(params.locations);
    });

    await client.start();

    const settingsSubscription = registerSemantifyrSettingsSync(client);
    context.subscriptions.push(settingsSubscription);

    const oxstsTestController = new VerificationTestController(client, {
        languageId: 'oxsts',
        fileGlob: '**/*.oxsts',
        controllerId: 'oxsts.verification',
        controllerLabel: 'Semantifyr Verification Cases',
        discoverCommand: 'oxsts.case.discover',
        verifyCommand: 'oxsts.case.verify',
    }, witnesses);
    oxstsTestController.registerTestProvider(context);
    await oxstsTestController.refreshWorkspaceTests();
}

export async function stopOxstsClient() {
    await client.stop();
}

async function navigateToLocation(locations: Location[]) {
    const firstLocation = locations[0];
    if (!firstLocation) {
        return;
    }

    const actualLocations = locations.map(location => {
        const uri = vscode.Uri.parse(location.uri.toString());
        const range = new Range(
            new Position(location.range.start.line, location.range.start.character),
            new Position(location.range.end.line, location.range.end.character),
        )
        return new Location(uri, range);
    });

    let editor = vscode.window.activeTextEditor;
    if (editor === undefined) {
        const doc = await vscode.workspace.openTextDocument(firstLocation.uri);
        editor = await vscode.window.showTextDocument(doc);
    }

    const currentUri = editor.document.uri;
    const currentPosition = editor.selection.start;

    await commands.executeCommand('editor.action.goToLocations', currentUri, currentPosition, actualLocations, "peek");
}

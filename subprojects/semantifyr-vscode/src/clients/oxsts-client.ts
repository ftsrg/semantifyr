
import vscode, { commands, ExtensionContext, Location, Position, Range } from "vscode";
import path from "path";
import { LanguageClient} from "vscode-languageclient/node.js";
import { createRemoteLspClient, createLspClient, registerSemantifyrSettingsSync } from "./client-utils.js";
import { executablePostfix } from "../runner-utils.js";
import { VerificationTestController } from "./verification-test-controller.js";

type NavigateToParams = {
    locations: Location[]
}

let client: LanguageClient;

export async function startOxstsClient(context: ExtensionContext) {
    const oxstsIdeExecutable = path.join(context.extensionPath, 'bin', 'oxsts.lang.ide', 'bin', `oxsts.lang.ide${executablePostfix}`);

    if (process.env.DEBUG_OXSTS_LSP) {
        const port = Number(process.env.DEBUG_OXSTS_LSP)
        client = createRemoteLspClient(port, "oxsts");
    } else {
        client = createLspClient(oxstsIdeExecutable, "oxsts");
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
    });
    oxstsTestController.registerTestProvider(context);
    await oxstsTestController.refreshWorkspaceTests();
}

export async function stopOxstsClient() {
    await client.stop();
}

async function navigateToLocation(locations: Location[]) {
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
        const uri = locations[0].uri;
        const doc = await vscode.workspace.openTextDocument(uri);
        editor = await vscode.window.showTextDocument(doc);
    }

    const currentUri = editor.document.uri;
    const currentPosition = editor.selection.start;

    await commands.executeCommand('editor.action.goToLocations', currentUri, currentPosition, actualLocations, "peek");
}

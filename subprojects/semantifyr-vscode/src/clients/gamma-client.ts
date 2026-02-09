import path from "path";
import { ExtensionContext } from "vscode";
import { LanguageClient } from "vscode-languageclient/node.js";
import { executablePostfix } from "../runner-utils.js";
import { createLspClient, createRemoteLspClient } from "./client-utils.js";

let client: LanguageClient;

export async function startGammaClient(context: ExtensionContext) {
    const gammaIdeExecutable = path.join(context.extensionPath, 'bin', 'gamma.lang.ide', 'bin', `gamma.lang.ide${executablePostfix}`);

    if (process.env.DEBUG_GAMMA_LSP) {
        const port = Number(process.env.DEBUG_GAMMA_LSP)
        client = createRemoteLspClient(port, "gamma");
    } else {
        client = createLspClient(gammaIdeExecutable, "gamma");
    }

    await client.start();
}

export async function stopGammaClient() {
    await client.stop();
}

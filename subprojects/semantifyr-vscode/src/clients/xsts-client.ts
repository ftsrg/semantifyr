import path from "path";
import { ExtensionContext } from "vscode";
import { LanguageClient } from "vscode-languageclient/node.js";
import { executablePostfix } from "../runner-utils.js";
import { createLspClient, createRemoteLspClient } from "./client-utils.js";

let client: LanguageClient;

export async function startXstsClient(context: ExtensionContext) {
    const xstsIdeExecutable = path.join(context.extensionPath, 'bin', 'xsts.lang.ide', 'bin', `xsts.lang.ide${executablePostfix}`);

    if (process.env.DEBUG_XSTS_LSP) {
        const port = Number(process.env.DEBUG_XSTS_LSP)
        client = createRemoteLspClient(port, "xsts");
    } else {
        client = createLspClient(xstsIdeExecutable, "xsts");
    }

    await client.start();
}

export async function stopXstsClient() {
    await client.stop();
}

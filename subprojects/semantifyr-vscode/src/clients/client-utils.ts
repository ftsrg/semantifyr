import { workspace } from "vscode";
import * as net from "net";
import { spawn } from "child_process";
import { LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo } from "vscode-languageclient/node.js";
import { DidChangeConfigurationNotification } from "vscode-languageclient";
import { readSemantifyrPayload } from "./settings.js";

function buildClientOptions(language: string): LanguageClientOptions {
    return {
        documentSelector: [{ scheme: "file", language: language }],
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher("**"),
        },
        initializationOptions: readSemantifyrPayload(),
    };
}

export function registerSemantifyrSettingsSync(client: LanguageClient) {
    const send = async () => {
        await client.sendNotification(DidChangeConfigurationNotification.type, {
            settings: readSemantifyrPayload(),
        });
    };
    void send();
    const subscription = workspace.onDidChangeConfiguration(async (event) => {
        if (event.affectsConfiguration("semantifyr")) {
            await send();
        }
    });
    return subscription;
}

export function createRemoteLspClient(port: number, language: string): LanguageClient {
    const serverOptions: ServerOptions = () => {
        return new Promise((resolve, reject) => {
            const socket = net.connect(port, "127.0.0.1", () => {
                resolve({
                    reader: socket,
                    writer: socket,
                });
            });
            socket.on("error", (err) => {
                reject(err);
            });
        });
    };

    return new LanguageClient(
        `${language}LSP`,
        `${language.toUpperCase()} Language Server`,
        serverOptions,
        buildClientOptions(language)
    );
}

export function createLspClient(lspExecutable: string, language: string): LanguageClient {
    const serverOptions: ServerOptions = () => new Promise<StreamInfo>((resolve, reject) => {
        const server = net.createServer((socket) => {
            resolve({ reader: socket, writer: socket });
        });
        server.on("error", reject);
        server.listen(0, "127.0.0.1", () => {
            const address = server.address();
            if (address === null || typeof address === "string") {
                reject(new Error(`Failed to obtain a port for the ${language} LSP socket`));
                return;
            }
            const port = address.port;
            const child = spawn(lspExecutable, [`--socket=${port}`], {
                shell: true,
                stdio: ["ignore", "inherit", "inherit"],
            });
            child.on("error", reject);
            child.on("exit", () => server.close());
        });
    });

    return new LanguageClient(
        `${language}LSP`,
        `${language.toUpperCase()} Language Server`,
        serverOptions,
        buildClientOptions(language)
    );
}

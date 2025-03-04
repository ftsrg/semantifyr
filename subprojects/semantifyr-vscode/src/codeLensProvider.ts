/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import vscode, {CancellationError, ExtensionContext} from "vscode";

class OxstsCodeLensProvider implements vscode.CodeLensProvider {

    public provideCodeLenses(document: vscode.TextDocument, token: vscode.CancellationToken): vscode.CodeLens[] {
        const codeLenses: vscode.CodeLens[] = [];
        const targetRegex = /target\s+([a-zA-Z0-9_]+)/g;
        const documentText = document.getText();

        let match: RegExpExecArray | null;

        while ((match = targetRegex.exec(documentText)) !== null) {
            if (token.isCancellationRequested) {
                throw new CancellationError();
            }

            const targetName = match[1];
            const line = document.positionAt(match.index).line;
            const range = new vscode.Range(line, 0, line, 0);

            codeLenses.push(new vscode.CodeLens(range, {
                title: 'Compile',
                command: 'semantifyr.compileTarget',
                arguments: [targetName, document]
            }));

            codeLenses.push(new vscode.CodeLens(range, {
                title: 'Verify',
                command: 'semantifyr.verifyTarget',
                arguments: [targetName, document, true]
            }));

            codeLenses.push(new vscode.CodeLens(range, {
                title: 'Verify (no Witness)',
                command: 'semantifyr.verifyTarget',
                arguments: [targetName, document, false]
            }));
        }

        return codeLenses;
    }

}

class GammaCodeLensProvider implements vscode.CodeLensProvider {

    public provideCodeLenses(document: vscode.TextDocument, token: vscode.CancellationToken): vscode.CodeLens[] {        
        const codeLenses: vscode.CodeLens[] = [];
        const targetRegex = /verification case\s+([a-zA-Z0-9_]+)/g;
        const documentText = document.getText();

        let match: RegExpExecArray | null;

        while ((match = targetRegex.exec(documentText)) !== null) {
            if (token.isCancellationRequested) {
                throw new CancellationError();
            }

            const verificationCaseName = match[1];
            const line = document.positionAt(match.index).line;
            const range = new vscode.Range(line, 0, line, 0);

            codeLenses.push(new vscode.CodeLens(range, {
                title: 'Verify',
                command: 'gamma.verify',
                arguments: [verificationCaseName, document, true]
            }));

            codeLenses.push(new vscode.CodeLens(range, {
                title: 'Verify (no Witness)',
                command: 'gamma.verify',
                arguments: [verificationCaseName, document, false]
            }));
        }

        return codeLenses;
    }

}

class SysMLCodeLensProvider implements vscode.CodeLensProvider {

    public provideCodeLenses(document: vscode.TextDocument, token: vscode.CancellationToken): vscode.CodeLens[] {        
        const codeLenses: vscode.CodeLens[] = [];
        const targetRegex = /verification def\s+([a-zA-Z0-9_']+)/g;
        const documentText = document.getText();

        let match: RegExpExecArray | null;

        while ((match = targetRegex.exec(documentText)) !== null) {
            if (token.isCancellationRequested) {
                throw new CancellationError();
            }

            const verificationCaseName = match[1];
            const line = document.positionAt(match.index).line;
            const range = new vscode.Range(line, 0, line, 0);

            codeLenses.push(new vscode.CodeLens(range, {
                title: 'Verify',
                command: 'gamma.verify',
                arguments: [verificationCaseName, document, true]
            }));

            codeLenses.push(new vscode.CodeLens(range, {
                title: 'Verify (no Witness)',
                command: 'gamma.verify',
                arguments: [verificationCaseName, document, false]
            }));
        }

        return codeLenses;
    }

}

export function registerCodeLensProvider(context: ExtensionContext) {
    context.subscriptions.push(
            vscode.languages.registerCodeLensProvider({language: 'oxsts'}, new OxstsCodeLensProvider())
    );
    context.subscriptions.push(
            vscode.languages.registerCodeLensProvider({language: 'gamma'}, new GammaCodeLensProvider())
    );
    context.subscriptions.push(
            vscode.languages.registerCodeLensProvider({language: 'sysml'}, new SysMLCodeLensProvider())
    );
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createLspClient, registerSemantifyrSettingsSync } from '../clients/client-utils.js';
import { readSemantifyrSettings } from '../clients/settings.js';
import { resetVscode, state } from './stubs/vscode.js';
import { LanguageClient } from './stubs/vscode-languageclient-node.js';

interface ContributedSetting {
    default: unknown;
}

const contributed = (JSON.parse(readFileSync(new URL('../../package.json', import.meta.url), 'utf-8')) as {
    contributes: { configuration: { properties: Record<string, ContributedSetting> } };
}).contributes.configuration.properties;

function contributedDefaults() {
    const value = (name: string) => contributed[`semantifyr.${name}`]!.default;
    return {
        portfolio: value('portfolio'),
        timeoutSeconds: value('timeoutSeconds'),
        maxConcurrency: value('maxConcurrency'),
        theta: { executor: value('theta.executor'), dockerImage: value('theta.dockerImage') },
        artifacts: { location: value('artifacts.location'), preset: value('artifacts.preset') },
        optimization: { level: value('optimization.level') },
    };
}

beforeEach(() => {
    resetVscode();
});

afterEach(() => {
    LanguageClient.disposeAll();
});

describe('readSemantifyrSettings', () => {
    it('reads every contributed setting', () => {
        expect(Object.keys(contributed).sort()).toEqual([
            'semantifyr.artifacts.location',
            'semantifyr.artifacts.preset',
            'semantifyr.maxConcurrency',
            'semantifyr.optimization.level',
            'semantifyr.portfolio',
            'semantifyr.theta.dockerImage',
            'semantifyr.theta.executor',
            'semantifyr.timeoutSeconds',
        ]);
    });

    it('falls back to the contributed defaults', () => {
        expect(readSemantifyrSettings()).toEqual(contributedDefaults());
    });

    it('reads the configured values', () => {
        state.configuration.set('semantifyr.portfolio', 'theta-full');
        state.configuration.set('semantifyr.theta.executor', 'docker');
        state.configuration.set('semantifyr.theta.dockerImage', 'example/theta:1.0');

        expect(readSemantifyrSettings()).toEqual({
            ...contributedDefaults(),
            portfolio: 'theta-full',
            theta: { executor: 'docker', dockerImage: 'example/theta:1.0' },
        });
    });
});

describe('settings reaching the server', () => {
    it('passes the settings as initialization options', () => {
        state.configuration.set('semantifyr.portfolio', 'theta-full');
        state.configuration.set('semantifyr.theta.dockerImage', 'example/theta:1.0');

        const client = createLspClient('/extension/bin/oxsts.lang.ide', 'oxsts') as unknown as LanguageClient;

        expect(client.clientOptions.initializationOptions).toEqual({
            semantifyr: { ...contributedDefaults(), portfolio: 'theta-full', theta: { executor: 'auto', dockerImage: 'example/theta:1.0' } },
        });
        expect(client.clientOptions.documentSelector).toEqual([{ scheme: 'file', language: 'oxsts' }]);
    });

    it('sends the settings on start and on each change of a Semantifyr setting', async () => {
        const client = createLspClient('/extension/bin/oxsts.lang.ide', 'oxsts') as unknown as LanguageClient;
        const received: unknown[] = [];
        client.server.onNotification('workspace/didChangeConfiguration', (parameters: { settings: { semantifyr: { portfolio: string } } }) => {
            received.push(parameters.settings.semantifyr.portfolio);
        });

        const subscription = registerSemantifyrSettingsSync(client as never);
        await expect.poll(() => received).toEqual(['smart-full']);

        state.configuration.set('semantifyr.portfolio', 'theta-full');
        state.configurationChanged.fire({ affectsConfiguration: (section) => section === 'semantifyr' });
        await expect.poll(() => received).toEqual(['smart-full', 'theta-full']);

        state.configurationChanged.fire({ affectsConfiguration: (section) => section === 'editor' });
        subscription.dispose();
        state.configurationChanged.fire({ affectsConfiguration: () => true });
        await new Promise((resolve) => setTimeout(resolve, 20));
        expect(received).toEqual(['smart-full', 'theta-full']);
    });
});

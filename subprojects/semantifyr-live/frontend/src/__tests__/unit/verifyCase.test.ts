/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import {
  AbstractMessageReader,
  AbstractMessageWriter,
  CancellationTokenSource,
  createMessageConnection,
  type Disposable,
  type Message,
  type NotificationMessage,
  type RequestMessage,
} from 'vscode-jsonrpc';
import { verifyCase } from '@semantifyr/editor-common';

class SilentReader extends AbstractMessageReader {
  listen(): Disposable {
    return { dispose: () => {} };
  }
}

class CapturingWriter extends AbstractMessageWriter {
  readonly messages: Message[] = [];

  write(message: Message): Promise<void> {
    this.messages.push(message);
    return Promise.resolve();
  }

  end(): void {}
}

const uri = 'file:///workspace/snippet.oxsts';
const range = { start: { line: 1, character: 0 }, end: { line: 3, character: 1 } };

function connect() {
  const writer = new CapturingWriter();
  const connection = createMessageConnection(new SilentReader(), writer);
  connection.listen();
  return { connection, writer };
}

describe('verifyCase over a vscode-jsonrpc connection', () => {
  it('sends the command as by-name parameters when no token is given', async () => {
    const { connection, writer } = connect();

    void verifyCase(connection, 'oxsts.case.verify', { uri, range }, { portfolio: 'smart-full' }).catch(() => null);

    await vi.waitFor(() => {
      expect(writer.messages).toHaveLength(1);
    });
    expect((writer.messages[0] as RequestMessage).params).toEqual({
      command: 'oxsts.case.verify',
      arguments: [{ uri, range, portfolio: 'smart-full' }],
    });
    connection.dispose();
  });

  it('cancels through the token it is given', async () => {
    const { connection, writer } = connect();
    const source = new CancellationTokenSource();

    void verifyCase(connection, 'oxsts.case.verify', { uri, range }, { portfolio: 'smart-full', token: source.token }).catch(() => null);
    await vi.waitFor(() => {
      expect(writer.messages).toHaveLength(1);
    });
    source.cancel();

    await vi.waitFor(() => {
      expect(writer.messages.map((message) => (message as NotificationMessage).method)).toContain('$/cancelRequest');
    });
    expect((writer.messages[0] as RequestMessage).params).toEqual({
      command: 'oxsts.case.verify',
      arguments: [{ uri, range, portfolio: 'smart-full' }],
    });
    connection.dispose();
  });
});

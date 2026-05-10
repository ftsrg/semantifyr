/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.concurrent;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LockProvider {

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    public void acquireWriteLock() {
        writeLock.lock();
    }

    public void acquireReadLock() {
        readLock.lock();
    }

    public void releaseWriteLock() {
        writeLock.unlock();
    }

    public void releaseReadLock() {
        readLock.unlock();
    }

}

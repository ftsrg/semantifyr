/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runInterruptible
import java.io.Closeable
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * docker-java's async commands use a callback-based API. This callback bridges a single terminal
 * result into a suspendable [await]. `onNext` captures the last item; `onComplete` resolves it,
 * `onError` rejects the deferred.
 */
class DeferredResultCallback<T : Any> : ResultCallback<T> {
    private val job = CompletableDeferred<T>()
    private lateinit var item: T

    override fun close() {}

    override fun onStart(closeable: Closeable?) {}

    override fun onError(throwable: Throwable) {
        job.completeExceptionally(throwable)
    }

    override fun onComplete() {
        job.complete(item)
    }

    override fun onNext(item: T) {
        this.item = item
    }

    suspend fun await(): T = job.await()
}

/**
 * Runs a docker-java async command that expects a [ResultCallback] and awaits its single terminal
 * result suspendably. Cancellation interrupts the underlying call.
 */
suspend inline fun <T : Any> runAsync(
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: (DeferredResultCallback<T>) -> Unit,
): T {
    val callback = DeferredResultCallback<T>()
    runInterruptible(context) {
        block(callback)
    }
    return callback.await()
}

/**
 * docker-java callback that splits a container's stdout/stderr frames into the two given output
 * streams. Closed once the container's log stream completes. Exceptions during log streaming are
 * surfaced via [await]; callers typically swallow them since container logs are best-effort.
 */
class StreamLoggerCallback(
    private val logStream: OutputStream,
    private val errorStream: OutputStream,
) : ResultCallback<Frame> {

    private val job = CompletableDeferred<Unit>()

    override fun onNext(item: Frame) {
        when (item.streamType) {
            StreamType.STDOUT -> logStream.write(item.payload)
            StreamType.STDERR -> errorStream.write(item.payload)
            else -> { /* ignore raw/stdin frames */ }
        }
    }

    override fun close() {
        logStream.close()
        errorStream.close()
    }

    override fun onStart(closeable: Closeable) {}

    override fun onError(throwable: Throwable) {
        job.completeExceptionally(throwable)
    }

    override fun onComplete() {
        job.complete(Unit)
    }

    suspend fun await() {
        job.await()
        close()
    }
}

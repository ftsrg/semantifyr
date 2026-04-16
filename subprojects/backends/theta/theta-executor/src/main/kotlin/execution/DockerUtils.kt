/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.wrapper.execution

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runInterruptible
import java.io.Closeable
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class DeferredResultCallback<T : Any> : ResultCallback<T> {
    private val job = CompletableDeferred<T>()
    private lateinit var item: T

    override fun close() {

    }

    override fun onStart(closeable: Closeable?) {

    }

    override fun onError(throwable: Throwable) {
        job.completeExceptionally(throwable)
    }

    override fun onComplete() {
        job.complete(item)
    }

    override fun onNext(item: T) {
        this.item = item
    }

    suspend fun await(): T {
        return job.await()
    }

}

suspend inline fun <T : Any> runAsync(context: CoroutineContext = EmptyCoroutineContext, crossinline block: (DeferredResultCallback<T>) -> Unit): T {
    val callback = DeferredResultCallback<T>()

    runInterruptible(context) {
        block(callback)
    }

    return callback.await()
}

class StreamLoggerCallback(
    private val logStream: OutputStream,
    private val errorStream: OutputStream
) : ResultCallback<Frame> {

    private val job = CompletableDeferred<Unit>()

    override fun onNext(item: Frame) {
        if (item.streamType == StreamType.STDOUT) {
            logStream.write(item.payload)
        } else if (item.streamType == StreamType.STDERR) {
            errorStream.write(item.payload)
        }
    }

    override fun close() {
        logStream.close()
        errorStream.close()
    }

    override fun onStart(closeable: Closeable) {

    }

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

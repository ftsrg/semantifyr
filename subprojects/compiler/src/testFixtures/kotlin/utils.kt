/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.engine

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runInterruptible
import java.io.Closeable
import java.io.OutputStream

suspend fun <T> List<Deferred<T>>.awaitAny(): T {
    val firstCompleted = CompletableDeferred<T>()
    var counter = 0

    forEach { job ->
        job.invokeOnCompletion { exception ->
            counter++

            if (exception == null && !firstCompleted.isCompleted) {
                firstCompleted.complete(job.getCompleted())
            } else {
                if (counter == size) {
                    firstCompleted.completeExceptionally(IllegalStateException("All executed jobs failed: ", exception))
                }
            }
        }
    }

    return firstCompleted.await()
}

class DefferedResultCallback<T : Any> : ResultCallback<T> {
    val job = CompletableDeferred<T>()
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

}

suspend fun <T : Any> runAsync(block: (DefferedResultCallback<T>) -> Unit): T {
    val callback = DefferedResultCallback<T>()

    runInterruptible {
        block(callback)
    }

    return callback.job.await()
}

class StreamLoggerCallback(
    private val logStream: OutputStream,
    private val errorStream: OutputStream
) : ResultCallback.Adapter<Frame>() {

    override fun onNext(frame: Frame) {
        if (frame.streamType == StreamType.STDOUT) {
            logStream.write(frame.payload)
        } else if (frame.streamType == StreamType.STDERR) {
            errorStream.write(frame.payload)
        }
    }

    override fun close() {
        super.close()
        logStream.close()
        errorStream.close()
    }
}

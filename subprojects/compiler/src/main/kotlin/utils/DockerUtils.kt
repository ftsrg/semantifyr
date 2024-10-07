package hu.bme.mit.semantifyr.oxsts.compiler.utils

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runInterruptible
import java.io.Closeable
import java.io.OutputStream
import kotlin.collections.forEach

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> List<Deferred<T>>.awaitAny(): T {
    val firstCompleted = CompletableDeferred<T>()
    var counter = 0

    forEach { job ->
        job.invokeOnCompletion { exception ->
            synchronized(firstCompleted) {
                val id = counter++

                if (!firstCompleted.isCompleted) {
                    if (exception == null) {
                        firstCompleted.complete(job.getCompleted())
                    } else if (id == size - 1) {
                        firstCompleted.completeExceptionally(IllegalStateException("All executed jobs failed: ", exception))
                    }
                }
            }
        }
    }

    return firstCompleted.await()
}

class DefferedResultCallback<T : Any> : ResultCallback<T> {
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

suspend fun <T : Any> runAsync(block: (DefferedResultCallback<T>) -> Unit): T {
    val callback = DefferedResultCallback<T>()

    runInterruptible {
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
    }

}

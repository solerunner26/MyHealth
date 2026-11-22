package app.myhealth.core

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CancellationException

/** Last-resort guards so that a click, lifecycle callback or background job can never kill the process. */
object Safe {
    private const val TAG = "MyHealth"

    val handler = CoroutineExceptionHandler { _, e -> if (e !is CancellationException) Log.w(TAG, "background failure", e) }

    inline fun <T> call(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("MyHealth", "guarded failure", e)
        fallback
    }
}

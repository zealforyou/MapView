package ovh.plrapps.mapview.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * So long as the returned [SendChannel] receives [T] elements, the provided [block] function isn't
 * executed until a time-span of [timeoutMillis] elapses.
 * When [block] is executed, it's provided with the last [T] value sent to the channel.
 *
 * @author peterLaurence
 */
fun <T> CoroutineScope.debounce(
        timeoutMillis: Long,
        block: (T) -> Unit
): SendChannel<T> {
    val channel = Channel<T>(capacity = Channel.CONFLATED)
    val flow = channel.consumeAsFlow().debounce(timeoutMillis)
    launch {
        flow.collect {
            block(it)
        }
    }

    return channel
}
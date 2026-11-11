package news.app.server.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import news.app.server.serverUrl
import kotlin.time.Duration

/**
 * The addresses the phone keeps for the news server, to try when the one in use stops answering (as the journal app's
 * KeptDiscovery): the one that last worked first, then the server's own list (`serverInfo.discovery.addresses`, or
 * its DHT record), then those kept before; at most [MAX], the enrolled one always among them. Each is `https://host:port`.
 */
object ServerAddresses {
    const val MAX = 16

    /** [address] as kept: `https://host:port` ([serverUrl]); null when it isn't an address. */
    fun originOf(address: String): String? = serverUrl(address)

    /** [kept] with [working] first: it is the one that answers now. */
    fun working(kept: List<String>, working: String, enrolled: String?): List<String> =
        capped(listOfNotNull(originOf(working)) + kept, enrolled)

    /** [kept] after the server named its [addresses] (serverInfo.discovery): the working one, the server's, the rest. */
    fun merge(kept: List<String>, working: String, addresses: List<String>, enrolled: String?): List<String> =
        capped(listOfNotNull(originOf(working)) + addresses.mapNotNull(::originOf) + kept, enrolled)

    /** [found] (the server's DHT record, newer than what was kept) before [kept]. */
    fun withFound(kept: List<String>, found: List<String>, enrolled: String?): List<String> =
        capped(found.mapNotNull(::originOf) + kept, enrolled)

    /** [all] without repeats, at most [MAX], keeping [enrolled] when it is among them. */
    private fun capped(all: List<String>, enrolled: String?): List<String> {
        val distinct = all.distinct()
        val first = distinct.take(MAX)
        return if (enrolled == null || enrolled in first || enrolled !in distinct) first else first.dropLast(1) + enrolled
    }
}

/**
 * The first of [origins] for which [probe] says the server answers, each started [stagger] after the one before (so the
 * likeliest goes first, and a slow one doesn't hold the rest up); null when none does. From the journal app.
 */
suspend fun firstToAnswer(origins: List<String>, stagger: Duration, probe: suspend (String) -> Boolean): String? {
    if (origins.isEmpty()) return null
    return coroutineScope {
        val winner = CompletableDeferred<String?>()
        val tries = origins.mapIndexed { i, origin ->
            launch {
                delay(stagger * i)
                val answered = try {
                    // On its own job: a failed request may fail the job it runs in, which mustn't stop the others.
                    supervisorScope { async { probe(origin) }.await() }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                if (answered) winner.complete(origin)
            }
        }
        launch {
            tries.joinAll()
            winner.complete(null)
        }
        winner.await().also { coroutineContext.cancelChildren() }
    }
}

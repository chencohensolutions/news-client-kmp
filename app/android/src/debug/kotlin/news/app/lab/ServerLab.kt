package news.app.lab

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import news.app.ServerActions
import news.app.ServerError
import news.app.ServerScreen
import news.app.ServerState
import kotlin.time.Instant

// Settings › שרת החדשות with made-up connections (the app's is news.app.server.NewsServer). From the server round's
// option a (docs/ux-history/server/2026-11-10-connect.md).

private const val ADDRESS = "192.168.50.2"
private const val CODE = "K7QM-3XRP"

/** A code the fake server turns down, to show the error by typing it. */
private const val EXPIRED = "Q2WD-8LTN"

private fun connected(unreachable: Boolean = false) = ServerState.Connected(
    address = ADDRESS,
    user = "reader",
    deviceName = Build.MODEL,
    version = "0.5.0 (c8cf640)",
    storiesOn = true,
    lastRead = PreviewData.refreshedAt,
    certificateExpires = Instant.fromEpochMilliseconds(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000),
    unreachableSince = if (unreachable) Instant.fromEpochMilliseconds(System.currentTimeMillis() - 23 * 60_000) else null,
)

/**
 * The server page over a fake connection: scanning connects to [ADDRESS] at once (a QR code carries the key); a typed
 * code shows a key to confirm first, or is turned down when it is [EXPIRED]; connecting takes a moment;
 * retry and disconnect change only this screen.
 */
@Composable
private fun ServerLab(initial: ServerState) {
    var state by remember { mutableStateOf(initial) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    fun form() = state as? ServerState.NotConnected ?: ServerState.NotConnected()
    fun finish(address: String, code: String) {
        job = scope.launch {
            state = ServerState.Connecting(address, code, user = "reader")
            delay(1500)
            state = connected()
        }
    }
    ServerScreen(
        state,
        ServerActions(
            onAddress = { state = form().copy(address = it, error = null) },
            onCode = { state = form().copy(code = it, error = null) },
            onScan = { finish(ADDRESS, CODE) },
            onConnect = {
                val f = form()
                job = scope.launch {
                    state = ServerState.Connecting(f.address, f.code)
                    delay(1000)
                    state = if (f.code.equals(EXPIRED, ignoreCase = true)) f.copy(error = ServerError.CODE_EXPIRED)
                    else ServerState.ConfirmKey(f.address, f.code, "reader", "3f9a c210 7be4 51d8")
                }
            },
            onConfirmKey = { (state as? ServerState.ConfirmKey)?.let { finish(it.address, it.code) } },
            onCancel = {
                job?.cancel()
                state = when (val s = state) {
                    is ServerState.Connecting -> ServerState.NotConnected(s.address, s.code)
                    is ServerState.ConfirmKey -> ServerState.NotConnected(s.address, s.code)
                    else -> s
                }
            },
            onRetry = { (state as? ServerState.Connected)?.let { state = it.copy(unreachableSince = null) } },
            onDisconnect = { state = ServerState.NotConnected() },
        ),
    )
}

/** The server page's states, `current` first. */
internal val serverLabOptions: List<LabOption> = listOf(
    LabOption("current", "Current", "Not connected: scan, or type the address and code") { ServerLab(ServerState.NotConnected()) },
    LabOption("confirm", "Confirm the key", "A typed code: the server's key, to compare with what enroll-code printed") {
        ServerLab(ServerState.ConfirmKey(ADDRESS, CODE, "reader", "3f9a c210 7be4 51d8"))
    },
    LabOption("connecting", "Connecting", "The code checked, the certificate on its way") { ServerLab(ServerState.Connecting(ADDRESS, CODE, user = "reader")) },
    LabOption("connected", "Connected", "Address, last read, stories, version, disconnect") { ServerLab(connected()) },
    LabOption("expired", "Code expired", "INVALID_CODE under the code field") {
        ServerLab(ServerState.NotConnected(ADDRESS, EXPIRED, ServerError.CODE_EXPIRED))
    },
    LabOption("wrong-key", "Another key", "The server at the address isn't the QR code's") {
        ServerLab(ServerState.NotConnected(ADDRESS, CODE, ServerError.WRONG_KEY))
    },
    LabOption("unreachable", "Server unreachable", "Connected, no answer for 23 min: the feed stays as it was meanwhile") { ServerLab(connected(unreachable = true)) },
)

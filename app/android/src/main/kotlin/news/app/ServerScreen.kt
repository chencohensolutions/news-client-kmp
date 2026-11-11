package news.app

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import java.util.Date
import kotlin.time.Instant

/** What the server page can ask for ([NewsViewModel]'s server functions). */
class ServerActions(
    val onAddress: (String) -> Unit = {},
    val onCode: (String) -> Unit = {},
    /** Google's code scanner (Play services): the QR code is an enrollment link with the address, code and key. */
    val onScan: () -> Unit = {},
    /** The enrollment link from the clipboard: copied from a message that can't open `news://` links. */
    val onPaste: () -> Unit = {},
    val onConnect: () -> Unit = {},
    /** The key shown for a typed code is the one `enroll-code` printed. */
    val onConfirmKey: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    /** The statistics page: what the server has done. */
    val onStats: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * Connecting the phone to the news server (news-server-go), where all of the app's news comes from. Until the phone
 * is enrolled, this is the only screen the app shows ([canGoBack] false); once connected it is Settings › שרת החדשות,
 * with the server's details. Scanning comes first, typing below it. From the UI lab's server round, option a
 * (docs/ux-history/server/2026-11-10-connect.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(state: ServerState, actions: ServerActions, canGoBack: Boolean = true) {
    AppLayout {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("שרת החדשות") },
                    navigationIcon = {
                        if (canGoBack) IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                when (state) {
                    is ServerState.NotConnected -> Enroll(state.address, state.code, state.error, connecting = false, user = null, actions, detail = state.detail)
                    is ServerState.Connecting -> Enroll(state.address, state.code, null, connecting = true, user = state.user, actions)
                    is ServerState.ConfirmKey -> ConfirmKeyPage(state, actions)
                    is ServerState.Connected -> ConnectedPage(state, actions)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Enroll(address: String, code: String, error: ServerError?, connecting: Boolean, user: String?, actions: ServerActions, detail: String? = null) {
    Muted(
        "האפליקציה מציגה את החדשות משרת החדשות שלכם: הוא אוסף את הכותרות והכתבות, מקבץ את הסיפורים " +
            "וכותב את הסיכומים. חברו את הטלפון לשרת כדי להתחיל.",
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    Section("חיבור")
    Muted(
        "בשרת: news-server enroll-code ושם המשתמש. הפקודה מדפיסה קוד QR, קוד חד־פעמי ומפתח.",
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    OutlinedButton(
        onClick = actions.onScan,
        enabled = !connecting,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text("סריקת קוד QR")
    }
    TextButton(
        onClick = actions.onPaste,
        enabled = !connecting,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("הדבקת קישור")
    }
    Muted("או הקלדה (אפשר גם להדביק כאן את הקישור כולו):", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    OutlinedTextField(
        value = address,
        onValueChange = actions.onAddress,
        enabled = !connecting,
        singleLine = true,
        label = { Text("כתובת השרת") },
        supportingText = {
            Text(
                when (error) {
                    ServerError.UNREACHABLE -> "אין תשובה מהכתובת הזאת. השרת פועל, והטלפון באותה רשת?"
                    ServerError.BAD_ADDRESS -> "זו לא כתובת. לדוגמה 192.168.50.2 או myhome.example.org:56399"
                    ServerError.WRONG_KEY -> "השרת בכתובת הזאת הציג מפתח אחר מזה שבקוד ה־QR. זה השרת הנכון?"
                    else -> "לדוגמה 192.168.50.2; בלי פורט: 56399"
                },
            )
        },
        isError = error == ServerError.UNREACHABLE || error == ServerError.BAD_ADDRESS || error == ServerError.WRONG_KEY,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    OutlinedTextField(
        value = code,
        onValueChange = actions.onCode,
        enabled = !connecting,
        singleLine = true,
        label = { Text("קוד חיבור") },
        supportingText = {
            Text(
                when (error) {
                    ServerError.CODE_EXPIRED -> "הקוד לא תקף: פג תוקפו או שכבר השתמשו בו. צרו קוד חדש בשרת."
                    ServerError.RATE_LIMITED -> "יותר מדי קודים שגויים. חכו דקה ונסו שוב."
                    ServerError.FAILED -> "החיבור נכשל. נסו שוב, או צרו קוד חדש."
                    ServerError.CODE_USED -> "הקוד הזה כבר נשלח לשרת מהטלפון, והשרת לא יקבל אותו שוב. צרו קוד חדש."
                    ServerError.SAVE_FAILED -> "השרת רשם את הטלפון, אבל שמירת התעודה בטלפון נכשלה: ${detail.orEmpty()}. צרו קוד חדש ונסו שוב."
                    else -> "8 אותיות וספרות. עובד פעם אחת, ל־10 דקות."
                },
            )
        },
        isError = error in setOf(ServerError.CODE_EXPIRED, ServerError.RATE_LIMITED, ServerError.FAILED, ServerError.CODE_USED, ServerError.SAVE_FAILED),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (connecting) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Muted(if (user == null) "בודק את הקוד…" else "מחבר את הטלפון לחשבון $user…", Modifier.weight(1f))
            TextButton(onClick = actions.onCancel) { Text("ביטול") }
        } else {
            Button(onClick = actions.onConnect, enabled = address.isNotBlank() && code.isNotBlank()) { Text("חיבור") }
        }
    }
    if (connecting) {
        Muted(
            "נוצר מפתח פרטי בטלפון, שלא יוצא ממנו. השרת מחזיר תעודה, ואיתה הטלפון מזדהה מעכשיו.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/**
 * A typed code: the phone has nothing to check the server against, so the key it showed is compared with the "Key"
 * line `enroll-code` printed before anything is made. The phone then trusts only that key.
 */
@Composable
private fun ConfirmKeyPage(state: ServerState.ConfirmKey, actions: ServerActions) {
    Section("אישור המפתח של השרת")
    Muted(
        "הקוד שייך לחשבון ${state.user} בשרת ${state.address}. הטלפון יסמוך רק על המפתח הזה, אז בדקו שהוא זהה " +
            "לשורה Key שהפקודה הדפיסה.",
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            state.key,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            textAlign = TextAlign.Center,
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = actions.onConfirmKey) { Text("המפתח זהה, חיבור") }
        TextButton(onClick = actions.onCancel) { Text("ביטול") }
    }
    Muted("מפתח אחר: אולי זה לא השרת שלכם. בטלו ובדקו את הכתובת.", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

/** The server row's subtitle on Settings' main page. */
fun serverStatus(state: ServerState): String = when (state) {
    is ServerState.NotConnected -> "לא מחובר"
    is ServerState.Connecting -> "מתחבר…"
    is ServerState.ConfirmKey -> "מחכה לאישור המפתח"
    is ServerState.Connected -> when {
        state.unreachableSince != null -> "השרת לא עונה · אין כותרות חדשות"
        else -> "מחובר ל־${state.address}" + (state.lastRead?.let { " · נקרא ${ago(it)}" } ?: "")
    }
}

@Composable
private fun ConnectedPage(state: ServerState.Connected, actions: ServerActions) {
    val context = LocalContext.current
    fun date(i: Instant) = DateFormat.getDateFormat(context).format(Date(i.toEpochMilliseconds()))
    fun time(i: Instant) = DateFormat.getTimeFormat(context).format(Date(i.toEpochMilliseconds()))
    state.unreachableSince?.let { since ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f)) {
                Text("השרת לא עונה מאז ${ltr(time(since))}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                Muted("בינתיים לא מגיעות כותרות חדשות. מה שכבר בטלפון נשאר לקריאה.")
            }
            TextButton(onClick = actions.onRetry) { Text("ניסיון שוב") }
        }
    }
    run {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = actions.onStats).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Filled.BarChart, null)
            Column(Modifier.weight(1f)) {
                Text("סטטיסטיקה", style = MaterialTheme.typography.bodyLarge)
                Muted("Gemini היום ובשבוע האחרון, מה השרת שומר, והאתרים")
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
    Section("השרת")
    InfoRow(
        "כתובת",
        ltr(state.address) + if (state.addressCount > 1) " · ועוד ${state.addressCount - 1} כתובות שמורות, אם זו תפסיק לענות" else "",
    )
    InfoRow(
        "חיפוש ב־DHT",
        state.dhtFoundAt?.let { "מצא את השרת בכתובת חדשה ${ago(it)}" } ?: "עוד לא נדרש: השרת עונה בכתובות השמורות",
    )
    InfoRow("קריאה אחרונה של האתרים", state.lastRead?.let { ago(it) } ?: "עוד לא")
    InfoRow(
        "סיפורים",
        if (state.storiesOn) "מקובצים בשרת" else "כבויים בשרת: כל כתבה מוצגת לבדה",
    )
    InfoRow("גרסה", ltr(state.version))
    InfoRow("הטלפון", "${state.deviceName} · חשבון ${state.user} · התעודה בתוקף עד ${date(state.certificateExpires)}")
    var confirm by remember { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(top = 8.dp))
    OutlinedButton(onClick = { confirm = true }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { Text("ניתוק מהשרת") }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("לנתק את הטלפון מהשרת?") },
            text = { Text("המפתח והתעודה יימחקו מהטלפון, ובלי שרת אין חדשות חדשות. לחיבור מחדש צריך קוד חדש.") },
            confirmButton = { TextButton(onClick = { confirm = false; actions.onDisconnect() }) { Text("ניתוק") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
private fun Section(title: String) {
    HorizontalDivider()
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun InfoRow(title: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Muted(value)
    }
}


/** Latin text (a version, a time) kept left to right inside a Hebrew line. */
private fun ltr(text: String) = "\u2066$text\u2069"

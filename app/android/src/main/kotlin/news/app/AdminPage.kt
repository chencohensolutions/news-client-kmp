package news.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import news.app.server.Invite
import news.app.server.ServerDevice
import news.app.server.ServerUser
import news.app.server.UserRole
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

/** What the admin page does, through the view model ([NewsViewModel.loadUsers] and the rest). */
data class AdminActions(
    val load: () -> Unit = {},
    val invite: (user: String, role: UserRole) -> Unit = { _, _ -> },
    val dismissInvite: () -> Unit = {},
    val setRole: (user: String, role: UserRole) -> Unit = { _, _ -> },
    val revoke: (serial: String) -> Unit = {},
    val remove: (user: String) -> Unit = {},
)

/**
 * Settings › משתמשים ומכשירים, for the server's admins: its users and their devices, inviting someone (a one-time code
 * and its QR code, for a new user or another device of one), changing a role, revoking a device and removing a user,
 * the last two after a confirmation. The server checks every call too: to anyone else they answer FORBIDDEN.
 */
@Composable
fun AdminPage(state: AdminState, actions: AdminActions) {
    LaunchedEffect(Unit) { actions.load() }
    var inviting by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<Pair<ServerUser, ServerDevice>?>(null) }
    var removing by remember { mutableStateOf<ServerUser?>(null) }

    Text(
        "המשתמשים של השרת והמכשירים שלהם. מנהל משנה את הגדרות השרת לכולם ומנהל את המשתמשים; משתמש קורא, ובוחר לעצמו אילו אתרים יופיעו בפיד שלו.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    Button(onClick = { inviting = true }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Icon(Icons.Filled.PersonAdd, null)
        Text("הזמנה", Modifier.padding(start = 8.dp))
    }
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }

    val admins = state.users.orEmpty().count { it.role == UserRole.ADMIN }
    state.users?.forEach { user ->
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    roleName(user.role) + " · " + deviceCount(user.devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The server keeps its last admin: neither made a user nor removed.
            val lastAdmin = user.role == UserRole.ADMIN && admins <= 1
            TextButton(onClick = { actions.setRole(user.name, otherRole(user.role)) }, enabled = !lastAdmin && !state.loading) {
                Text(if (user.role == UserRole.ADMIN) "למשתמש" else "למנהל")
            }
            TextButton(onClick = { removing = user }, enabled = !lastAdmin && !state.loading) {
                Text("הסרה", color = if (lastAdmin) Color.Unspecified else MaterialTheme.colorScheme.error)
            }
        }
        user.devices.forEach { device ->
            ListItem(
                headlineContent = { Text(device.name + if (device.current) " (המכשיר הזה)" else "") },
                supportingContent = { Text(deviceLine(device)) },
                leadingContent = { Icon(Icons.Filled.PhoneAndroid, null) },
                trailingContent = {
                    if (device.revokedAt == null) {
                        TextButton(onClick = { revoking = user to device }, enabled = !state.loading) { Text("ביטול") }
                    }
                },
            )
        }
    }
    if (state.users?.isEmpty() == true) {
        Text("אין משתמשים.", modifier = Modifier.padding(16.dp))
    }

    if (inviting) InviteDialog(onDismiss = { inviting = false }, onInvite = { name, role -> inviting = false; actions.invite(name, role) })
    state.invite?.let { InviteShown(it, actions.dismissInvite) }
    revoking?.let { (user, device) ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text("לבטל את המכשיר?") },
            text = {
                Text(
                    "״${device.name}״ של ${user.name} לא יוכל להתחבר לשרת יותר. " +
                        (if (device.current) "זה המכשיר הזה: הוא יתנתק מהשרת. " else "") +
                        "כדי לחבר אותו שוב צריך הזמנה חדשה.",
                )
            },
            confirmButton = { TextButton(onClick = { revoking = null; actions.revoke(device.serial) }) { Text("ביטול המכשיר", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text("חזרה") } },
        )
    }
    removing?.let { user ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("להסיר את ${user.name}?") },
            text = { Text("המשתמש יוסר מהשרת, וכל ${deviceCount(user.devices.filter { it.revokedAt == null })} שלו יבוטלו. אי אפשר לבטל את זה.") },
            confirmButton = { TextButton(onClick = { removing = null; actions.remove(user.name) }) { Text("הסרה", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("חזרה") } },
        )
    }
}

/** A name and a role for the invite: a new name makes the user; an existing one gets a code for another device. */
@Composable
private fun InviteDialog(onDismiss: () -> Unit, onInvite: (String, UserRole) -> Unit) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.USER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הזמנה") },
        text = {
            Column {
                Text("שם חדש יוצר משתמש; שם קיים מקבל קוד למכשיר נוסף, ותפקידו לא משתנה.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(name, { name = it }, label = { Text("שם") }, singleLine = true, modifier = Modifier.padding(vertical = 8.dp))
                UserRole.entries.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = r == role, role = Role.RadioButton) { role = r },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = r == role, onClick = null, modifier = Modifier.padding(8.dp))
                        Text(roleName(r))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onInvite(name, role) }, enabled = name.isNotBlank()) { Text("יצירת קוד") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** The invite made: its QR code (scanned on the new phone's connect screen), its code and until when it works. */
@Composable
private fun InviteShown(invite: Invite, onDismiss: () -> Unit) {
    val qr = remember(invite.link) { qrBitmap(invite.link, 640) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הזמנה ל${invite.user}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(qr, "קוד QR להזמנה", Modifier.size(240.dp).background(Color.White))
                Text("סורקים במסך החיבור של האפליקציה במכשיר החדש, או מקלידים את הקוד:", style = MaterialTheme.typography.bodyMedium)
                SelectionContainer {
                    Text("⁦${invite.code}⁩", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                }
                Text(
                    "${roleName(invite.role)} · לשימוש אחד, עד ${time(invite.expiresAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text("⁦${invite.link}⁩", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגירה") } },
    )
}

internal fun roleName(role: UserRole) = if (role == UserRole.ADMIN) "מנהל" else "משתמש"

private fun otherRole(role: UserRole) = if (role == UserRole.ADMIN) UserRole.USER else UserRole.ADMIN

private fun deviceCount(devices: List<ServerDevice>): String {
    val live = devices.count { it.revokedAt == null }
    return when (live) {
        0 -> "אין מכשירים פעילים"
        1 -> "מכשיר אחד"
        else -> "$live מכשירים"
    }
}

/** A device's dates: enrolled, its certificate until, or when it was revoked; and how its key is kept. */
internal fun deviceLine(device: ServerDevice): String =
    listOfNotNull(
        "נרשם ${date(device.registeredAt)}",
        device.revokedAt?.let { "בוטל ${date(it)}" } ?: "בתוקף עד ${date(device.expiresAt)}",
        device.keyProtection.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

private val DATE = DateTimeFormatter.ofPattern("d.M.yyyy")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

private fun date(at: Instant) = java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()).atZone(ZoneId.systemDefault()).format(DATE)

private fun time(at: Instant) = java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()).atZone(ZoneId.systemDefault()).format(TIME)

/** [text]'s QR code as [size]×[size] ARGB pixels, black on white with a one-module margin. */
internal fun qrPixels(text: String, size: Int): IntArray {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    return IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
}

private fun qrBitmap(text: String, size: Int): ImageBitmap =
    Bitmap.createBitmap(qrPixels(text, size), size, size, Bitmap.Config.ARGB_8888).asImageBitmap()

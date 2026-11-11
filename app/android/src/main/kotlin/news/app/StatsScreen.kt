package news.app

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import news.app.server.GeminiDay
import news.app.server.ServerStats
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.time.Instant

/**
 * Settings › שרת החדשות › סטטיסטיקה: what the news server has done (`stats`). Gemini first (today's requests against
 * the free tier's daily budget, its tokens, when Google's day starts again, the last 7 days, the last summary run),
 * then what the server keeps, then each site's last read, failing ones marked in red with their error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(stats: ServerStats?, loading: Boolean, names: Map<String, String>, onRefresh: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    fun time(i: Instant) = DateFormat.getTimeFormat(context).format(Date(i.toEpochMilliseconds()))
    AppLayout {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("סטטיסטיקה") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") } },
                    actions = {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "רענון") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                if (stats == null) {
                    Muted(if (loading) "שואל את השרת…" else "השרת לא ענה. נסו שוב.", Modifier.padding(vertical = 16.dp))
                    return@Column
                }
                stats.lastLoop?.let { loop ->
                    Section("סבב אחרון")
                    Info("התחיל", "${ago(loop.startedAt)} (${time(loop.startedAt)}) · כל ${loop.everyMinutes} דקות")
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Figure("קריאת האתרים", seconds(loop.readSeconds))
                        Figure("קיבוץ", loop.groupSeconds?.let(::seconds) ?: "—")
                        Figure("Gemini", loop.summarySeconds?.let(::seconds) ?: "—")
                        Figure("סה״כ", seconds(loop.totalSeconds))
                    }
                    Muted(
                        "${"%,d".format(loop.headlines)} כותרות, ${loop.newArticles} כתבות חדשות" +
                            if (loop.failedSites > 0) " · ${loop.failedSites} אתרים נכשלו" else " · כל האתרים עבדו",
                    )
                }
                val gemini = stats.gemini
                Section("Gemini היום")
                if (gemini == null) {
                    Muted("כבוי בשרת: אין כתבות מסוכמות.")
                } else {
                    val today = gemini.days.firstOrNull { it.day == gemini.today } ?: GeminiDay(gemini.today, 0, 0, 0, 0, 0)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${today.requests}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text(" מתוך ${gemini.budget} בקשות", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    LinearProgressIndicator(
                        progress = { if (gemini.budget > 0) (today.requests.toFloat() / gemini.budget).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Muted("${ltr(gemini.model)} · היום של Google מתחיל מחדש ב־${time(googleDayStart(Instant.fromEpochMilliseconds(System.currentTimeMillis())))}")
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Figure("נכנסו", tokens(today.tokensIn))
                        Figure("מהמטמון", tokens(today.tokensCached))
                        Figure("יצאו", tokens(today.tokensOut))
                        Figure("חשיבה", tokens(today.tokensThinking))
                    }
                    Muted("טוקנים. \"מהמטמון\" הם מתוך אלה שנכנסו; החשיבה נספרת כפלט.", Modifier.padding(top = 4.dp))

                    Section("7 הימים האחרונים")
                    if (gemini.days.isEmpty()) Muted("עוד לא היו בקשות.") else Days(gemini.days, gemini.budget)

                    Section("הסיכום האחרון")
                    val run = gemini.lastRun
                    if (run == null) {
                        Muted("עוד לא רץ.")
                    } else {
                        Info("מתי", "${ago(run.at)} (${time(run.at)})")
                        Info("כתבות שנכתבו", "${run.written}" + if (run.waiting > 0) " · ${run.waiting} מחכות לריצות הבאות" else "")
                        run.error?.let { Info("נעצר", it, error = true) }
                    }
                }

                Section("בשרת")
                Info("כותרות", "%,d".format(stats.headlines))
                Info("כתבות שהורדו", "%,d".format(stats.articles))
                Info("סיפורים", "%,d".format(stats.stories) + (stats.groupedAt?.let { " · קובצו ${ago(it)}" } ?: ""))
                Info("כתבות מסוכמות", "%,d".format(stats.summaries))

                val failing = stats.sites.count { it.enabled && it.error != null }
                Section("אתרים" + if (failing > 0) " · $failing נכשלים" else "")
                stats.sites.sortedWith(compareBy({ it.error == null }, { !it.enabled }, { names[it.sourceId] ?: it.sourceId })).forEach { site ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(
                                when {
                                    !site.enabled -> MaterialTheme.colorScheme.outlineVariant
                                    site.error != null -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                },
                            ),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(names[site.sourceId] ?: site.sourceId, style = MaterialTheme.typography.bodyLarge)
                            Muted(
                                when {
                                    !site.enabled -> "כבוי"
                                    site.lastRunAt == null -> "עוד לא נקרא"
                                    else -> "${site.headlines} כותרות · נקרא ${ago(site.lastRunAt)}" +
                                        if (site.error != null) site.lastOkAt?.let { " · עבד לאחרונה ${ago(it)}" } ?: " · עוד לא עבד" else ""
                                },
                            )
                            site.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** The last days' requests as bars against the budget, newest first, with their tokens in and out. */
@Composable
private fun Days(days: List<GeminiDay>, budget: Int) {
    days.take(7).forEach { day ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ltr(day.day.drop(5)), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
            Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(if (budget > 0) (day.requests.toFloat() / budget).coerceIn(0f, 1f) else 0f)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Text("${day.requests}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
            Text("${tokens(day.tokensIn)} / ${tokens(day.tokensOut)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(78.dp))
        }
    }
    Muted("בקשות ביום, וטוקנים נכנסו / יצאו. הטוקנים נספרים מ־11 בנובמבר.")
}

@Composable
private fun Figure(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Info(title: String, value: String, error: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

/** Latin text kept left to right inside a Hebrew line. */
private fun ltr(text: String) = "⁦$text⁩"

/** Seconds, short: 0.8 ש׳, 34 ש׳, 2:05 דק׳. */
internal fun seconds(s: Double): String = when {
    s < 10 -> "%.1f ש׳".format(s)
    s < 60 -> "${s.toInt()} ש׳"
    else -> "%d:%02d דק׳".format(s.toInt() / 60, s.toInt() % 60)
}

/** A token count, short: 940, 12.4K, 1.2M. */
internal fun tokens(n: Int): String = when {
    n < 1_000 -> "$n"
    n < 1_000_000 -> "%.1fK".format(n / 1_000.0).replace(".0K", "K")
    else -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
}

/** When Google's next day starts after [now]: the free tier's quota counts by Pacific time, from midnight there. */
internal fun googleDayStart(now: Instant): Instant {
    val pacific = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()), ZoneId.of("America/Los_Angeles"))
    val next = pacific.truncatedTo(ChronoUnit.DAYS).plusDays(1)
    return Instant.fromEpochMilliseconds(next.toInstant().toEpochMilli())
}

package news.app.lab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import news.app.NewsTheme

/**
 * The UI lab (debug and uiLab builds only): the app's screens and suggested designs for them, with made-up data
 * ([PreviewData]) and no server, network calls or settings storage. The first page lists the screens ([labScreens]); a
 * screen lists its options, the first being what the app has now. Taps that would load or save do nothing.
 * `./scripts/ui-lab.sh <screen> [option]` opens one directly. See docs/ui-lab.md.
 */
class UiLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val askedScreen = intent.getStringExtra("screen")?.takeIf { id -> labScreens.any { it.id == id } }
        val askedOption = intent.getStringExtra("option")
        setContent {
            NewsTheme {
                // Keyed on what the intent asks for, so a saved state from an earlier launch doesn't override it.
                var screenId by rememberSaveable(askedScreen, askedOption) { mutableStateOf(askedScreen) }
                var optionId by rememberSaveable(askedScreen, askedOption) { mutableStateOf(askedOption) }
                val screen = labScreens.firstOrNull { it.id == screenId }
                val option = screen?.options?.firstOrNull { it.id == optionId }
                when {
                    option != null -> {
                        BackHandler { optionId = null }
                        option.content()
                    }
                    screen != null -> {
                        BackHandler { screenId = null }
                        LabList(
                            title = screen.title,
                            // The option's id leads its title ("a: …"), so the rows read as the choices they are.
                            rows = screen.options.mapIndexed { i, it -> LabRow("${it.id}: ${it.title}", if (i == 0) "Now in the app" else it.note, it.id) },
                            onOpen = { optionId = screen.options[it].id },
                        )
                    }
                    else -> LabList(
                        title = "UI lab",
                        rows = labScreens.map { LabRow(it.title, "${it.id} · ${optionCount(it.options.size)}", it.id) },
                        onOpen = {
                            screenId = labScreens[it].id
                            optionId = null
                        },
                    )
                }
            }
        }
    }
}

/** A screen in the lab. [options] starts with the screen as the app has it, then the suggestions. */
internal class LabScreen(val id: String, val title: String, val options: List<LabOption>)

/** One way to draw a screen: the current one or a suggestion. [id] is what `ui-lab.sh` takes. */
internal class LabOption(val id: String, val title: String, val note: String = "", val content: @Composable () -> Unit)

private class LabRow(val title: String, val note: String, val id: String)

private fun optionCount(n: Int) = if (n == 1) "Current only" else "Current + ${n - 1} more"

@Composable
private fun LabList(title: String, rows: List<LabRow>, onOpen: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        item {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        itemsIndexed(rows) { i, row ->
            Column(Modifier.fillMaxWidth().clickable { onOpen(i) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    row.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}

package pl.rytm.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.rytm.app.R

/** Schowek i udostępnianie tekstu. */
object TextTransfer {

    fun copy(context: Context, label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13+ sam pokazuje potwierdzenie skopiowania.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Skopiowano: $label", Toast.LENGTH_SHORT).show()
        }
    }

    fun paste(context: Context): String? {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    /** Systemowe „Udostępnij” – np. prosto do aplikacji ChatGPT, Gemini czy Claude. */
    fun share(context: Context, title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Blok kodu: tekst o stałej szerokości znaków, do zaznaczenia, z przyciskiem „Kopiuj” w rogu.
 * Długi tekst jest zwinięty – „Pokaż całość” rozwija go.
 */
@Composable
fun CodeBlock(
    text: String,
    label: String,
    modifier: Modifier = Modifier,
    collapsedMaxHeight: Int = 180,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .animateContentSize(),
        ) {
            SelectionContainer(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (expanded) 2_000.dp else collapsedMaxHeight.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 52.dp),
            ) {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = { TextTransfer.copy(context, label, text) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                Icon(painterResource(R.drawable.ic_copy), contentDescription = "Kopiuj: $label")
            }
        }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Zwiń" else "Pokaż całość") }
    }
}

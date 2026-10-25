package pl.rytm.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.rytm.app.RytmApp
import pl.rytm.app.ui.importer.ImportRoutineScreen
import pl.rytm.app.ui.importer.ImportViewModel
import pl.rytm.app.ui.theme.RytmTheme
import java.io.File

/**
 * Ekran „Rutyna z tekstu” renderowany naprawdę (Robolectric, grafika natywna): polecenie dla AI
 * z przyciskiem kopiowania, wklejona rutyna, podgląd i utworzenie rutyny w bazie.
 * Zrzuty ekranu trafiają do app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = RytmApp::class, sdk = [35], qualifiers = "w411dp-h1400dp-xxhdpi")
class ImportScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Rysuje okno aktywności do bitmapy (grafika natywna Robolectric) i zapisuje PNG. */
    private fun save(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `prompt block, pasted routine, preview and creation`() {
        val app = ApplicationProvider.getApplicationContext<RytmApp>()
        val vm = ImportViewModel(app)
        var createdId: Long? = null
        vm.setRequirements("Wstaję 6:30, wychodzę 7:20. Prysznic, śniadanie, pakowanie plecaka.")
        compose.setContent { RytmTheme { ImportRoutineScreen(vm, onBack = {}, onCreated = { createdId = it }) } }
        compose.waitForIdle()
        compose.onNodeWithText("Kopiuj polecenie").assertExists()
        compose.onNodeWithText("Wyślij do aplikacji AI").assertExists()
        save("import-1-polecenie-ai")

        vm.insertExample()
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Rutyna jest poprawna."))
        compose.waitForIdle()
        save("import-2-podglad")

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Utwórz rutynę"))
        compose.onNodeWithText("Utwórz rutynę").assertIsEnabled().performClick()
        // Zapis idzie przez Room (wątek w tle) i wraca na wątek główny – przepychamy go ręcznie.
        val end = System.currentTimeMillis() + 15_000
        while (createdId == null && System.currentTimeMillis() < end) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        org.junit.Assert.assertNotNull("Rutyna nie została utworzona", createdId)
        val saved = runBlocking { app.container.routines.getRoutine(createdId!!) }!!
        assertEquals("Poranek przed szkołą", saved.name)
        assertEquals(9, saved.tasks.size)
    }
}

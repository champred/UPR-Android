package ly.mens.rndpkmn

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.dabomstew.pkrandom.Randomizer
import com.dabomstew.pkrandom.Settings
import com.dabomstew.pkrandom.romhandlers.Gen1RomHandler
import com.dabomstew.pkrandom.romhandlers.RomHandler
import ly.mens.rndpkmn.R.string.saverombutton
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.io.File


class RandomizerActivity: AppCompatActivity() {
    companion object {
        const val FILE_PATH = "FILE_PATH"
        const val HANDLER_FACTORY = "HANDLER_FACTORY"
    }
    lateinit private var saveDir: File
    lateinit private var outputName: String
    lateinit private var romHandler: RomHandler
    val operations = mutableListOf<Settings.()->Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val romPath = intent.getStringExtra(FILE_PATH) ?: throw IllegalStateException("rom path not provided")
        saveDir = File(romPath).parentFile
        val handlerFactory = Class.forName(intent.getStringExtra(HANDLER_FACTORY)).newInstance()
            as? RomHandler.Factory ?: throw IllegalStateException("handler factory not provided")
        romHandler = handlerFactory.create(random).apply {
            // TODO: Make async
            loadRom(romPath)
            outputName = "$romName Random.$defaultExtension"
        }
        RandomizerActivityUI().setContentView(this)
    }

    fun saveRom() {
        Randomizer(settings, romHandler)
                .randomize(File(saveDir, outputName).canonicalPath)
    }

    val settings = Settings()
    val timeBasedEncounters get() = romHandler.hasTimeBasedEncounters()
    val heldItems get() = romHandler !is Gen1RomHandler
}

class RandomizerActivityUI: AnkoComponent<RandomizerActivity> {
    override fun createView(ui: AnkoContext<RandomizerActivity>) = ui.apply { owner.apply {
        // TODO: Tabs?
        scrollView {
            verticalLayout {
                wildPokemon(settings, timeBasedEncounters, heldItems)
                button(saverombutton) { onClick {
                    saveRom()
                } }
            }
        }
    }}.view
}

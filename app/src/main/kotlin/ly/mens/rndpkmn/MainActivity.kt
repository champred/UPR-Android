package ly.mens.rndpkmn

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dabomstew.pkrandom.Settings
import com.dabomstew.pkrandom.Utils.testForRequiredConfigs
import com.dabomstew.pkrandom.romhandlers.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import java.io.File

private val defaultDir by lazy {
    // TODO: Default to /sdcard if ROMs directory not present
    File(Environment.getExternalStorageDirectory(), "ROMs").canonicalPath
    // TODO: Allow user to choose a directory more easily
}

private val romHandlerFactories = listOf(
        Gen1RomHandler.Factory(),
        Gen2RomHandler.Factory(),
        Gen3RomHandler.Factory(),
        Gen4RomHandler.Factory(),
        Gen5RomHandler.Factory())

class MainActivity : AppCompatActivity() {
    private var saveDir: File? = null
    private var romHandler: RomHandler? = null
    var loading = false
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("pkrandom.root", filesDir.canonicalPath)
        if (BuildConfig.DEBUG) {
            testForRequiredConfigs()
        }
        checkPermission()
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            // TODO: UX flow
        }
    }

    fun listRoms(directory: File): List<File>? {
        if (!directory.exists() || !directory.isDirectory) {
            toast(R.string.error_invalid_dir)
            return null
        }
        val files = directory.listFiles(File::isRomFile)
        if (files.isEmpty()) {
            longToast(R.string.error_no_roms, directory)
        }
        return files.toList()
    }

    fun loadRom(romPath: String) {
        val handler = romHandlerFactories.firstOrNull { it.isLoadable(romPath) }?.create(random)
        if (handler == null) {
            longToast(R.string.error_invalid_rom, romPath)
            return
        }
        loading = true
        GlobalScope.async {
            handler.loadRom(romPath)
            runOnUiThread {
                saveDir = File(romPath).parentFile
                romHandler = handler
                loading = false
                toast(R.string.rom_loaded, handler.romName)
            }
        }
    }

    fun saveRom() {
        // TODO: Move this to separate activity with randomization options
        val saveDir = this.saveDir
        if (saveDir == null) {
            toast(R.string.noromloaded)
            return
        }
        GlobalScope.async {
            romHandler?.apply {
                randomizeFieldItems(settings)
                if (canChangeStaticPokemon()) {
                    randomizeStaticPokemon(settings)
                    randomizeBasicTwoEvosStarters(settings)
                }
                area1to1Encounters(settings)
                // TODO: Allow choosing file name and output directory
                val output = File(saveDir, "$romName Random.$defaultExtension").canonicalPath
                saveRom()
                runOnUiThread {
                    toast(R.string.rom_saved, output)
                }
            }
        }
    }
}

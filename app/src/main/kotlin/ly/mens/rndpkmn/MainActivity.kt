package ly.mens.rndpkmn

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dabomstew.pkrandom.Utils.testForRequiredConfigs
import com.dabomstew.pkrandom.romhandlers.*
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("pkrandom.root", filesDir.canonicalPath)
        if (BuildConfig.DEBUG) {
            testForRequiredConfigs()
        }
        setContent { RandomizerApp() }
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
        val handler = RandomizerSettings.romHandlerFactories.firstOrNull { it.isLoadable(romPath) }
        if (handler == null) {

            longToast(R.string.error_invalid_rom, romPath)
            return
        }
    }
}

package ly.mens.rndpkmn

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import com.dabomstew.pkrandom.Utils.testForRequiredConfigs
import com.dabomstew.pkrandom.romhandlers.*
import ly.mens.rndpkmn.R.string.*
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk25.coroutines.onClick
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("pkrandom.root", filesDir.canonicalPath)
        if (BuildConfig.DEBUG) {
            testForRequiredConfigs()
        }
        MainActivityUI().setContentView(this)
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
            toast(error_invalid_dir)
            return null
        }
        val files = directory.listFiles(File::isRomFile)
        if (files.isEmpty()) {
            longToast(error_no_roms, directory)
        }
        return files.toList()
    }

    fun loadRom(romPath: String) {
        val handler = romHandlerFactories.firstOrNull { it.isLoadable(romPath) }
        if (handler == null) {

            longToast(error_invalid_rom, romPath)
            return
        }
        startActivity<RandomizerActivity>(
                RandomizerActivity.FILE_PATH to romPath,
                RandomizerActivity.HANDLER_FACTORY to handler.javaClass.name
        )
    }
}

class MainActivityUI: AnkoComponent<MainActivity> {
    override fun createView(ui: AnkoContext<MainActivity>) = ui.apply {
        verticalLayout {
            val romsDir = editText(defaultDir)
            button(openrombutton) {
                onClick {
                    owner.listRoms(File(romsDir.text.toString()))?.apply {
                        selector(ctx.getString(openrombutton),
                                map(File::nameWithoutExtension)) { _, index ->
                            owner.loadRom(get(index).absolutePath)
                        }
                    }
                }
            }
        }
    }.view
}

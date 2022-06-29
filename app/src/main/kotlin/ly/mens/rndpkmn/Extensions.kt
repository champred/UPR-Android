package ly.mens.rndpkmn

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import com.dabomstew.pkrandom.RandomSource
import com.dabomstew.pkrandom.Utils
import java.io.File
import java.lang.reflect.Field

val Field.id get() = name.replaceFirstChar { it.uppercase() }
val Enum<*>.id get() = name.split("_").joinToString("") {
    it.replaceRange(1 until it.length, it.substring(1).lowercase())
}
val Triple<Any, Any, Any>.fileName get() = "$first-$second.$third"

val File.isRomFile: Boolean get() {
    if (!isFile) { return false }
    try {
        Utils.validateRomFile(this)
        return true
    }
    catch (e: Utils.InvalidROMException) {
        return false
    }
}

val random get() = RandomSource.instance()

fun Context.toast(@StringRes resId: Int, vararg formatArgs: Any) =
        Toast.makeText(this, getString(resId, *formatArgs), Toast.LENGTH_SHORT)
fun Context.longToast(@StringRes resId: Int, vararg formatArgs: Any) =
        Toast.makeText(this, getString(resId, *formatArgs), Toast.LENGTH_LONG)

fun Context.loadFromUri(uri: Uri, file: File) {
    //copy selected file to app directory if it doesn't exist
    if (!file.isRomFile) {
        openFileOutput(file.name, Context.MODE_PRIVATE).use {
            val source = contentResolver.openInputStream(uri)
            if (source != null) {
                source.copyTo(it)
                source.close()
            }
        }
    }
}

fun Context.saveToUri(uri: Uri, file: File) {
    //copy temporary file to selected path
    contentResolver.openOutputStream(uri).use {
        val source = openFileInput(file.name)
        if (it != null) {
            source.copyTo(it)
        }
        source.close()
        //clean up temporary file
        deleteFile(file.name)
    }
}

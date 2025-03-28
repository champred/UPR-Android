package ly.mens.rndpkmn

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.text.parseAsHtml
import com.dabomstew.pkrandom.Utils
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Field

val Field.id get() = name.replaceFirstChar { it.uppercase() }
val Enum<*>.id get() = name.split("_").joinToString("") {
    it.replaceRange(1 until it.length, it.substring(1).lowercase())
}
val Triple<Any, Any, Any>.fileName get() = "$first-$second.$third"

val File.isRomFile: Boolean get() {
    return isFile && try {
        Utils.validateRomFile(this)
        true
    } catch (e: Utils.InvalidROMException) {
        e.printStackTrace()
        false
    }
}

fun Context.toast(@StringRes resId: Int, vararg formatArgs: Any, length: Int = Toast.LENGTH_LONG) {
    val text = if (formatArgs.isNotEmpty()) {
        getString(resId, *formatArgs)
    } else {
        getString(resId)
    }
    toast(text, length)
}
fun Context.toast(text: String, length: Int = Toast.LENGTH_LONG) {
    Toast.makeText(this, text.parseAsHtml(), length).show()
}

fun Context.renderText(@StringRes resId: Int) = renderText(getString(resId))
fun renderText(text: String) = text.parseAsHtml()

fun Context.loadFromUri(uri: Uri, file: File) {
    //copy selected file to app directory
    if (!file.exists()) {
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
        val source = try {
            openFileInput(file.name)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return
        }
        if (it != null) {
            source.copyTo(it)
        }
        source.close()
    }
}

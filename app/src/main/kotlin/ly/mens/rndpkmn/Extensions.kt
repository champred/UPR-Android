package ly.mens.rndpkmn

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import com.dabomstew.pkrandom.RandomSource
import com.dabomstew.pkrandom.Utils
import java.io.File

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

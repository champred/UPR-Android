@file:Suppress("NOTHING_TO_INLINE")

package ly.mens.rndpkmn

import android.content.Context
import android.support.annotation.StringRes
import android.view.ViewManager
import com.dabomstew.pkrandom.RandomSource
import com.dabomstew.pkrandom.Utils
import org.jetbrains.anko.longToast
import org.jetbrains.anko.radioButton
import org.jetbrains.anko.toast
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
        toast(getString(resId, *formatArgs))
fun Context.longToast(@StringRes resId: Int, vararg formatArgs: Any) =
        longToast(getString(resId, *formatArgs))


inline fun ViewManager.radioButton(title: Int, tooltip: Int) = radioButton {
    with(context.resources) {
        id = title
        text = getString(title)
        contentDescription = getString(tooltip)
    }
}
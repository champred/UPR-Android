package ly.mens.rndpkmn

import android.view.ViewManager
import com.dabomstew.pkrandom.Settings
import com.dabomstew.pkrandom.Settings.WildPokemonMod.*
import org.jetbrains.anko.radioGroup
import org.jetbrains.anko.sdk25.coroutines.onCheckedChange
import org.jetbrains.anko.textView


fun ViewManager.wildPokemon(settings: Settings, timeBasedEncounters: Boolean, heldItems: Boolean) {
    textView(R.string.wildpokemonpanel_border_title)
    val type = radioGroup {
        radioButton(R.string.wpunchangedrb, R.string.wpunchangedrb_tooltip)
        radioButton(R.string.wprandomrb, R.string.wprandomrb_tooltip)
        radioButton(R.string.wparea11rb, R.string.wparea11rb_tooltip)
        radioButton(R.string.wpglobalrb, R.string.wpglobalrb_tooltip)
    }

    type.onCheckedChange { _, checkedId ->
        settings.wildPokemonMod = when(checkedId) {
            R.string.wpunchangedrb -> UNCHANGED
            R.string.wprandomrb -> RANDOM
            R.string.wparea11rb -> AREA_MAPPING
            R.string.wpglobalrb -> GLOBAL_MAPPING
            else -> throw IllegalStateException()
        }
    }
}
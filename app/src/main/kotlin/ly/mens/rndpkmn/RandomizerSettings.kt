package ly.mens.rndpkmn

import com.dabomstew.pkrandom.Settings
import com.dabomstew.pkrandom.SettingsMod
import com.dabomstew.pkrandom.romhandlers.*
import java.lang.reflect.Modifier

object RandomizerSettings {
	val settings = Settings()
	val romHandlerFactories = listOf(
			Gen1RomHandler.Factory(),
			Gen2RomHandler.Factory(),
			Gen3RomHandler.Factory(),
			Gen4RomHandler.Factory(),
			Gen5RomHandler.Factory()
	)

	init {
		settings.javaClass.declaredFields.forEach { f ->
			if (!Modifier.isStatic(f.modifiers)) {
				val type = f.type
				f.isAccessible = true
				if (type.isPrimitive) {
					val suffix = when (type.name) {
						"boolean" -> "CheckBox"
						"int" -> "Slider"
						else -> ""
					}
					for (p in SettingsPrefix.values()) {
						if (p.search(f, suffix)) break
					}
				} else if (type.enclosingClass == SettingsMod::class.java) {
					val prefix = type.getField("PREFIX").get(null) as String
					type.enumConstants.forEach {
						for (p in SettingsPrefix.values()) {
							if (prefix == p.prefix && p.searchEnum(f, it as Enum<*>)) break
						}
					}
				}
			}
		}
	}
}

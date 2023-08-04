package ly.mens.rndpkmn.settings

import ly.mens.rndpkmn.BuildConfig

enum class SettingsPreset {
	NONE {
		override val preset: String? = null
	},
	STANDARD,
	ULTIMATE,
	KAIZO,
	SURVIVAL,
	DOUBLES;

	open val preset: String? get() {
		val name = RandomizerSettings.romName ?: return null
		val prefix: String = when (RandomizerSettings.currentGen) {
			1 -> "RBY"
			2 -> "GSC"
			3 -> if ("Fire" in name || "Leaf" in name) "FRLG" else "RSE"
			4 -> if ("Heart" in name || "Soul" in name) "HGSS" else "DPPT"
			5 -> if ("2" in name) "B2W2" else "BW"
			else -> return null
		}
		return try {
			BuildConfig::class.java.getField("${prefix}_${this.name}").get(null) as? String
		} catch (e: NoSuchFieldException) {
			null
		}
	}
}

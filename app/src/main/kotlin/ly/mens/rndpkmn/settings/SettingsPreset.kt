package ly.mens.rndpkmn.settings

import ly.mens.rndpkmn.BuildConfig

enum class SettingsPreset {
	NONE {
		override fun getPreset(): String? {
			return null
		}
	},
	STANDARD,
	ULTIMATE,
	KAIZO,
	SURVIVAL;

	open fun getPreset(): String? {
		val name = RandomizerSettings.romName ?: return null
		val prefix: String = when (RandomizerSettings.currentGen) {
			1 -> "RBY"
			3 -> {
				if ("Red" in name || "Green" in name) "FRLG"
				else if ("Ruby" in name || "Sapphire" in name || "Emerald" in name) "RSE"
				else return null
			}
			4 -> {
				if ("Gold" in name || "Silver" in name) "HGSS"
				else if ("Diamond" in name || "Pearl" in name || "Platinum" in name) "DPPT"
				else return null
			}
			5 -> {
				if ("2" in name) "B2W2"
				else if ("Black" in name || "White" in name) "BW"
				else return null
			}
			else -> return null
		}
		return try {
			BuildConfig::class.java.getField("${prefix}_${this.name}").get(null) as? String
		} catch (e: NoSuchFieldException) {
			null
		}
	}
}

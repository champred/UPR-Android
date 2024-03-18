package ly.mens.rndpkmn.settings

import com.dabomstew.pkrandom.RandomSource
import ly.mens.rndpkmn.BuildConfig
import ly.mens.rndpkmn.makeTriple

enum class SettingsPreset {
	NONE {
		override val preset: String? get() {
			selected = true
			return null
		}
		override var selected = true
	},
	STANDARD,
	ULTIMATE,
	KAIZO,
	SURVIVAL,
	DOUBLES,
	STARTERS {
		override val preset: String? get() {
			return with(RandomizerSettings) {
				if (useNatDex) {
					selected = true
					val starters = pickStarters(handler!!, RandomSource.instance())
					currentStarters = makeTriple(*starters.toTypedArray())
					BuildConfig.END_STARTERS
				} else {
					NONE.selected = true
					null
				}
			}
		}
	};

	open var selected = false
		protected set(value) {
			if (value) {
				for (e in entries) {
					e.selected = false
				}
			}
			field = value
		}
	open val preset: String? get() {
		val name = RandomizerSettings.romName ?: return null
		val prefix: String = when (RandomizerSettings.currentGen) {
			1 -> "RBY"
			2 -> "GSC"
			3 -> if ("Fire" in name || "Leaf" in name) "FRLG" else "RSE"
			4 -> if ("Heart" in name || "Soul" in name) "HGSS" else "DPPT"
			5 -> if ("2" in name) "B2W2" else "BW"
			9 -> "END"
			else -> return null
		}
		return try {
			selected = true
			BuildConfig::class.java.getField("${prefix}_${this.name}").get(null) as? String
		} catch (e: NoSuchFieldException) {
			NONE.selected = true
			selected = false
			null
		}
	}

	override fun toString(): String {
		return "${name}: $selected"
	}
}

package ly.mens.rndpkmn.settings

import com.dabomstew.pkrandom.RandomSource
import com.dabomstew.pkrandom.romhandlers.AbstractRomHandler
import ly.mens.rndpkmn.BuildConfig
import ly.mens.rndpkmn.makeTriple

enum class SettingsPreset {
	NONE {
		override val preset: String? get() {
			selected = true
			AbstractRomHandler.useSmartAI = false;
			return null
		}
		override var selected = true
	},
	STANDARD,
	ULTIMATE,
	KAIZO,
	SURVIVAL,
	DOUBLES,
	SUPER,
	STARTERS {
		override val preset: String? get() {
			AbstractRomHandler.useSmartAI = false;
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
			9 -> if ("Emerald" in name) "END" else "FRND"
			else -> return null
		}
		return try {
			selected = true
			BuildConfig::class.java.getField("${prefix}_${this.name}").get(null) as? String
		} catch (e: NoSuchFieldException) {
			NONE.selected = true
			selected = false
			null
		} finally {
			AbstractRomHandler.useSmartAI = this === SUPER
		}
	}

	override fun toString(): String {
		return "${name}: $selected"
	}
}

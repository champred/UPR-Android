package ly.mens.rndpkmn.settings

import com.dabomstew.pkrandom.constants.Species
import ly.mens.rndpkmn.BuildConfig
import ly.mens.rndpkmn.makeTriple

enum class SettingsPreset {
	NONE {
		override val preset: String? = null
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
					starters.shuffle()
					currentStarters = makeTriple(*starters.map(handler!!.pokemon::get).toTypedArray())
					BuildConfig.END_STARTERS
				} else null
			}
		}
		private val starters = intArrayOf(
				Species.bulbasaur, Species.charmander, Species.squirtle,
				Species.chikorita, Species.cyndaquil, Species.totodile,
				Species.treecko, Species.torchic, Species.mudkip,
				Species.turtwig, Species.chimchar, Species.piplup,
				Species.snivy, Species.tepig, Species.oshawott,
				Species.chespin, Species.fennekin, Species.froakie,
				Species.rowlet, Species.litten, Species.popplio,
				Species.grookey, Species.scorbunny, Species.sobble,
				Species.sprigatito, Species.fuecoco, Species.quaxly,
				)
	};

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
			BuildConfig::class.java.getField("${prefix}_${this.name}").get(null) as? String
		} catch (e: NoSuchFieldException) {
			null
		}
	}
}

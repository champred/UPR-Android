package ly.mens.rndpkmn.settings

import androidx.annotation.StringRes
import com.dabomstew.pkrandom.romhandlers.RomHandler
import ly.mens.rndpkmn.BuildConfig
import ly.mens.rndpkmn.R
import ly.mens.rndpkmn.id
import java.lang.reflect.Field

enum class SettingsPrefix(val prefix: String, @StringRes val title: Int) {
	STATS("pbs", R.string.pbsPanel_title),
	TYPES("pt", R.string.ptPanel_title),
	ABILITIES("pa", R.string.paPanel_title) {
		override fun isSupported(handler: RomHandler): Boolean {
			return handler.generationOfPokemon() >= 3
		}
	},
	EVOS("pe", R.string.pePanel_title),
	STARTERS("sp", R.string.spPanel_title),
	STATICS("stp", R.string.stpPanel_title) {
		override fun isSupported(handler: RomHandler): Boolean {
			return handler.canChangeStaticPokemon()
		}
	},
	TRADES("igt", R.string.igtPanel_title),
	MOVES("md", R.string.mdPanel_title),
	MOVESETS("pms", R.string.pmsPanel_title),
	TRAINERS("tp", R.string.tpPanel_title),
	ENCOUNTERS("wp", R.string.wpPanel_title),
	TM_MOVES("tm", R.string.tmPanel_title),
	TM_COMPAT("thc", R.string.thcPanel_title),
	TUTORS("mt", R.string.mtPanel_title) {
		override fun isSupported(handler: RomHandler): Boolean {
			return handler.hasMoveTutors()
		}
	},
	TUTOR_COMPAT("mtc", R.string.mtcPanel_title) {
		override fun isSupported(handler: RomHandler): Boolean {
			return handler.hasMoveTutors()
		}
	},
	FIELD_ITEMS("fi", R.string.fiPanel_title),
	SHOP_ITEMS("sh", R.string.shPanel_title) {
		override fun isSupported(handler: RomHandler): Boolean {
			return handler.hasShopRandomization()
		}
	},
	PICKUP_ITEMS("pu", R.string.puPanel_title) {
		override fun isSupported(handler: RomHandler): Boolean {
			return handler.abilitiesPerPokemon() > 0
		}
	},
	TOTEMS("totp", R.string.totpPanel_title) {
		override fun isSupported(handler: RomHandler): Boolean {
			return handler.generationOfPokemon() == 7
		}
	};

	val strings: Map<String, String> = BuildConfig.PREFIX_MAP[prefix]!!
	val props: MutableMap<String, Field> = mutableMapOf()
	val groups: MutableMap<String, MutableMap<String, Field>> = mutableMapOf()

	open fun isSupported(handler: RomHandler): Boolean {
		return true
	}

	fun search(fld: Field, suffix: String): Boolean {
		val template = "GUI.$prefix${fld.id}$suffix.text"
		if (template in strings.keys) {
			props[template] = fld
			return true
		}
		return false
	}
	fun <T : Enum<T>> searchEnum(fld: Field, enm: Enum<T>): Boolean {
		val template = "GUI.$prefix${enm.id}RadioButton.text"
		if (template in strings.keys) {
			val subtitle = try {
				fld.type.getField("TITLE").get(null) as String
			} catch (e: NoSuchFieldException) {
				""
			}
			groups.putIfAbsent(subtitle, mutableMapOf())
			groups[subtitle]!![template] = fld
			return true
		}
		return false
	}

	companion object {
		val emptyPrefix: Map<String, String> = BuildConfig.PREFIX_MAP[""]!!

		fun customNamesTitle(name: String): String? {
			val template = "CustomNamesEditorDialog.${name}SP.TabConstraints.tabTitle"
			return emptyPrefix[template]
		}
	}
}

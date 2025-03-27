package ly.mens.rndpkmn.settings

import android.util.Log
import com.dabomstew.pkrandom.*
import com.dabomstew.pkrandom.constants.*
import com.dabomstew.pkrandom.constants.GlobalConstants.HIGHEST_POKEMON_GEN
import com.dabomstew.pkrandom.pokemon.ExpCurve
import com.dabomstew.pkrandom.pokemon.GenRestrictions
import com.dabomstew.pkrandom.pokemon.Pokemon
import com.dabomstew.pkrandom.romhandlers.*
import ly.mens.rndpkmn.BuildConfig
import ly.mens.rndpkmn.Trie
import ly.mens.rndpkmn.fileName
import ly.mens.rndpkmn.makeTriple
import java.io.*
import java.lang.NumberFormatException
import java.lang.reflect.Field
import java.util.Random
import java.util.ResourceBundle.getBundle
import kotlin.properties.Delegates
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

object RandomizerSettings : Settings() {
	private val romHandlerFactories = listOf(
			Gen1RomHandler.Factory(),
			Gen2RomHandler.Factory(),
			Gen3RomHandler.Factory(),
			Gen4RomHandler.Factory(),
			Gen5RomHandler.Factory()
	)
	private lateinit var romHandlerFactory: RomHandler.Factory
	private lateinit var romHandler: RomHandler
	val handler: RomHandler? get() = if (RandomizerSettings::romHandler.isInitialized) romHandler else null
	private lateinit var inputFile: File
	//allocate enough space to accommodate large logs
	val outputLog = ByteArrayOutputStream(1024 * 1024)
	private val emptyLog = object : OutputStream() {
		override fun write(b: Int) {
			return
		}
	}
	private val random = object : ThreadLocal<Random>() {
		override fun initialValue() = Random()
		fun seed(seed: Long) = get()!!.apply { setSeed(seed) }
	}
	//limit the number of ROMs based on amount of available memory
	val romLimit: Int get() {
		val rt = Runtime.getRuntime()
		val mem = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
		return (mem / inputFile.length() / 3L).toInt().coerceAtLeast(1)
	}
	val currentGen: Int
		get() {
			return if (RandomizerSettings::romHandler.isInitialized) {
				if (useNatDex) 9
				else romHandler.generationOfPokemon()
			} else 0
		}
	val isValid: Boolean get() = if (RandomizerSettings::romHandler.isInitialized) romHandler.isRomValid else false
	var currentStarters: Triple<Pokemon?, Pokemon?, Pokemon?> = Triple(null, null, null)
		set(value) {
			val (first, second, third) = value
			customStarters = intArrayOf(
					first?.number?.plus(1) ?: 1,
					second?.number?.plus(1) ?: 1,
					third?.number?.plus(1) ?: 1
			)
			field = value
		}
	val pokeTrie = Trie()
	var currentSeed by Delegates.notNull<Long>()
	lateinit var romFileName: String
	val versionString: String get() = "$VERSION$this"
	val selections: MutableMap<Field?, List<Any>> = mutableMapOf()
	val nameLists: List<Pair<String, MutableList<String>>>
	var allowExtraBerries = true
		set(value) {
		// berries without useful battle effects
		if (value) {
			// include extra berries
			Gen4Constants.nonBadItems.unbanRange(Items.figyBerry, BuildConfig.BERRY_COUNT_OLD)
			Gen4Constants.nonBadItems.banRange(Items.figyBerry, BuildConfig.BERRY_COUNT_NEW)
		} else {
			// don't include extra berries
			Gen4Constants.nonBadItems.banRange(Items.figyBerry, BuildConfig.BERRY_COUNT_OLD)
		}
		field = value
	}
	var useNatDex = false
		set(value) {
			Gen3RomHandler.useNatDex = value
			Gen3RomHandler.loadROMInfo(if (value) "nd_offsets.ini" else "gen3_offsets.ini")
			field = value
		}

	private const val TAG = "Settings"

	init {
		this::class.memberProperties.filterNot {
			"MegaEvo" in it.name || "AltForme" in it.name
		}.forEach { prop ->
			val field = prop.javaField
			if (field != null && field.declaringClass == Settings::class.java) {
				val type = field.type
				field.isAccessible = true
				if (type.isPrimitive) {
					val suffix = when (type.name) {
						"boolean" -> "CheckBox"
						"int" -> "Slider"
						else -> ""
					}
					for (pre in SettingsPrefix.entries) {
						if (pre.search(field, suffix)) break
					}
				} else if (type.enclosingClass == Settings::class.java) {
					val prefix = type.getField("PREFIX").get(null) as String
					type.enumConstants.forEach {
						for (pre in SettingsPrefix.entries) {
							if (prefix == pre.prefix && pre.searchEnum(field, it as Enum<*>)) break
						}
					}
				}
			}
		}
		customNames = try {
			FileFunctions.getCustomNames()
		} catch (e: IOException) {
			Log.e(TAG, "Unable to load custom names.", e)
			CustomNamesSet()
		}
		nameLists = customNames::class.memberProperties.reversed().associate {
			it.isAccessible = true
			val title = SettingsPrefix.customNamesTitle(it.name) ?: it.name
			@Suppress("UNCHECKED_CAST")
			val names = it.javaField?.get(customNames) as? MutableList<String> ?: mutableListOf()
			title to names
		}.toList()
		currentRestrictions = GenRestrictions()
	}

	fun loadRom(file: File): Boolean {
		inputFile = file
		currentSeed = RandomSource.pickSeed()
		romFileName = Triple(
				file.nameWithoutExtension.substringAfter(':'),
				currentSeed.toString(16),
				file.extension
		).fileName
		try {
			romHandlerFactory = romHandlerFactories.first { it.isLoadable(file.absolutePath) }
			romHandler = createRomHandler(RandomSource.instance())
			romName = romHandler.romName
		} catch (e: Exception) {
			Log.e(TAG, "${file.name} cannot be loaded.", e)
			return false
		}

		currentStarters = makeTriple(*(romHandler.starters?.toTypedArray() ?: arrayOfNulls(3)))
		pokeTrie.clear()
		for (i in 1 until romHandler.pokemon.size) {
			pokeTrie.insert(romHandler.pokemon[i].name)
		}

		with (SettingsPrefix.STARTERS.props) {
			if (!romHandler.supportsStarterHeldItems()) {
				remove("GUI.spRandomizeStartersHeldItemsCheckBox.text")
				remove("GUI.spBanBadRandomStarterHeldItemsCheckBox.text")
			} else {
				put("GUI.spRandomizeStartersHeldItemsCheckBox.text", RandomizerSettings::randomizeStartersHeldItems.javaField!!)
				put("GUI.spBanBadRandomStarterHeldItemsCheckBox.text", RandomizerSettings::banBadRandomStarterHeldItems.javaField!!)
			}
		}

		val baseStatGenerationNumbers = arrayOfNulls<Int>(4.coerceAtMost(HIGHEST_POKEMON_GEN - currentGen))
		var gen = 6.coerceAtLeast(currentGen + 1)
		updateBaseStatsToGeneration = gen
		for (i in baseStatGenerationNumbers.indices) {
			baseStatGenerationNumbers[i] = gen++
		}
		selections[::updateBaseStats.javaField] = baseStatGenerationNumbers.filterNotNull()

		val moveGenerationNumbers = arrayOfNulls<Int>(HIGHEST_POKEMON_GEN - currentGen)
		gen = currentGen + 1
		updateMovesToGeneration = gen
		for (i in moveGenerationNumbers.indices) {
			moveGenerationNumbers[i] = gen++
		}
		selections[::updateMoves.javaField] = moveGenerationNumbers.filterNotNull()

		val expCurves = if (currentGen < 3) {
			arrayOf(ExpCurve.MEDIUM_FAST, ExpCurve.MEDIUM_SLOW, ExpCurve.FAST, ExpCurve.SLOW)
		} else {
			arrayOf(ExpCurve.MEDIUM_FAST, ExpCurve.MEDIUM_SLOW, ExpCurve.FAST, ExpCurve.SLOW, ExpCurve.ERRATIC, ExpCurve.FLUCTUATING)
		}
		selections[::standardizeEXPCurves.javaField] = expCurves.toList()

		return true
	}

	fun saveRom(file: File): Boolean {
		outputLog.reset()
		return saveRom(file, currentSeed, romHandler, PrintStream(outputLog))
	}

	fun saveRom(file: File, seed: Long, log: OutputStream? = null): Boolean {
		val handler = try {
			createRomHandler(random.seed(seed))
		} catch (e: Exception) {
			Log.e(TAG, "Failed to create ROM handler.", e)
			log?.close()
			return false
		}
		return saveRom(file, seed, handler, PrintStream(log ?: emptyLog))
	}

	private fun saveRom(file: File, seed: Long, handler: RomHandler, log: PrintStream): Boolean {
		return try {
			handler.setLog(log)
			Randomizer(this, handler, getBundle("com.dabomstew.pkrandom.newgui.Bundle"), false)
				.randomize(file.absolutePath, log, seed)
			true
		} catch (e: Exception) {
			Log.e(TAG, "Failed to randomize ROM.", e)
			e.printStackTrace(log)
			false
		} finally {
			log.close()
		}
	}

	private fun createRomHandler(rand: Random): RomHandler {
		return romHandlerFactory.create(rand).apply {
			val loaded = loadRom(inputFile.absolutePath)
			if (!loaded) {
				throw Exception("Unable to load ROM")
			}
			if (SettingsPreset.STARTERS.selected) {
				pickStarters(this, rand)
				startersMod = StartersMod.UNCHANGED
			}
			if (!isRomValid) {
				Log.i(TAG, "The loaded ROM is not valid.")
			}
		}
	}

	fun reloadRomHandler() {
		romHandler = try {
			createRomHandler(RandomSource.instance())
		} catch (e: Exception) {
			Log.e(TAG, "Failed to create new ROM handler.", e)
			romHandler
		}
	}

	fun getPokemon(name: String): Pokemon? {
		if (name.isBlank()) {
			return null
		}
		for (i in 1 until romHandler.pokemon.size) {
			if (romHandler.pokemon[i].name.equals(name, true)) return romHandler.pokemon[i]
		}
		return null
	}

	fun updateFromString(text: String): Boolean {
		val other: Settings
		try {
			val version = text.substring(0..2).toInt()
			var config = text.substring(3)
			if (version < VERSION) {
				config = SettingsUpdater().update(version, config)
			} else if (version > VERSION) {
				throw Exception("Settings string created by newer version.")
			}
			other = fromString(config)
		} catch (e: Exception) {
			Log.e(TAG, "Failed to load settings string.", e)
			return false
		}
		if (romName != other.romName) {
			Log.i(TAG, "Settings string created for ${other.romName} but $romName is loaded.")
		}
		other.javaClass.declaredFields.forEach { field ->
			field.isAccessible = true
			if (field == ::customStarters.javaField && SettingsPreset.STARTERS.selected)
				return@forEach
			//don't copy null values
			field.get(other)?.let { field.set(this, it) }
		}
		return true
	}

	fun updateSeed(text: String, base: Int): Boolean {
		return try {
			currentSeed = text.toLong(base)
			true
		} catch (e: NumberFormatException) {
			Log.e(TAG, "Unable to convert seed to number.", e)
			false
		}
	}

	fun pickStarters(handler: RomHandler, rand: Random): List<Pokemon> {
		val copy = starters.toMutableList().apply { shuffle(rand) }
		handler.starters = copy.map(handler.pokemon::get).take(3)
		return handler.starters
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

	val limits: Map<Field?, ClosedFloatingPointRange<Float>> = mapOf(
			::staticLevelModifier.javaField to -50f..50f,
			::guaranteedMoveCount.javaField to 2f..4f,
			::movesetsGoodDamagingPercent.javaField to 0f..100f,
			::trainersForceFullyEvolvedLevel.javaField to 30f..65f,
			::trainersLevelModifier.javaField to -50f..60f,
			::totemLevelModifier.javaField to -50f..50f,
			::minimumCatchRateLevel.javaField to 1f..5f,
			::wildLevelModifier.javaField to -50f..60f,
			::tmsGoodDamagingPercent.javaField to 0f..100f,
			::tutorsGoodDamagingPercent.javaField to 0f..100f,
			::additionalBossTrainerPokemon.javaField to 0f..5f,
			::additionalImportantTrainerPokemon.javaField to 0f..5f,
			::additionalRegularTrainerPokemon.javaField to 0f..5f,
			::eliteFourUniquePokemonNumber.javaField to 0f..2f,
	)
	val toggles: Map<Field?, Field?> = mapOf(
			::staticLevelModifier.javaField to ::staticLevelModified.javaField,
			::guaranteedMoveCount.javaField to ::startWithGuaranteedMoves.javaField,
			::movesetsGoodDamagingPercent.javaField to ::movesetsForceGoodDamaging.javaField,
			::trainersForceFullyEvolvedLevel.javaField to ::trainersForceFullyEvolved.javaField,
			::trainersLevelModifier.javaField to ::trainersLevelModified.javaField,
			::totemLevelModifier.javaField to ::totemLevelsModified.javaField,
			::minimumCatchRateLevel.javaField to ::useMinimumCatchRate.javaField,
			::wildLevelModifier.javaField to ::wildLevelsModified.javaField,
			::tmsGoodDamagingPercent.javaField to ::tmsForceGoodDamaging.javaField,
			::tutorsGoodDamagingPercent.javaField to ::tutorsForceGoodDamaging.javaField,
			::additionalBossTrainerPokemon.javaField to null,
			::additionalImportantTrainerPokemon.javaField to null,
			::additionalRegularTrainerPokemon.javaField to null,
			::eliteFourUniquePokemonNumber.javaField to null,
			::updateBaseStats.javaField to ::updateBaseStatsToGeneration.javaField,
			::standardizeEXPCurves.javaField to ::selectedEXPCurve.javaField,
			::updateMoves.javaField to ::updateMovesToGeneration.javaField,
	)
	val generations: Map<Field?, IntRange> = mapOf(
			::randomizeInGameTradesOTs.javaField to 2..HIGHEST_POKEMON_GEN,
			::randomizeInGameTradesIVs.javaField to 2..HIGHEST_POKEMON_GEN,
			::randomizeInGameTradesItems.javaField to 2..HIGHEST_POKEMON_GEN,
			::evolutionMovesForAll.javaField to 7..HIGHEST_POKEMON_GEN,
			::doubleBattleMode.javaField to 3..HIGHEST_POKEMON_GEN,
			::additionalRegularTrainerPokemon.javaField to 3..HIGHEST_POKEMON_GEN,
			::additionalImportantTrainerPokemon.javaField to 3..HIGHEST_POKEMON_GEN,
			::additionalBossTrainerPokemon.javaField to 3..HIGHEST_POKEMON_GEN,
			::randomizeHeldItemsForRegularTrainerPokemon.javaField to 3..HIGHEST_POKEMON_GEN,
			::randomizeHeldItemsForImportantTrainerPokemon.javaField to 3..HIGHEST_POKEMON_GEN,
			::randomizeHeldItemsForBossTrainerPokemon.javaField to 3..HIGHEST_POKEMON_GEN,
			::consumableItemsOnlyForTrainerPokemon.javaField to 3..HIGHEST_POKEMON_GEN,
			::sensibleItemsOnlyForTrainerPokemon.javaField to 3..HIGHEST_POKEMON_GEN,
			::highestLevelOnlyGetsItemsForTrainerPokemon.javaField to 3..HIGHEST_POKEMON_GEN,
			::eliteFourUniquePokemonNumber.javaField to 3..HIGHEST_POKEMON_GEN,
			::trainersBlockEarlyWonderGuard.javaField to 3..HIGHEST_POKEMON_GEN,
			::shinyChance.javaField to 7..7,
			::betterTrainerMovesets.javaField to 3..HIGHEST_POKEMON_GEN,
			::randomizeWildPokemonHeldItems.javaField to 2..HIGHEST_POKEMON_GEN,
			::banBadRandomWildPokemonHeldItems.javaField to 2..HIGHEST_POKEMON_GEN,
			::balanceShakingGrass.javaField to 5..5,
			::randomizeMoveCategory.javaField to 4..HIGHEST_POKEMON_GEN,
	)
}

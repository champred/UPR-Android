package ly.mens.rndpkmn

import android.util.Log
import com.dabomstew.pkrandom.*
import com.dabomstew.pkrandom.constants.GlobalConstants
import com.dabomstew.pkrandom.pokemon.ExpCurve
import com.dabomstew.pkrandom.pokemon.GenRestrictions
import com.dabomstew.pkrandom.pokemon.Pokemon
import com.dabomstew.pkrandom.romhandlers.*
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
	val handler: RomHandler? get() = if (::romHandler.isInitialized) romHandler else null
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
	val currentGen: Int get() = if (::romHandler.isInitialized) romHandler.generationOfPokemon() else 1
	val isValid: Boolean get() = if (::romHandler.isInitialized) romHandler.isRomValid else false
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

	private const val TAG = "Settings"

	init {
		this::class.memberProperties.filterNot {
			"MegaEvo" in it.name || "AltForme" in it.name
		}.forEach { prop ->
			val fld = prop.javaField
			if (fld != null) {
				val type = fld.type
				fld.isAccessible = true
				if (type.isPrimitive) {
					val suffix = when (type.name) {
						"boolean" -> "CheckBox"
						"int" -> "Slider"
						else -> ""
					}
					for (pre in SettingsPrefix.values()) {
						if (pre.search(fld, suffix)) break
					}
				} else if (type.enclosingClass == Settings::class.java) {
					val prefix = type.getField("PREFIX").get(null) as String
					type.enumConstants.forEach {
						for (pre in SettingsPrefix.values()) {
							if (prefix == pre.prefix && pre.searchEnum(fld, it as Enum<*>)) break
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
		romFileName = Triple(file.nameWithoutExtension.substringAfter(':'), currentSeed.toString(16), file.extension).fileName
		try {
			romHandlerFactory = romHandlerFactories.first { it.isLoadable(file.absolutePath) }
			romHandler = createRomHandler(RandomSource.instance())
			romName = romHandler.romName
		} catch (e: Exception) {
			Log.e(TAG, "${file.name} cannot be loaded.", e)
			return false
		}

		val (first, second, third) = romHandler.starters
		currentStarters = Triple(first, second, third)
		pokeTrie.clear()
		for (i in 1 until romHandler.pokemon.size) {
			pokeTrie.insert(romHandler.pokemon[i].name)
		}

		val baseStatGenerationNumbers = arrayOfNulls<Int>(Math.min(3, GlobalConstants.HIGHEST_POKEMON_GEN - currentGen))
		var j = Math.max(6, currentGen + 1)
		updateBaseStatsToGeneration = j
		for (i in baseStatGenerationNumbers.indices) {
			baseStatGenerationNumbers[i] = j++
		}
		selections[::updateBaseStats.javaField] = baseStatGenerationNumbers.filterNotNull()

		val moveGenerationNumbers = arrayOfNulls<Int>(GlobalConstants.HIGHEST_POKEMON_GEN - currentGen)
		j = currentGen + 1
		updateMovesToGeneration = j
		for (i in moveGenerationNumbers.indices) {
			moveGenerationNumbers[i] = j++
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
			return false
		}
		return saveRom(file, seed, handler, PrintStream(log ?: emptyLog))
	}

	private fun saveRom(file: File, seed: Long, handler: RomHandler, log: PrintStream): Boolean {
		return try {
			handler.setLog(log)
			Randomizer(this, handler, getBundle("com.dabomstew.pkrandom.newgui.Bundle"), false).randomize(file.absolutePath, log, seed)
			true
		} catch (e: Exception) {
			Log.e(TAG, "Failed to randomize ROM.", e)
			false
		} finally {
			log.close()
		}
	}

	private fun createRomHandler(random: Random): RomHandler {
		return romHandlerFactory.create(random).apply {
			loadRom(inputFile.absolutePath)
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
		other.javaClass.declaredFields.forEach { fld ->
			fld.isAccessible = true
			//don't copy null values
			fld.get(other)?.let { fld.set(this, it) }
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

	val limits: Map<Field?, ClosedFloatingPointRange<Float>> = mapOf(
			::staticLevelModifier.javaField to -50f..50f,
			::guaranteedMoveCount.javaField to 2f..4f,
			::movesetsGoodDamagingPercent.javaField to 0f..100f,
			::trainersForceFullyEvolvedLevel.javaField to 30f..65f,
			::trainersLevelModifier.javaField to -50f..50f,
			::totemLevelModifier.javaField to -50f..50f,
			::minimumCatchRateLevel.javaField to 1f..4f,
			::wildLevelModifier.javaField to -50f..50f,
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
			::randomizeStartersHeldItems.javaField to 2..3,
			::banBadRandomStarterHeldItems.javaField to 2..3,
			::randomizeInGameTradesOTs.javaField to 2..7,
			::randomizeInGameTradesIVs.javaField to 2..7,
			::randomizeInGameTradesItems.javaField to 2..7,
			::evolutionMovesForAll.javaField to 7..7,
			::doubleBattleMode.javaField to 3..7,
			::additionalRegularTrainerPokemon.javaField to 3..7,
			::additionalImportantTrainerPokemon.javaField to 3..7,
			::additionalBossTrainerPokemon.javaField to 3..7,
			::randomizeHeldItemsForRegularTrainerPokemon.javaField to 3..7,
			::randomizeHeldItemsForImportantTrainerPokemon.javaField to 3..7,
			::randomizeHeldItemsForBossTrainerPokemon.javaField to 3..7,
			::consumableItemsOnlyForTrainerPokemon.javaField to 3..7,
			::sensibleItemsOnlyForTrainerPokemon.javaField to 3..7,
			::highestLevelOnlyGetsItemsForTrainerPokemon.javaField to 3..7,
			::eliteFourUniquePokemonNumber.javaField to 3..7,
			::trainersBlockEarlyWonderGuard.javaField to 3..7,
			::shinyChance.javaField to 7..7,
			::betterTrainerMovesets.javaField to 3..7,
			::randomizeWildPokemonHeldItems.javaField to 2..7,
			::banBadRandomWildPokemonHeldItems.javaField to 2..7,
			::balanceShakingGrass.javaField to 5..5,
			::fullHMCompat.javaField to 1..6,
			::randomizeMoveCategory.javaField to 4..7,
	)
}

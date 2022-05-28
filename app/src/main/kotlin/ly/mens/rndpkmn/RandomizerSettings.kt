package ly.mens.rndpkmn

import com.dabomstew.pkrandom.RandomSource
import com.dabomstew.pkrandom.Randomizer
import com.dabomstew.pkrandom.Settings
import com.dabomstew.pkrandom.SettingsMod
import com.dabomstew.pkrandom.constants.GlobalConstants
import com.dabomstew.pkrandom.pokemon.ExpCurve
import com.dabomstew.pkrandom.pokemon.Pokemon
import com.dabomstew.pkrandom.romhandlers.*
import java.io.File
import java.lang.reflect.Field
import kotlin.properties.Delegates
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

object RandomizerSettings : Settings() {
	val romHandlerFactories = listOf(
			Gen1RomHandler.Factory(),
			Gen2RomHandler.Factory(),
			Gen3RomHandler.Factory(),
			Gen4RomHandler.Factory(),
			Gen5RomHandler.Factory()
	)
	private var _romHandler: RomHandler? = null
	val romHandler: RomHandler get() = _romHandler!!
	private var _currentStarters: Triple<Pokemon, Pokemon, Pokemon>? = null
	var currentStarters: Triple<Pokemon, Pokemon, Pokemon>
		set(value) {
			customStarters = intArrayOf(value.first.number, value.second.number, value.third.number)
			_currentStarters = value
		}
		get() {
			val poke = Pokemon().apply {
				name = ""
			}
			return _currentStarters ?: Triple(poke, poke, poke)
		}
	val pokeTrie = Trie()
	private var seed by Delegates.notNull<Long>()
	val limits: Map<Field?, ClosedFloatingPointRange<Float>> = mapOf(
			this::staticLevelModifier.javaField to -50f..50f,
			this::guaranteedMoveCount.javaField to 2f..4f,
			this::movesetsGoodDamagingPercent.javaField to 0f..100f,
			this::trainersForceFullyEvolvedLevel.javaField to 30f..65f,
			this::trainersLevelModifier.javaField to -50f..50f,
			this::totemLevelModifier.javaField to -50f..50f,
			this::minimumCatchRateLevel.javaField to 1f..4f,
			this::wildLevelModifier.javaField to -50f..50f,
			this::tmsGoodDamagingPercent.javaField to 0f..100f,
			this::tutorsGoodDamagingPercent.javaField to 0f..100f,
			this::additionalBossTrainerPokemon.javaField to 0f..5f,
			this::additionalImportantTrainerPokemon.javaField to 0f..5f,
			this::additionalRegularTrainerPokemon.javaField to 0f..5f,
			this::eliteFourUniquePokemonNumber.javaField to 0f..2f,
	)
	val toggles: Map<Field?, Field?> = mapOf(
			this::staticLevelModifier.javaField to this::staticLevelModified.javaField,
			this::guaranteedMoveCount.javaField to this::startWithGuaranteedMoves.javaField,
			this::movesetsGoodDamagingPercent.javaField to this::movesetsForceGoodDamaging.javaField,
			this::trainersForceFullyEvolvedLevel.javaField to this::trainersForceFullyEvolved.javaField,
			this::trainersLevelModifier.javaField to this::trainersLevelModified.javaField,
			this::totemLevelModifier.javaField to this::totemLevelsModified.javaField,
			this::minimumCatchRateLevel.javaField to this::useMinimumCatchRate.javaField,
			this::wildLevelModifier.javaField to this::wildLevelsModified.javaField,
			this::tmsGoodDamagingPercent.javaField to this::tmsForceGoodDamaging.javaField,
			this::tutorsGoodDamagingPercent.javaField to this::tutorsForceGoodDamaging.javaField,
			this::additionalBossTrainerPokemon.javaField to null,
			this::additionalImportantTrainerPokemon.javaField to null,
			this::additionalRegularTrainerPokemon.javaField to null,
			this::eliteFourUniquePokemonNumber.javaField to null,
			this::updateBaseStats.javaField to this::updateBaseStatsToGeneration.javaField,
			this::standardizeEXPCurves.javaField to this::selectedEXPCurve.javaField,
			this::updateMoves.javaField to this::updateMovesToGeneration.javaField,
	)
	val selections: MutableMap<Field?, List<Any>> = mutableMapOf()

	init {
		this::class.memberProperties.forEach { prop ->
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
				} else if (type.enclosingClass == SettingsMod::class.java) {
					val prefix = type.getField("PREFIX").get(null) as String
					type.enumConstants.forEach {
						for (pre in SettingsPrefix.values()) {
							if (prefix == pre.prefix && pre.searchEnum(fld, it as Enum<*>)) break
						}
					}
				}
			}
		}
	}

	fun loadRom(file: File) {
		seed = RandomSource.pickSeed()
		romName = "${file.nameWithoutExtension.substringAfter(':')} ${seed.toString(16)}.${file.extension}"
		_romHandler = romHandlerFactories.firstOrNull() { it.isLoadable(file.absolutePath) }?.create(random)
		if (_romHandler == null) return
		romHandler.loadRom(file.absolutePath)

		val (first, second, third) = romHandler.starters
		currentStarters = Triple(first, second, third)
		for (i in 1 until romHandler.pokemon.size) {
			pokeTrie.insert(romHandler.pokemon[i].name)
		}

		val baseStatGenerationNumbers = arrayOfNulls<Int>(Math.min(3, GlobalConstants.HIGHEST_POKEMON_GEN - romHandler.generationOfPokemon()))
		var j = Math.max(6, romHandler.generationOfPokemon() + 1)
		updateBaseStatsToGeneration = j
		for (i in baseStatGenerationNumbers.indices) {
			baseStatGenerationNumbers[i] = j++
		}
		selections[this::updateBaseStats.javaField] = baseStatGenerationNumbers.filterNotNull()

		val moveGenerationNumbers = arrayOfNulls<Int>(GlobalConstants.HIGHEST_POKEMON_GEN - romHandler.generationOfPokemon())
		j = romHandler.generationOfPokemon() + 1
		updateMovesToGeneration = j
		for (i in moveGenerationNumbers.indices) {
			moveGenerationNumbers[i] = j++
		}
		selections[this::updateMoves.javaField] = moveGenerationNumbers.filterNotNull()

		val expCurves = if (romHandler.generationOfPokemon() < 3) {
			arrayOf(ExpCurve.MEDIUM_FAST, ExpCurve.MEDIUM_SLOW, ExpCurve.FAST, ExpCurve.SLOW)
		} else {
			arrayOf(ExpCurve.MEDIUM_FAST, ExpCurve.MEDIUM_SLOW, ExpCurve.FAST, ExpCurve.SLOW, ExpCurve.ERRATIC, ExpCurve.FLUCTUATING)
		}
		selections[this::standardizeEXPCurves.javaField] = expCurves.toList()
	}

	fun saveRom(file: File) {
		Randomizer(this, romHandler, null, false).randomize(file.absolutePath, System.out, seed)
	}

	fun getPokemon(name: String): Pokemon? {
		for (i in 1 until romHandler.pokemon.size) {
			if (romHandler.pokemon[i].name.equals(name, true)) return romHandler.pokemon[i]
		}
		return null
	}
}

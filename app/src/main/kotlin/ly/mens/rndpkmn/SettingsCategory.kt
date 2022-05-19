package ly.mens.rndpkmn

import androidx.annotation.StringRes

import ly.mens.rndpkmn.SettingsPrefix.*

enum class SettingsCategory(@StringRes val title: Int) {
	TRAITS(R.string.pokemonTraitsPanel_title) {
		override val prefixes = arrayOf(STATS, TYPES, ABILITIES, EVOS)
	}, SST(R.string.startersStaticsTradesPanel) {
		override val prefixes = arrayOf(STARTERS, STATICS, TRADES)
	}, MM(R.string.movesMovesetsPanel) {
		override val prefixes = arrayOf(MOVES, MOVESETS)
	}, FOE(R.string.foePokemonPanel_title) {
		override val prefixes = arrayOf(TRAINERS, TOTEMS)
	}, WILD(R.string.wildPokemonPanel_title) {
		override val prefixes = arrayOf(ENCOUNTERS)
	}, THT(R.string.tmsHMsTutorsPanel_title) {
		override val prefixes = arrayOf(TM_MOVES, TM_COMPAT, TUTORS, TUTOR_COMPAT)
	}, ITEMS(R.string.itemsPanel_title) {
		override val prefixes = arrayOf(FIELD_ITEMS, SHOP_ITEMS, PICKUP_ITEMS)
	};

	abstract val prefixes: Array<SettingsPrefix>
}

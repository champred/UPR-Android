package com.dabomstew.pkrandom;

public class SettingsMod {
	public enum BaseStatisticsMod {
		UNCHANGED, SHUFFLE, RANDOM;
		public static final String PREFIX = "pbs";
	}

	public enum ExpCurveMod {
		LEGENDARIES, STRONG_LEGENDARIES, ALL;
		public static final String PREFIX = "pbs";
		public static final String TITLE = "GUI.pbsStandardizeEXPCurvesCheckBox.text";
	}

	public enum TypesMod {
		UNCHANGED, RANDOM_FOLLOW_EVOLUTIONS, RANDOM_COMPLETELY;
		public static final String PREFIX = "pt";
	}

	public enum AbilitiesMod {
		UNCHANGED, RANDOM;
		public static final String PREFIX = "pa";
	}

	public enum EvolutionsMod {
		UNCHANGED, RANDOM, RANDOM_EVERY_LEVEL;
		public static final String PREFIX = "pe";
	}

	public enum StartersMod {
		UNCHANGED, CUSTOM, RANDOM_COMPLETELY, RANDOM_TWO_EVOLUTIONS;
		public static final String PREFIX = "sp";
	}

	public enum StaticPokemonMod {
		UNCHANGED, SWAP_LEGENDARIES_SWAP_STANDARDS, RANDOM_COMPLETELY, RANDOM_SIMILAR_STRENGTH;
		public static final String PREFIX = "stp";
	}

	public enum InGameTradesMod {
		UNCHANGED, RANDOMIZE_GIVEN_POKEMON_ONLY, RANDOMIZE_BOTH_REQUESTED_GIVEN;
		public static final String PREFIX = "igt";
	}

	public enum MovesetsMod {
		UNCHANGED, RANDOM_PREFER_SAME_TYPE, RANDOM_COMPLETELY, METRONOME_ONLY_MODE;
		public static final String PREFIX = "pms";
	}

	public enum TrainersMod {
		UNCHANGED, RANDOM, RANDOM_EVEN_DISTRIBUTION, RANDOM_EVEN_DISTRIBUTION_MAIN, TYPE_THEMED, TYPE_THEMED_ELITE4_GYMS;
		public static final String PREFIX = "tp";
	}

	public enum TotemPokemonMod {
		UNCHANGED, RANDOM, RANDOM_SIMILAR_STRENGTH;
		public static final String PREFIX = "totp";
	}

	public enum AllyPokemonMod {
		UNCHANGED, RANDOM, SIMILAR_STRENGTH;
		public static final String PREFIX = "totp";
		public static final String TITLE = "GUI.totpAllyPanel.title";
	}

	public enum AuraMod {
		UNCHANGED, RANDOM, SAME_STRENGTH;
		public static final String PREFIX = "totp";
		public static final String TITLE = "GUI.totpAuraPanel.title";
	}

	public enum WildPokemonMod {
		UNCHANGED, RANDOM, AREA_MAPPING, GLOBAL_MAPPING;
		public static final String PREFIX = "wp";
	}

	public enum WildPokemonRestrictionMod {
		NONE, SIMILAR_STRENGTH, CATCH_EM_ALL, TYPE_THEME_AREAS;
		public static final String PREFIX = "wp";
		public static final String TITLE = "GUI.wpARPanel.title";
	}

	public enum TMsMod {
		UNCHANGED, RANDOM;
		public static final String PREFIX = "tm";
	}

	public enum TMsHMsCompatibilityMod {
		UNCHANGED, RANDOM_PREFER_SAME_TYPE, RANDOM_COMPLETELY, FULL_COMPATIBILITY;
		public static final String PREFIX = "thc";
	}

	public enum MoveTutorMovesMod {
		UNCHANGED, RANDOM;
		public static final String PREFIX = "mt";
	}

	public enum MoveTutorsCompatibilityMod {
		UNCHANGED, RANDOM_PREFER_SAME_TYPE, RANDOM_COMPLETELY, FULL_COMPATIBILITY;
		public static final String PREFIX = "mtc";
	}

	public enum FieldItemsMod {
		UNCHANGED, SHUFFLE, RANDOM, RANDOM_EVEN_DISTRIBUTION;
		public static final String PREFIX = "fi";
	}

	public enum ShopItemsMod {
		UNCHANGED, SHUFFLE, RANDOM;
		public static final String PREFIX = "sh";
	}

	public enum PickupItemsMod {
		UNCHANGED, RANDOM;
		public static final String PREFIX = "pu";
	}
}

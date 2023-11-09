package com.dabomstew.pkrandom.pokemon;

/*----------------------------------------------------------------------------*/
/*--  EvolutionType.java - describes what process is necessary for an       --*/
/*--                       evolution to occur                               --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import com.dabomstew.pkrandom.romhandlers.Gen3RomHandler;

public enum EvolutionType {
    /* @formatter:off */
    LEVEL(1, 1, 4, 4, 4, 4, 4) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    STONE(2, 2, 7, 7, 8, 8, 8),
    TRADE(3, 3, 5, 5, 5, 5, 5),
    TRADE_ITEM(-1, 3, 6, 6, 6, 6, 6),
    HAPPINESS(-1, 4, 1, 1, 1, 1, 1),
    HAPPINESS_DAY(-1, 4, 2, 2, 2, 2, 2),
    HAPPINESS_NIGHT(-1, 4, 3, 3, 3, 3, 3),
    LEVEL_ATTACK_HIGHER(-1, 5, 8, 8, 9, 9, 9) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_DEFENSE_HIGHER(-1, 5, 10, 10, 11, 11, 11) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_ATK_DEF_SAME(-1, 5, 9, 9, 10, 10, 10) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_LOW_PV(-1, -1, 11, 11, 12, 12, 12) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_HIGH_PV(-1, -1, 12, 12, 13, 13, 13) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_CREATE_EXTRA(-1, -1, 13, 13, 14, 14, 14) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_IS_EXTRA(-1, -1, 14, 14, 15, 15, 15) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_HIGH_BEAUTY(-1, -1, 15, 15, 16, 16, 16) {
        @Override
        public boolean skipSplitEvo() {
            return true;
        }
    },
    STONE_MALE_ONLY(-1, -1, -1, 16, 17, 17, 17) {
        @Override
        public int toIndex(int generation) {
            if (Gen3RomHandler.useNatDex) {
                indexNumbers = new int[]{-1, -1, 18, 16, 17, 17, 17};
            }
            return super.toIndex(generation);
        }
    },
    STONE_FEMALE_ONLY(-1, -1, -1, 17, 18, 18, 18) {
        @Override
        public int toIndex(int generation) {
            if (Gen3RomHandler.useNatDex) {
                indexNumbers = new int[]{-1, -1, 19, 17, 18, 18, 18};
            }
            return super.toIndex(generation);
        }
    },
    LEVEL_ITEM_DAY(-1, -1, -1, 18, 19, 19, 19),
    LEVEL_ITEM_NIGHT(-1, -1, -1, 19, 20, 20, 20),
    LEVEL_WITH_MOVE(-1, -1, -1, 20, 21, 21, 21),
    LEVEL_WITH_OTHER(-1, -1, -1, 21, 22, 22, 22),
    LEVEL_MALE_ONLY(-1, -1, -1, 22, 23, 23, 23) {
        @Override
        public int toIndex(int generation) {
            if (Gen3RomHandler.useNatDex) {
                indexNumbers = new int[]{-1, -1, 17, 22, 23, 23, 23};
            }
            return super.toIndex(generation);
        }
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_FEMALE_ONLY(-1, -1, -1, 23, 24, 24, 24) {
        @Override
        public int toIndex(int generation) {
            if (Gen3RomHandler.useNatDex) {
                indexNumbers = new int[]{-1, -1, 16, 23, 24, 24, 24};
            }
            return super.toIndex(generation);
        }
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_ELECTRIFIED_AREA(-1, -1, -1, 24, 25, 25, 25),
    LEVEL_MOSS_ROCK(-1, -1, -1, 25, 26, 26, 26),
    LEVEL_ICY_ROCK(-1, -1, -1, 26, 27, 27, 27),
    TRADE_SPECIAL(-1, -1, -1, -1, 7, 7, 7),
    FAIRY_AFFECTION(-1, -1, -1, -1, -1, 29, 29),
    LEVEL_WITH_DARK(-1, -1, -1, -1, -1, 30, 30) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_UPSIDE_DOWN(-1, -1, -1, -1, -1, 28, 28) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_RAIN(-1, -1, -1, -1, -1, 31, 31) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_DAY(-1, -1, -1, -1, -1, 32, 32) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_NIGHT(-1, -1, -1, -1, -1, 33, 33) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_FEMALE_ESPURR(-1, -1, -1, -1, -1, 34, 34) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_GAME(-1, -1, -1, -1, -1, -1, 36) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_DAY_GAME(-1, -1, -1, -1, -1, -1, 37) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_NIGHT_GAME(-1, -1, -1, -1, -1, -1, 38) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_SNOWY(-1, -1, -1, -1, -1, -1, 39) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_DUSK(-1, -1, -1, -1, -1, -1, 40) {
        @Override
        public boolean usesLevel() {
            return true;
        }
    },
    LEVEL_NIGHT_ULTRA(-1, -1, -1, -1, -1, -1, 41) {
        @Override
        public boolean usesLevel() {
            return true;
        }
        @Override
        public boolean skipSplitEvo() {
            return true;
        }
    },
    STONE_ULTRA(-1, -1, -1, -1, -1, -1, 42) {
        @Override
        public boolean skipSplitEvo() {
            return true;
        }
    },
    NONE(-1, -1, -1, -1, -1, -1, -1);
    /* @formatter:on */

    protected int[] indexNumbers;
    private static EvolutionType[][] reverseIndexes;

    private static void init(int generation) {
        reverseIndexes = new EvolutionType[8][50];
        for (EvolutionType et : EvolutionType.values()) {
            //convert index numbers
            et.toIndex(generation);
            for (int i = 0; i < et.indexNumbers.length; i++) {
                if (et.indexNumbers[i] > 0 && reverseIndexes[i][et.indexNumbers[i]] == null) {
                    reverseIndexes[i][et.indexNumbers[i]] = et;
                }
            }
        }
    }

    EvolutionType(int... indexes) {
        this.indexNumbers = indexes;
    }

    public int toIndex(int generation) {
        return indexNumbers[generation - 1];
    }

    public static EvolutionType fromIndex(int generation, int index) {
        if (reverseIndexes == null)
            init(generation);
        return reverseIndexes[generation - 1][index];
    }

    public boolean usesLevel() {
        return false;
    }

    public boolean skipSplitEvo() {
        return false;
    }
}

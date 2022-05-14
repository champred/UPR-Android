package com.dabomstew.pkrandom.pokemon;

/*----------------------------------------------------------------------------*/
/*--  Move.java - represents a move usable by Pokemon.                      --*/
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

public class Move {
    public class StatChange {
        public StatChangeType type;
        public int stages;
        public double percentChance;
    }

    public String name;
    public int number;
    public int internalId;
    public int power;
    public int pp;
    public double hitratio;
    public Type type;
    public StatChangeMoveType statChangeMoveType = StatChangeMoveType.NONE_OR_UNKNOWN;
    public StatChange[] statChanges = new StatChange[3];
    public StatusMoveType statusMoveType = StatusMoveType.NONE_OR_UNKNOWN;
    public StatusType statusType = StatusType.NONE;
    public double statusPercentChance;
    public double flinchPercentChance;
    public int effectIndex;
    public int target;
    public MoveCategory category;
    public double hitCount = 1; // not saved, only used in randomized move powers.
    public double secondaryEffectChance;
    public int priority;
    public boolean makesContact;

    public Move() {
        // Initialize all statStageChanges to something sensible so that we don't need to have
        // each RomHandler mess with them if they don't need to.
        for (int i = 0; i < this.statChanges.length; i++) {
            this.statChanges[i] = new StatChange();
            this.statChanges[i].type = StatChangeType.NONE;
        }
    }

    public String toString() {
        return "#" + number + " " + name + " - Power: " + power + ", Base PP: " + pp + ", Type: " + type + ", Hit%: "
                + (hitratio) + ", Effect: " + effectIndex + ", Priority: " + priority;
    }

}

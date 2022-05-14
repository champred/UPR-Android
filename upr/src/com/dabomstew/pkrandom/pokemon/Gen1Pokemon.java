package com.dabomstew.pkrandom.pokemon;

/*----------------------------------------------------------------------------*/
/*--  Gen1Pokemon.java - represents an individual Gen 1 Pokemon. Used to    --*/
/*--                 handle things related to stats because of the lack     --*/
/*--                 of the Special split in Gen 1.                         --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
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

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Gen1Pokemon extends Pokemon {

    public Gen1Pokemon() {
        shuffledStatsOrder = Arrays.asList(0, 1, 2, 3, 4);
    }

    @Override
    public void copyShuffledStatsUpEvolution(Pokemon evolvesFrom) {
        // If stats were already shuffled once, un-shuffle them
        shuffledStatsOrder = Arrays.asList(
                shuffledStatsOrder.indexOf(0),
                shuffledStatsOrder.indexOf(1),
                shuffledStatsOrder.indexOf(2),
                shuffledStatsOrder.indexOf(3),
                shuffledStatsOrder.indexOf(4));
        applyShuffledOrderToStats();
        shuffledStatsOrder = evolvesFrom.shuffledStatsOrder;
        applyShuffledOrderToStats();
    }

    @Override
    protected void applyShuffledOrderToStats() {
        List<Integer> stats = Arrays.asList(hp, attack, defense, special, speed);

        // Copy in new stats
        hp = stats.get(shuffledStatsOrder.get(0));
        attack = stats.get(shuffledStatsOrder.get(1));
        defense = stats.get(shuffledStatsOrder.get(2));
        special = stats.get(shuffledStatsOrder.get(3));
        speed = stats.get(shuffledStatsOrder.get(4));
    }

    @Override
    public void randomizeStatsWithinBST(Random random) {
        // Minimum 20 HP, 10 everything else
        int bst = bst() - 60;

        // Make weightings
        double hpW = random.nextDouble(), atkW = random.nextDouble(), defW = random.nextDouble();
        double specW = random.nextDouble(), speW = random.nextDouble();

        double totW = hpW + atkW + defW + specW + speW;

        hp = (int) Math.max(1, Math.round(hpW / totW * bst)) + 20;
        attack = (int) Math.max(1, Math.round(atkW / totW * bst)) + 10;
        defense = (int) Math.max(1, Math.round(defW / totW * bst)) + 10;
        special = (int) Math.max(1, Math.round(specW / totW * bst)) + 10;
        speed = (int) Math.max(1, Math.round(speW / totW * bst)) + 10;

        // Check for something we can't store
        if (hp > 255 || attack > 255 || defense > 255 || special > 255 || speed > 255) {
            // re roll
            randomizeStatsWithinBST(random);
        }
    }

    @Override
    public void copyRandomizedStatsUpEvolution(Pokemon evolvesFrom) {
        double ourBST = bst();
        double theirBST = evolvesFrom.bst();

        double bstRatio = ourBST / theirBST;

        hp = (int) Math.min(255, Math.max(1, Math.round(evolvesFrom.hp * bstRatio)));
        attack = (int) Math.min(255, Math.max(1, Math.round(evolvesFrom.attack * bstRatio)));
        defense = (int) Math.min(255, Math.max(1, Math.round(evolvesFrom.defense * bstRatio)));
        speed = (int) Math.min(255, Math.max(1, Math.round(evolvesFrom.speed * bstRatio)));
        special = (int) Math.min(255, Math.max(1, Math.round(evolvesFrom.special * bstRatio)));
    }

    @Override
    public void assignNewStatsForEvolution(Pokemon evolvesFrom, Random random) {
        double ourBST = bst();
        double theirBST = evolvesFrom.bst();

        double bstDiff = ourBST - theirBST;

        // Make weightings
        double hpW = random.nextDouble(), atkW = random.nextDouble(), defW = random.nextDouble();
        double specW = random.nextDouble(), speW = random.nextDouble();

        double totW = hpW + atkW + defW + specW + speW;

        double hpDiff = Math.round((hpW / totW) * bstDiff);
        double atkDiff = Math.round((atkW / totW) * bstDiff);
        double defDiff = Math.round((defW / totW) * bstDiff);
        double specDiff = Math.round((specW / totW) * bstDiff);
        double speDiff = Math.round((speW / totW) * bstDiff);

        hp = (int) Math.min(255, Math.max(1, evolvesFrom.hp + hpDiff));
        attack = (int) Math.min(255, Math.max(1, evolvesFrom.attack + atkDiff));
        defense = (int) Math.min(255, Math.max(1, evolvesFrom.defense + defDiff));
        speed = (int) Math.min(255, Math.max(1, evolvesFrom.speed + speDiff));
        special = (int) Math.min(255, Math.max(1, evolvesFrom.special + specDiff));
    }

    @Override
    protected int bst() {
        return hp + attack + defense + special + speed;
    }

    @Override
    public int bstForPowerLevels() {
        return hp + attack + defense + special + speed;
    }

    @Override
    public double getAttackSpecialAttackRatio() {
        return (double)attack / ((double)attack + (double)special);
    }

    @Override
    public String toString() {
        return "Pokemon [name=" + name + ", number=" + number + ", primaryType=" + primaryType + ", secondaryType="
                + secondaryType + ", hp=" + hp + ", attack=" + attack + ", defense=" + defense + ", special=" + special
                + ", speed=" + speed + "]";
    }
}

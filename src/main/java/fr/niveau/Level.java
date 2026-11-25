package fr.niveau;

import java.util.ArrayList;
import java.util.List;

public class Level {
    public final int number;
    /** XP TOTALE nécessaire pour atteindre ce niveau. */
    public long xp;
    public final List<Reward> rewards = new ArrayList<>();

    public Level(int number, long xp) {
        this.number = number;
        this.xp = xp;
    }
}

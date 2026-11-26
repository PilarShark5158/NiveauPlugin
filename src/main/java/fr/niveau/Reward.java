package fr.niveau;

import org.bukkit.Material;

public class Reward {
    public String name;
    public String command;
    public Material icon;

    public Reward(String name, String command, Material icon) {
        this.name = name;
        this.command = command;
        this.icon = icon;
    }
}

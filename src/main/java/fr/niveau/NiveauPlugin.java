package fr.niveau;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class NiveauPlugin extends JavaPlugin {
    private LevelManager levels;
    private ChatInput chat;
    private Menus menus;
    private PlacedBlocks placed;
    private XpBossBar bossBar;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        levels = new LevelManager(this);
        levels.load();
        placed = new PlacedBlocks(this);
        placed.load();
        bossBar = new XpBossBar(this);
        chat = new ChatInput(this);
        menus = new Menus(this);

        Bukkit.getPluginManager().registerEvents(chat, this);
        Bukkit.getPluginManager().registerEvents(bossBar, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(new XpListener(this), this);

        NiveauCommand player = new NiveauCommand(this, false);
        NiveauCommand admin = new NiveauCommand(this, true);
        getCommand("niveau").setExecutor(player);
        getCommand("niveau").setTabCompleter(player);
        getCommand("niveauadmin").setExecutor(admin);
        getCommand("niveauadmin").setTabCompleter(admin);

        // XP au temps de jeu : chaque minute
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long amount = getConfig().getLong("xp.playtime-per-minute", 10);
            if (amount <= 0) return;
            for (Player p : Bukkit.getOnlinePlayers()) levels.addXp(p, amount);
        }, 1200L, 1200L);

        // Sauvegarde auto toutes les 5 minutes
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            levels.saveIfDirty();
            placed.saveIfDirty();
        }, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (bossBar != null) bossBar.hideAll();
        if (levels != null) levels.savePlayers();
        if (placed != null) placed.save();
    }

    public LevelManager levels() { return levels; }
    public ChatInput chat() { return chat; }
    public Menus menus() { return menus; }
    public PlacedBlocks placed() { return placed; }
    public XpBossBar bossBar() { return bossBar; }
}

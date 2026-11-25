package fr.niveau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LevelManager {
    private final NiveauPlugin plugin;
    private final File levelsFile;
    private final File playersFile;
    private final List<Level> levels = new ArrayList<>();
    private final Map<UUID, Long> xp = new HashMap<>();
    private final Map<UUID, Integer> claimed = new HashMap<>();
    private boolean dirty;

    public LevelManager(NiveauPlugin plugin) {
        this.plugin = plugin;
        this.levelsFile = new File(plugin.getDataFolder(), "levels.yml");
        this.playersFile = new File(plugin.getDataFolder(), "players.yml");
    }

    // ------------------------------------------------------------ chargement

    public void load() {
        levels.clear();
        xp.clear();
        claimed.clear();
        plugin.getDataFolder().mkdirs();

        if (!levelsFile.exists()) {
            generateDefaults();
            saveLevels();
        } else {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(levelsFile);
            ConfigurationSection sec = cfg.getConfigurationSection("levels");
            if (sec != null) {
                List<Integer> keys = new ArrayList<>();
                for (String k : sec.getKeys(false)) {
                    try { keys.add(Integer.parseInt(k)); } catch (NumberFormatException ignored) {}
                }
                Collections.sort(keys);
                for (int k : keys) {
                    Level lv = new Level(levels.size() + 1, sec.getLong(k + ".xp"));
                    ConfigurationSection rs = sec.getConfigurationSection(k + ".rewards");
                    if (rs != null) {
                        for (String rk : rs.getKeys(false)) {
                            String name = rs.getString(rk + ".name", "&fRécompense");
                            String cmd = rs.getString(rk + ".command", "");
                            Material mat = Material.matchMaterial(rs.getString(rk + ".icon", "PAPER"));
                            if (mat == null || !mat.isItem() || mat.isAir()) mat = Material.PAPER;
                            lv.rewards.add(new Reward(name, cmd, mat));
                        }
                    }
                    levels.add(lv);
                }
            }
        }

        if (playersFile.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(playersFile);
            ConfigurationSection ps = cfg.getConfigurationSection("players");
            if (ps != null) {
                for (String id : ps.getKeys(false)) {
                    try {
                        UUID u = UUID.fromString(id);
                        xp.put(u, ps.getLong(id + ".xp"));
                        claimed.put(u, ps.getInt(id + ".claimed"));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    }

    private void generateDefaults() {
        long cumulative = 0;
        for (int i = 1; i <= 50; i++) {
            cumulative += 100 + (i - 1) * 50L; // le palier coûte de plus en plus cher
            Level lv = new Level(i, cumulative);
            lv.rewards.add(new Reward("&e" + Text.n(i * 500L) + " $", "eco give %player% " + (i * 500), Material.GOLD_INGOT));
            if (i % 5 == 0) {
                lv.rewards.add(new Reward("&b" + (i / 5 * 2) + " Diamants", "give %player% diamond " + (i / 5 * 2), Material.DIAMOND));
            }
            levels.add(lv);
        }
    }

    public void saveLevels() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Level lv : levels) {
            String base = "levels." + lv.number;
            cfg.set(base + ".xp", lv.xp);
            int j = 0;
            for (Reward r : lv.rewards) {
                String rb = base + ".rewards." + j++;
                cfg.set(rb + ".name", r.name);
                cfg.set(rb + ".command", r.command);
                cfg.set(rb + ".icon", r.icon.name());
            }
        }
        try { cfg.save(levelsFile); } catch (IOException e) { plugin.getLogger().severe("Sauvegarde levels.yml impossible : " + e.getMessage()); }
    }

    public void savePlayers() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Long> en : xp.entrySet()) {
            cfg.set("players." + en.getKey() + ".xp", en.getValue());
            cfg.set("players." + en.getKey() + ".claimed", claimed.getOrDefault(en.getKey(), 0));
        }
        try { cfg.save(playersFile); dirty = false; } catch (IOException e) { plugin.getLogger().severe("Sauvegarde players.yml impossible : " + e.getMessage()); }
    }

    public void saveIfDirty() {
        if (dirty) savePlayers();
    }

    // ------------------------------------------------------------ lecture

    public int size() { return levels.size(); }

    public Level get(int number) {
        return number >= 1 && number <= levels.size() ? levels.get(number - 1) : null;
    }

    public long getXp(UUID id) { return xp.getOrDefault(id, 0L); }

    public int levelFor(long totalXp) {
        int c = 0;
        for (Level lv : levels) {
            if (totalXp >= lv.xp) c++; else break;
        }
        return c;
    }

    // ------------------------------------------------------------ XP

    public void addXp(Player p, long amount) {
        if (amount <= 0) return;
        UUID id = p.getUniqueId();
        long total = getXp(id) + amount;
        xp.put(id, total);
        dirty = true;
        if (plugin.getConfig().getBoolean("xp.actionbar", true)) {
            p.sendActionBar(Text.c("&a+" + Text.n(amount) + " XP &7| &e" + Text.n(total) + " XP"));
        }
        checkLevelUp(p);
        if (plugin.bossBar() != null) plugin.bossBar().update(p, amount);
    }

    public void resetPlayer(UUID id) {
        xp.put(id, 0L);
        claimed.put(id, 0);
        dirty = true;
    }

    public void checkLevelUp(Player p) {
        UUID id = p.getUniqueId();
        int current = levelFor(getXp(id));
        int done = claimed.getOrDefault(id, 0);
        if (current <= done) return;

        for (int n = done + 1; n <= current; n++) {
            Level lv = get(n);
            if (lv == null) continue;
            giveRewards(p, lv);
        }
        claimed.put(id, current);
        dirty = true;

        p.showTitle(Title.title(Text.c("&6&lNIVEAU " + current), Text.c("&eFélicitations !")));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        p.sendMessage(Text.c("&6&l» &eTu passes au &6niveau " + current + " &e!"));
        Level last = get(current);
        if (last != null) {
            for (Reward r : last.rewards) p.sendMessage(Text.c("&8  + &f" + r.name));
        }
    }

    private void giveRewards(Player p, Level lv) {
        for (Reward r : lv.rewards) {
            if (r.command == null || r.command.isBlank()) continue;
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), r.command.replace("%player%", p.getName()));
            } catch (Exception ex) {
                plugin.getLogger().warning("Commande de récompense invalide (niveau " + lv.number + ") : " + r.command);
            }
        }
    }

    // ------------------------------------------------------------ admin

    /** @return null si OK, sinon un message d'erreur. */
    public String setLevelXp(int number, long value) {
        Level lv = get(number);
        if (lv == null) return "Niveau introuvable.";
        long min = number > 1 ? levels.get(number - 2).xp + 1 : 1;
        if (value < min) return "L'XP doit être d'au moins " + Text.n(min) + " (supérieure au niveau précédent).";
        if (number < levels.size() && value >= levels.get(number).xp)
            return "L'XP doit être inférieure à " + Text.n(levels.get(number).xp) + " (niveau suivant).";
        lv.xp = value;
        saveLevels();
        return null;
    }

    public Level addLevel() {
        long last = levels.isEmpty() ? 0 : levels.get(levels.size() - 1).xp;
        long prev = levels.size() >= 2 ? levels.get(levels.size() - 2).xp : 0;
        long inc = Math.max(100, (last - prev) + 50);
        Level lv = new Level(levels.size() + 1, last + inc);
        levels.add(lv);
        saveLevels();
        return lv;
    }

    public boolean removeLastLevel() {
        if (levels.size() <= 1) return false;
        levels.remove(levels.size() - 1);
        saveLevels();
        return true;
    }
}

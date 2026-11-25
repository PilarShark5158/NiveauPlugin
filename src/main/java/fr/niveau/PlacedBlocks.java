package fr.niveau;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Mémorise les blocs posés par les joueurs pour ne pas donner d'XP en les recassant. */
public class PlacedBlocks {
    private final NiveauPlugin plugin;
    private final File file;
    private final Map<UUID, Set<Long>> map = new HashMap<>();
    private boolean dirty;

    public PlacedBlocks(NiveauPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "placed.yml");
    }

    public void load() {
        map.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("worlds");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            try {
                map.put(UUID.fromString(id), new HashSet<>(sec.getLongList(id)));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void mark(Block b) {
        map.computeIfAbsent(b.getWorld().getUID(), k -> new HashSet<>()).add(b.getBlockKey());
        dirty = true;
    }

    public void unmark(Block b) {
        consume(b);
    }

    /** @return true si le bloc avait été posé par un joueur (la marque est retirée). */
    public boolean consume(Block b) {
        Set<Long> s = map.get(b.getWorld().getUID());
        if (s != null && s.remove(b.getBlockKey())) {
            dirty = true;
            return true;
        }
        return false;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<Long>> en : map.entrySet()) {
            if (!en.getValue().isEmpty()) cfg.set("worlds." + en.getKey(), new ArrayList<>(en.getValue()));
        }
        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Sauvegarde placed.yml impossible : " + e.getMessage());
        }
    }
}

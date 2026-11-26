package fr.niveau;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;

public class XpListener implements Listener {
    private final NiveauPlugin plugin;

    public XpListener(NiveauPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean skip(Player p) {
        return plugin.getConfig().getBoolean("xp.ignore-creative", true) && p.getGameMode() == GameMode.CREATIVE;
    }

    private boolean antiAbuse() {
        return plugin.getConfig().getBoolean("xp.anti-abuse.placed-blocks", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (antiAbuse()) plugin.placed().mark(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();

        // Toujours retirer la marque, même en créatif.
        boolean placedByPlayer = antiAbuse() && plugin.placed().consume(b);
        if (skip(p)) return;

        // Une culture mûre donne de l'XP même si elle a été plantée.
        boolean matureCrop = b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge();
        if (placedByPlayer && !matureCrop) return;

        FileConfiguration cfg = plugin.getConfig();
        long def = cfg.getLong("xp.blocks.default", 1);
        long amount = cfg.getLong("xp.blocks." + b.getType().name(), def);
        plugin.levels().addXp(p, amount);
    }

    // Les blocs détruits autrement ne doivent pas laisser de marque.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!antiAbuse()) return;
        for (Block b : e.blockList()) plugin.placed().unmark(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!antiAbuse()) return;
        for (Block b : e.blockList()) plugin.placed().unmark(b);
    }

    // Un arbre qui pousse sur une pousse plantée : les bûches sont naturelles.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent e) {
        if (antiAbuse()) plugin.placed().unmark(e.getLocation().getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        if (antiAbuse()) plugin.placed().unmark(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim instanceof ArmorStand) return;
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || skip(killer)) return;
        FileConfiguration cfg = plugin.getConfig();
        long def = cfg.getLong("xp.kills.default", 5);
        long amount = cfg.getLong("xp.kills." + victim.getType().name(), def);
        plugin.levels().addXp(killer, amount);
    }
}

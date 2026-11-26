package fr.niveau;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** BossBar de progression affichée quelques secondes à chaque gain d'XP. */
public class XpBossBar implements Listener {
    private final NiveauPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    public XpBossBar(NiveauPlugin plugin) {
        this.plugin = plugin;
    }

    public void update(Player p, long gained) {
        if (!plugin.getConfig().getBoolean("xp.bossbar.enabled", true)) return;
        LevelManager lm = plugin.levels();
        UUID id = p.getUniqueId();
        long xp = lm.getXp(id);
        int cur = lm.levelFor(xp);
        Level next = lm.get(cur + 1);

        float progress;
        Component title;
        if (next == null) {
            progress = 1f;
            title = Text.c("&6&lNiveau " + cur + " &8| &aNiveau maximum &a(+" + Text.n(gained) + " XP)");
        } else {
            long prev = cur > 0 ? lm.get(cur).xp : 0;
            progress = (float) (xp - prev) / Math.max(1, next.xp - prev);
            progress = Math.max(0f, Math.min(1f, progress));
            title = Text.c("&6&lNiveau " + cur + " &7➜ &e&l" + (cur + 1) + " &8| &f" + Text.n(xp)
                    + "&7/&f" + Text.n(next.xp) + " XP &a(+" + Text.n(gained) + ")");
        }
        BossBar.Color color = progress < 0.33f ? BossBar.Color.RED
                : progress < 0.66f ? BossBar.Color.YELLOW : BossBar.Color.GREEN;

        BossBar bar = bars.get(id);
        if (bar == null) {
            bar = BossBar.bossBar(title, progress, color, BossBar.Overlay.NOTCHED_10);
            bars.put(id, bar);
            p.showBossBar(bar);
        } else {
            bar.name(title);
            bar.progress(progress);
            bar.color(color);
        }

        BukkitTask old = tasks.remove(id);
        if (old != null) old.cancel();
        int secs = Math.max(1, plugin.getConfig().getInt("xp.bossbar.seconds", 5));
        tasks.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> hide(p), secs * 20L));
    }

    public void hide(Player p) {
        UUID id = p.getUniqueId();
        BukkitTask t = tasks.remove(id);
        if (t != null) t.cancel();
        BossBar b = bars.remove(id);
        if (b != null) p.hideBossBar(b);
    }

    public void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) hide(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hide(e.getPlayer());
    }
}

package fr.niveau;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Permet de demander une saisie dans le chat à un admin. */
public class ChatInput implements Listener {
    private record Pending(Consumer<String> onInput, Runnable onCancel) {}

    private final NiveauPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(NiveauPlugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String prompt, Consumer<String> onInput, Runnable onCancel) {
        p.closeInventory();
        pending.put(p.getUniqueId(), new Pending(onInput, onCancel));
        p.sendMessage(Text.c(prompt));
        p.sendMessage(Text.c("&7Écris &cannuler &7pour annuler."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Pending pd = pending.remove(e.getPlayer().getUniqueId());
        if (pd == null) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("annuler")) {
                p.sendMessage(Text.c("&cAnnulé."));
                pd.onCancel().run();
            } else {
                pd.onInput().accept(msg);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }
}

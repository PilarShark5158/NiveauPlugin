package fr.niveau;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class NiveauCommand implements CommandExecutor, TabCompleter {
    private final NiveauPlugin plugin;
    private final boolean admin;

    public NiveauCommand(NiveauPlugin plugin, boolean admin) {
        this.plugin = plugin;
        this.admin = admin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        LevelManager lm = plugin.levels();

        if (!admin) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Commande réservée aux joueurs.");
                return true;
            }
            int cur = lm.levelFor(lm.getXp(p.getUniqueId()));
            plugin.menus().openMain(p, cur / Menus.PER_PAGE, false);
            return true;
        }

        if (!sender.hasPermission("niveau.admin")) {
            sender.sendMessage(Text.c("&cTu n'as pas la permission."));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player p) plugin.menus().openMain(p, 0, true);
            else sender.sendMessage("Utilise /niveauadmin <reload|give|reset>.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                lm.savePlayers();
                lm.load();
                sender.sendMessage(Text.c("&aConfiguration et niveaux rechargés."));
            }
            case "give" -> {
                if (args.length < 3) { sender.sendMessage(Text.c("&cUsage : /niveauadmin give <joueur> <xp>")); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage(Text.c("&cJoueur introuvable.")); return true; }
                try {
                    long v = Long.parseLong(args[2]);
                    lm.addXp(t, v);
                    sender.sendMessage(Text.c("&a" + Text.n(v) + " XP donnés à " + t.getName() + "."));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Text.c("&cNombre invalide."));
                }
            }
            case "reset" -> {
                if (args.length < 2) { sender.sendMessage(Text.c("&cUsage : /niveauadmin reset <joueur>")); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage(Text.c("&cJoueur introuvable.")); return true; }
                lm.resetPlayer(t.getUniqueId());
                sender.sendMessage(Text.c("&aProgression de " + t.getName() + " remise à zéro."));
            }
            default -> sender.sendMessage(Text.c("&cUsage : /niveauadmin [reload|give|reset]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!admin || !sender.hasPermission("niveau.admin")) return out;
        if (args.length == 1) {
            for (String s : List.of("reload", "give", "reset")) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset"))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        return out;
    }
}

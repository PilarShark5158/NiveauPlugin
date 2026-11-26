package fr.niveau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Menus implements Listener {
    /** 25 niveaux par page, en serpent (5x5), la 6e ligne sert aux boutons. */
    public static final int PER_PAGE = 25;
    /** Nombre d'entrées par page dans le menu des sources d'XP. */
    private static final int SRC_PER_PAGE = 45;
    /**
     * Serpent vertical : colonnes 0, 2, 4, 6, 8. Colonne 0 vers le bas, colonne 2 vers le haut, etc.
     * Les colonnes impaires sont des séparateurs gris, avec un connecteur coloré en haut ou en bas.
     */
    private static final int[] PATH = buildPath();

    private static int[] buildPath() {
        int[] p = new int[PER_PAGE];
        for (int i = 0; i < PER_PAGE; i++) {
            int colIdx = i / 5;
            int k = i % 5;
            int row = (colIdx % 2 == 0) ? k : 4 - k;
            p[i] = row * 9 + colIdx * 2;
        }
        return p;
    }

    /** Emplacements des récompenses dans l'éditeur. */
    private static final int[] REWARD_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private static final Material[] THEME_MAIN = {Material.GRAY_STAINED_GLASS_PANE};
    private static final Material[] THEME_ADMIN = {
            Material.ORANGE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE};
    private static final Material[] THEME_SOURCES = {
            Material.MAGENTA_STAINED_GLASS_PANE, Material.PINK_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE};

    public enum Type { MAIN, ADMIN, EDITOR, SOURCES }

    public static class Holder implements InventoryHolder {
        public final Type type;
        public final int page;
        public final int level;
        /** Sources : true = du plus d'XP au moins d'XP. */
        public final boolean desc;
        /** Sources : 0 = tout, 1 = blocs, 2 = mobs, 3 = autres. */
        public final int filter;
        /** Sources : retour vers le menu admin. */
        public final boolean admin;
        private Inventory inv;

        public Holder(Type type, int page, int level, boolean desc, int filter, boolean admin) {
            this.type = type;
            this.page = page;
            this.level = level;
            this.desc = desc;
            this.filter = filter;
            this.admin = admin;
        }

        public Holder(Type type, int page, int level) {
            this(type, page, level, true, 0, false);
        }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private record Source(String kind, Material icon, Component name, String sortKey, long xp, String detail) {}

    private final NiveauPlugin plugin;
    private final LevelManager lm;
    private final ChatInput chat;

    public Menus(NiveauPlugin plugin) {
        this.plugin = plugin;
        this.lm = plugin.levels();
        this.chat = plugin.chat();
    }

    // ================================================================= items

    private static ItemStack itemC(Material m, Component name, List<String> lore, boolean glint) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        meta.lore(Text.lore(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (glint) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack item(Material m, String name, List<String> lore) {
        return itemC(m, Text.c(name), lore, false);
    }

    private static void fillAll(Inventory inv, Material m) {
        ItemStack filler = item(m, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private static void bottomRow(Inventory inv, Material[] theme) {
        for (int i = 45; i < 54; i++) inv.setItem(i, item(theme[i % theme.length], " ", List.of()));
    }

    private static int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return -1;
    }

    private ItemStack levelItem(Level lv, long xp, boolean admin) {
        int cur = lm.levelFor(xp);
        boolean five = lv.number % 5 == 0;
        Material m;
        String name;
        String state;
        boolean next = false;
        String star = five ? "★ " : "";
        if (lv.number <= cur) {
            m = five ? Material.GREEN_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE;
            name = "&a&l" + star + "Niveau " + lv.number;
            state = "&a✔ Débloqué";
        } else if (lv.number == cur + 1) {
            m = five ? Material.ORANGE_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE;
            name = "&6&l" + star + "Niveau " + lv.number;
            state = "&6➜ Prochain niveau &7(" + Text.n(xp) + " / " + Text.n(lv.xp) + ")";
            next = true;
        } else {
            m = five ? Material.RED_STAINED_GLASS_PANE : Material.PINK_STAINED_GLASS_PANE;
            name = "&c&l" + star + "Niveau " + lv.number;
            state = "&c✖ Verrouillé";
        }

        List<String> lore = new ArrayList<>();
        if (five) lore.add("&d✦ Palier spécial");
        lore.add("&7XP requise : &e" + Text.n(lv.xp));
        lore.add("");
        lore.add("&b&lRécompenses");
        if (lv.rewards.isEmpty()) {
            lore.add("&8  Aucune");
        } else {
            for (Reward r : lv.rewards) lore.add("&3 » &f" + r.name);
        }
        lore.add("");
        lore.add(state);
        if (next) {
            long prev = lv.number > 1 ? lm.get(lv.number - 1).xp : 0;
            double ratio = (double) (xp - prev) / Math.max(1, lv.xp - prev);
            lore.add(Text.bar(ratio, 16));
            lore.add("&7Il te manque &e" + Text.n(lv.xp - xp) + " XP");
        }
        if (admin) {
            lore.add("");
            lore.add("&e▶ Clique pour modifier");
        }
        ItemStack it = itemC(m, Text.c(name), lore, next);
        it.setAmount(Math.min(lv.number, 64));
        return it;
    }

    private ItemStack rewardItem(Reward r) {
        Material m = (r.icon != null && r.icon.isItem() && !r.icon.isAir()) ? r.icon : Material.PAPER;
        return item(m, r.name, List.of(
                "&7Commande : &f/" + r.command,
                "",
                "&e▶ Clic gauche : modifier",
                "&c▶ Shift + clic droit : supprimer"));
    }

    // ================================================================= menu principal

    public void openMain(Player p, int page, boolean admin) {
        int total = lm.size();
        int pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        Holder h = new Holder(admin ? Type.ADMIN : Type.MAIN, page, 0);
        String title = (admin ? "&8Niveaux &7> &cAdmin" : "&8Niveaux &7> &9Récompenses") + " &8(" + (page + 1) + "/" + pages + ")";
        Inventory inv = Bukkit.createInventory(h, 54, Text.c(title));
        h.inv = inv;

        fillAll(inv, Material.GRAY_STAINED_GLASS_PANE);
        bottomRow(inv, admin ? THEME_ADMIN : THEME_MAIN);

        long xp = lm.getXp(p.getUniqueId());
        int reached = lm.levelFor(xp);
        for (int i = 0; i < PER_PAGE; i++) {
            Level lv = lm.get(page * PER_PAGE + i + 1);
            if (lv != null) inv.setItem(PATH[i], levelItem(lv, xp, admin));
        }

        // Connecteurs du serpent (en bas après une colonne qui descend, en haut après une qui monte)
        for (int c = 0; c < 4; c++) {
            Level nxt = lm.get(page * PER_PAGE + (c + 1) * 5 + 1);
            if (nxt == null) break;
            int slot = (c % 2 == 0 ? 36 : 0) + 2 * c + 1;
            Material cm = nxt.number <= reached ? Material.LIME_STAINED_GLASS_PANE
                    : nxt.number == reached + 1 ? Material.YELLOW_STAINED_GLASS_PANE
                    : Material.PINK_STAINED_GLASS_PANE;
            inv.setItem(slot, item(cm, " ", List.of()));
        }

        if (page > 0) inv.setItem(45, item(Material.ARROW, "&e◀ Page précédente", List.of()));
        if (page < pages - 1) inv.setItem(53, item(Material.ARROW, "&ePage suivante ▶", List.of()));

        inv.setItem(48, item(Material.WRITABLE_BOOK, "&b&lSources d'XP",
                List.of("&7Tout ce qui te fait gagner de l'XP,", "&7avec le nombre d'XP de chacun.", "", "&e▶ Clique pour ouvrir")));

        int cur = lm.levelFor(xp);
        Level nextLv = lm.get(cur + 1);
        List<String> lore = new ArrayList<>();
        lore.add("&7Niveau actuel : &e" + cur);
        lore.add("&7XP totale : &e" + Text.n(xp));
        lore.add(nextLv == null ? "&aNiveau maximum atteint !" : "&7Prochain niveau : &e" + Text.n(nextLv.xp - xp) + " XP restants");
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(p);
        sm.displayName(Text.c("&6&l" + p.getName()));
        sm.lore(Text.lore(lore));
        head.setItemMeta(sm);
        inv.setItem(49, head);

        if (admin) {
            inv.setItem(47, item(Material.LIME_DYE, "&a+ Ajouter un niveau", List.of("&7Ajoute un palier à la fin du chemin.")));
            inv.setItem(51, item(Material.RED_DYE, "&c- Supprimer le dernier niveau", List.of("&7Shift + clic pour confirmer.")));
        }
        p.openInventory(inv);
    }

    // ================================================================= sources d'XP

    private List<Source> buildSources() {
        FileConfiguration cfg = plugin.getConfig();
        List<Source> out = new ArrayList<>();

        ConfigurationSection bs = cfg.getConfigurationSection("xp.blocks");
        if (bs != null) {
            for (String k : bs.getKeys(false)) {
                if (k.equals("default")) continue;
                Material m = Material.matchMaterial(k);
                if (m == null) continue;
                Material icon = (m.isItem() && !m.isAir()) ? m : Material.PAPER;
                out.add(new Source("BLOCK", icon, plain(m.translationKey()), k, bs.getLong(k), "&7Bloc cassé"));
            }
        }
        out.add(new Source("BLOCK", Material.GRASS_BLOCK, Text.c("&fAutres blocs"), "zzz",
                cfg.getLong("xp.blocks.default", 1), "&7Tous les blocs non listés"));

        long defMob = cfg.getLong("xp.kills.default", 5);
        for (EntityType t : EntityType.values()) {
            Class<?> c = t.getEntityClass();
            if (c == null || !LivingEntity.class.isAssignableFrom(c) || t == EntityType.ARMOR_STAND) continue;
            Material egg = Material.matchMaterial(t.name() + "_SPAWN_EGG");
            out.add(new Source("MOB", egg != null ? egg : Material.PAPER, plain(t.translationKey()), t.name(),
                    cfg.getLong("xp.kills." + t.name(), defMob), "&7Mob tué"));
        }

        long pt = cfg.getLong("xp.playtime-per-minute", 10);
        if (pt > 0) {
            out.add(new Source("OTHER", Material.CLOCK, Text.c("&fTemps de jeu"), "temps", pt, "&7Par minute passée en jeu"));
        }
        return out;
    }

    private static Component plain(String translationKey) {
        return Component.translatable(translationKey)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
    }

    public void openSources(Player p, int page, boolean desc, int filter, boolean backAdmin) {
        String kind = switch (filter) {
            case 1 -> "BLOCK";
            case 2 -> "MOB";
            case 3 -> "OTHER";
            default -> null;
        };
        List<Source> list = new ArrayList<>();
        for (Source s : buildSources()) if (kind == null || s.kind().equals(kind)) list.add(s);
        Comparator<Source> cmp = Comparator.comparingLong(Source::xp);
        if (desc) cmp = cmp.reversed();
        list.sort(cmp.thenComparing(Source::sortKey));

        int pages = Math.max(1, (list.size() + SRC_PER_PAGE - 1) / SRC_PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        Holder h = new Holder(Type.SOURCES, page, 0, desc, filter, backAdmin);
        Inventory inv = Bukkit.createInventory(h, 54,
                Text.c("&8Niveaux &7> &dSources d'XP &8(" + (page + 1) + "/" + pages + ")"));
        h.inv = inv;
        fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);
        bottomRow(inv, THEME_SOURCES);

        for (int i = 0; i < SRC_PER_PAGE; i++) {
            int idx = page * SRC_PER_PAGE + i;
            if (idx >= list.size()) break;
            Source s = list.get(idx);
            String color = switch (s.kind()) {
                case "BLOCK" -> "&a";
                case "MOB" -> "&c";
                default -> "&e";
            };
            inv.setItem(i, itemC(s.icon(), s.name(),
                    List.of(s.detail(), "", "&7XP gagnée : " + color + "+" + Text.n(s.xp())), false));
        }

        if (page > 0) inv.setItem(45, item(Material.ARROW, "&e◀ Page précédente", List.of()));
        if (page < pages - 1) inv.setItem(53, item(Material.ARROW, "&ePage suivante ▶", List.of()));

        String[] names = {"Tout", "Blocs", "Mobs", "Temps de jeu"};
        Material[] icons = {Material.NETHER_STAR, Material.GRASS_BLOCK, Material.ZOMBIE_HEAD, Material.CLOCK};
        for (int i = 0; i < 4; i++) {
            boolean sel = filter == i;
            inv.setItem(46 + i, itemC(icons[i], Text.c((sel ? "&a&l▶ " : "&f") + names[i]),
                    List.of(sel ? "&aFiltre actif" : "&e▶ Clique pour filtrer"), sel));
        }
        inv.setItem(50, item(Material.HOPPER,
                desc ? "&eTri : &a+ d'XP ➜ - d'XP" : "&eTri : &c- d'XP ➜ + d'XP",
                List.of("&e▶ Clique pour inverser le tri")));
        inv.setItem(52, item(Material.BARRIER, "&cRetour", List.of()));
        p.openInventory(inv);
    }

    // ================================================================= éditeur

    public void openEditor(Player p, int number) {
        Level lv = lm.get(number);
        if (lv == null) {
            openMain(p, 0, true);
            return;
        }
        Holder h = new Holder(Type.EDITOR, 0, number);
        Inventory inv = Bukkit.createInventory(h, 54, Text.c("&8Niveaux &7> &5Niveau " + number + " &7> &dModifier"));
        h.inv = inv;

        fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);
        ItemStack a = item(Material.PURPLE_STAINED_GLASS_PANE, " ", List.of());
        ItemStack b = item(Material.MAGENTA_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) inv.setItem(i, (i % 2 == 0) ? a : b);
        }

        inv.setItem(4, levelItem(lv, lm.getXp(p.getUniqueId()), false));
        inv.setItem(10, item(Material.EXPERIENCE_BOTTLE, "&e&lXP requise",
                List.of("&7Actuelle : &e" + Text.n(lv.xp), "", "&e▶ Clique pour modifier")));
        inv.setItem(13, item(Material.EMERALD, "&a&l+ Ajouter une récompense",
                List.of("&7Une commande exécutée par la console.", "&7Utilise &f%player% &7pour le joueur.", "", "&e▶ Clique pour ajouter")));
        inv.setItem(49, item(Material.ARROW, "&c◀ Retour", List.of()));

        for (int i = 0; i < lv.rewards.size() && i < REWARD_SLOTS.length; i++) {
            inv.setItem(REWARD_SLOTS[i], rewardItem(lv.rewards.get(i)));
        }
        p.openInventory(inv);
    }

    // ================================================================= clics

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getSlot();

        if (h.type == Type.EDITOR || h.type == Type.ADMIN) {
            if (!p.hasPermission("niveau.admin")) {
                p.closeInventory();
                return;
            }
        }

        switch (h.type) {
            case MAIN, ADMIN -> {
                boolean admin = h.type == Type.ADMIN;
                if (slot == 45 && h.page > 0) { openMain(p, h.page - 1, admin); return; }
                if (slot == 53) { openMain(p, h.page + 1, admin); return; }
                if (slot == 48) { openSources(p, 0, true, 0, admin); return; }
                if (!admin) return;

                int pos = indexOf(PATH, slot);
                if (pos >= 0) {
                    int n = h.page * PER_PAGE + pos + 1;
                    if (lm.get(n) != null) openEditor(p, n);
                    return;
                }
                if (slot == 47) {
                    Level lv = lm.addLevel();
                    p.sendMessage(Text.c("&aNiveau " + lv.number + " ajouté (" + Text.n(lv.xp) + " XP)."));
                    openMain(p, (lv.number - 1) / PER_PAGE, true);
                } else if (slot == 51 && e.isShiftClick()) {
                    if (lm.removeLastLevel()) p.sendMessage(Text.c("&cDernier niveau supprimé."));
                    else p.sendMessage(Text.c("&cImpossible de supprimer le seul niveau restant."));
                    openMain(p, h.page, true);
                }
            }
            case SOURCES -> {
                if (slot == 45 && h.page > 0) { openSources(p, h.page - 1, h.desc, h.filter, h.admin); return; }
                if (slot == 53) { openSources(p, h.page + 1, h.desc, h.filter, h.admin); return; }
                if (slot >= 46 && slot <= 49) { openSources(p, 0, h.desc, slot - 46, h.admin); return; }
                if (slot == 50) { openSources(p, 0, !h.desc, h.filter, h.admin); return; }
                if (slot == 52) openMain(p, 0, h.admin);
            }
            case EDITOR -> {
                int n = h.level;
                Level lv = lm.get(n);
                if (lv == null) { openMain(p, 0, true); return; }

                if (slot == 10) { askXp(p, n); return; }
                if (slot == 13) { rewardFlow(p, n, null, -1); return; }
                if (slot == 49) { openMain(p, (n - 1) / PER_PAGE, true); return; }

                int idx = indexOf(REWARD_SLOTS, slot);
                if (idx >= 0 && idx < lv.rewards.size()) {
                    if (e.getClick() == ClickType.SHIFT_RIGHT) {
                        lv.rewards.remove(idx);
                        lm.saveLevels();
                        p.sendMessage(Text.c("&cRécompense supprimée."));
                        openEditor(p, n);
                    } else {
                        rewardFlow(p, n, lv.rewards.get(idx), idx);
                    }
                }
            }
        }
    }

    // ================================================================= saisies chat

    private void askXp(Player p, int n) {
        Level lv = lm.get(n);
        chat.ask(p,
                "&6Entre l'XP totale requise pour atteindre le niveau &e" + n + " &7(actuelle : " + Text.n(lv.xp) + ")",
                msg -> {
                    try {
                        long v = Long.parseLong(msg.replace(" ", "").replace(",", ""));
                        String err = lm.setLevelXp(n, v);
                        if (err != null) p.sendMessage(Text.c("&c" + err));
                        else p.sendMessage(Text.c("&aXP du niveau " + n + " définie sur &e" + Text.n(v) + "&a."));
                    } catch (NumberFormatException ex) {
                        p.sendMessage(Text.c("&cNombre invalide."));
                    }
                    openEditor(p, n);
                },
                () -> openEditor(p, n));
    }

    /** Ajout (existing == null) ou modification d'une récompense en 3 étapes. */
    private void rewardFlow(Player p, int n, Reward existing, int idx) {
        Runnable back = () -> openEditor(p, n);
        String keep = existing == null ? "" : " &7(&f-&7 = garder l'actuel)";

        chat.ask(p, "&6[1/3] Commande de la récompense &7(sans /, utilise %player%)" + keep, cmd -> {
            final String command = (existing != null && cmd.equals("-"))
                    ? existing.command
                    : (cmd.startsWith("/") ? cmd.substring(1) : cmd);

            chat.ask(p, "&6[2/3] Nom affiché dans le menu &7(couleurs avec &, &f-&7 = défaut)", name -> {
                final String nm = name.equals("-")
                        ? (existing != null ? existing.name : "&f" + command)
                        : name;

                chat.ask(p, "&6[3/3] Icône &7(ex : DIAMOND, GOLD_INGOT, EMERALD - &f-&7 = auto)", ic -> {
                    Material mat;
                    if (ic.equals("-")) {
                        mat = existing != null ? existing.icon : guessIcon(command);
                    } else {
                        Material m = Material.matchMaterial(ic);
                        if (m == null || !m.isItem() || m.isAir()) {
                            p.sendMessage(Text.c("&cIcône inconnue, icône automatique utilisée."));
                            m = guessIcon(command);
                        }
                        mat = m;
                    }

                    Level lv = lm.get(n);
                    if (lv == null) { openMain(p, 0, true); return; }
                    Reward r = new Reward(nm, command, mat);
                    if (existing == null) lv.rewards.add(r);
                    else if (idx >= 0 && idx < lv.rewards.size()) lv.rewards.set(idx, r);
                    lm.saveLevels();
                    p.sendMessage(Text.c("&aRécompense enregistrée pour le niveau " + n + "."));
                    openEditor(p, n);
                }, back);
            }, back);
        }, back);
    }

    private static Material guessIcon(String command) {
        String c = command.toLowerCase();
        if (c.contains("eco") || c.contains("money") || c.contains("pay")) return Material.GOLD_INGOT;
        if (c.contains("gem")) return Material.EMERALD;
        if (c.contains("crate") || c.contains("key")) return Material.TRIPWIRE_HOOK;
        if (c.contains("xp") || c.contains("exp")) return Material.EXPERIENCE_BOTTLE;
        if (c.startsWith("give")) return Material.CHEST;
        return Material.NETHER_STAR;
    }
}

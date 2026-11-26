package fr.niveau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Text {
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    private Text() {}

    public static Component c(String s) {
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> lore(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String s : lines) out.add(c(s));
        return out;
    }

    public static String n(long v) {
        return String.format(Locale.US, "%,d", v).replace(',', ' ');
    }

    public static String bar(double ratio, int size) {
        ratio = Math.max(0, Math.min(1, ratio));
        int filled = (int) Math.round(ratio * size);
        return "&a" + "█".repeat(filled) + "&8" + "█".repeat(size - filled);
    }
}

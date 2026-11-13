package com.miseru.avemujica.client;

import net.minecraft.client.Minecraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Three independent octopus UI themes. Logo visibility is a theme property, not a palette side effect. */
public final class OctopusTheme {
    private OctopusTheme() { }

    public enum Palette {
        LIGHT(false, 0xFFF5F8FC, 0xFFFFFFFF, 0xFFF4F8FE, 0xFF9FC2DE,
              0xFF2F6D9E, 0xFF3A6EA5, 0xFF1F2C3A, 0xFF63768A,
              0xFF2D8B75, 0xFFD9EAFB, 0xFFEAF3FD, 0xFFD8EAFB),
        CRIMSON(false, 0xFF1A121A, 0xFF241823, 0xFF2E2030, 0xFF8E5570,
                0xFFE45F89, 0xFFFF97B5, 0xFFF8EAF0, 0xFFCFACBA,
                0xFFA8D0BE, 0xFF542638, 0xFF38212F, 0xFF4A1C34),
        CRIMSON_LOGO(true, 0xA85B2B4B, 0xD62A1231, 0xB5211028, 0xFFB56EE0,
                    0xFFF0A2FF, 0xFFFFE5A8, 0xFFF6E7F8, 0xFFCFB7D7,
                    0xFFABDCC4, 0x8A5A257B, 0x665C2480, 0xD34A1C57);

        public final boolean showLogo;
        public final int background, panel, alternate, edge, accent, secondary, text, muted;
        public final int positive, selection, hover, header;

        Palette(boolean showLogo, int background, int panel, int alternate, int edge,
                int accent, int secondary, int text, int muted,
                int positive, int selection, int hover, int header) {
            this.showLogo = showLogo;
            this.background = background; this.panel = panel; this.alternate = alternate;
            this.edge = edge; this.accent = accent; this.secondary = secondary;
            this.text = text; this.muted = muted; this.positive = positive;
            this.selection = selection; this.hover = hover; this.header = header;
        }
    }

    private static boolean loaded;
    // The LOGO skin remains the first-run default; the two legacy palettes remain selectable.
    private static Palette active = Palette.CRIMSON_LOGO;

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("avemujica_ui_theme.txt");
    }

    public static Palette current() {
        if (!loaded) {
            loaded = true;
            try {
                String stored = Files.readString(file(), StandardCharsets.UTF_8).trim();
                if (stored.equalsIgnoreCase("light")) active = Palette.LIGHT;
                else if (stored.equalsIgnoreCase("crimson") || stored.equalsIgnoreCase("dark"))
                    active = Palette.CRIMSON;
                else if (stored.equalsIgnoreCase("crimson_logo") || stored.equalsIgnoreCase("dark_logo"))
                    active = Palette.CRIMSON_LOGO;
            } catch (Exception ignored) { /* new installations use the LOGO theme */ }
        }
        return active;
    }

    /** Cycle LIGHT -> CRIMSON -> CRIMSON_LOGO -> LIGHT and persist the choice. */
    public static Palette toggle() {
        Palette current = current();
        active = switch (current) {
            case LIGHT -> Palette.CRIMSON;
            case CRIMSON -> Palette.CRIMSON_LOGO;
            case CRIMSON_LOGO -> Palette.LIGHT;
        };
        try {
            Files.createDirectories(file().getParent());
            String value = switch (active) {
                case LIGHT -> "light";
                case CRIMSON -> "crimson";
                case CRIMSON_LOGO -> "crimson_logo";
            };
            Files.writeString(file(), value, StandardCharsets.UTF_8);
        } catch (Exception ignored) { /* active theme still changes this session */ }
        return active;
    }

    public static String label() {
        return switch (current()) {
            case LIGHT -> "淡蓝 · 白色";
            case CRIMSON -> "绯红 · 暗黑";
            case CRIMSON_LOGO -> "绯红 · LOGO";
        };
    }
}

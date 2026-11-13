package com.miseru.avemujica.block;

import net.minecraft.util.StringRepresentable;

/** A single structural block of the full-size portable stage. */
public enum StagePart implements StringRepresentable {
    DECK("deck"), EDGE("edge"), STEP("step"), POST("post"), SUPPORT("support"),
    BEAM_X("beam_x"), BEAM_Z("beam_z"), SPEAKER("speaker"),
    MONITOR("monitor"),
    LAMP_PURPLE("lamp_purple"), LAMP_GREEN("lamp_green"),
    LAMP_CYAN("lamp_cyan"), LAMP_PINK("lamp_pink"), LAMP_GOLD("lamp_gold"),
    LAMP_SKY_BLUE("lamp_sky_blue"), LAMP_GRAY_BLUE("lamp_gray_blue"),
    LV2_DECK("lv2_deck"), LV2_DECK_LIT("lv2_deck_lit"),
    LV2_CARPET_LIT("lv2_carpet_lit"),
    LV2_BORDER("lv2_border"),
    LV2_BLACK("lv2_black"),
    LV2_RED("lv2_red"),
    LV2_CARPET("lv2_carpet"),
    LV2_RAIL("lv2_rail"),
    LV2_RAIL_X("lv2_rail_x"), LV2_RAIL_Z("lv2_rail_z"),
    LV2_RAIL_CORNER_NE("lv2_rail_corner_ne"), LV2_RAIL_CORNER_NW("lv2_rail_corner_nw"),
    LV2_RAIL_CORNER_SE("lv2_rail_corner_se"), LV2_RAIL_CORNER_SW("lv2_rail_corner_sw"),
    LV2_RAIL_END_N("lv2_rail_end_n"), LV2_RAIL_END_E("lv2_rail_end_e"),
    LV2_RAIL_END_S("lv2_rail_end_s"), LV2_RAIL_END_W("lv2_rail_end_w"),
    LV2_STAIR_HALF("lv2_stair_half"), // Pre-1.1.92 saved stairs
    LV2_STAIR_SOUTH("lv2_stair_south"), LV2_STAIR_WEST("lv2_stair_west"),
    LV2_STAIR_NORTH("lv2_stair_north"), LV2_STAIR_EAST("lv2_stair_east"),
    LV2_TRUSS("lv2_truss"),
    LV2_GOLD("lv2_gold"),
    LV2_WINDOW_GOLD("lv2_window_gold"),
    LV2_WINDOW_VIOLET("lv2_window_violet"),
    LV2_WINDOW_BLUE("lv2_window_blue"),
    LV2_WINDOW_PINK("lv2_window_pink"),
    LV2_ROSE("lv2_rose"),
    LV2_ROSE_DARK("lv2_rose_dark"),
    LV2_ROSE_GOLD("lv2_rose_gold"),
    LV2_ROSE_WHITE("lv2_rose_white"),
    LV2_SPOTLIGHT("lv2_spotlight"),
    LV2_KEYBOARD("lv2_keyboard"),
    LV2_DRUMS("lv2_drums");

    private final String name;
    StagePart(String name) { this.name = name; }
    @Override public String getSerializedName() { return name; }
    /** Visually identical flooring with the maximum Minecraft block-light value. */
    public boolean isLevelTwoFullLight() {
        return this == LV2_DECK_LIT || this == LV2_CARPET_LIT;
    }
    public boolean isLamp() { return name.startsWith("lamp_") || name.equals("lv2_gold") || name.startsWith("lv2_window_")
                || name.equals("lv2_rose") || name.equals("lv2_rose_gold")
                || name.equals("lv2_rose_white") || name.equals("lv2_spotlight"); }
}

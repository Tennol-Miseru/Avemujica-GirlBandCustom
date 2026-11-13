package com.miseru.avemujica.menu;

import com.miseru.avemujica.entity.MemberRole;

/** Explicit role-specific, single-NPC-safe pose whitelist. Duo states are never manually forced. */
public final class PoseDebugActions {
    private PoseDebugActions() { }
    public static final int SLOT_COUNT = 5;
    private static final int NONE = -1;
    // Order follows MemberRole values only through explicit switches, not network input.
    private static final int[] SAKIKO = {40, 5, 6, 20, 27};
    private static final int[] UIKA = {41, 15, 23, 26, 25};
    private static final int[] MUTSUMI = {42, 9, 21, NONE, NONE};
    private static final int[] UMIRI = {43, 10, 11, 2, 19};
    private static final int[] NYAMU = {44, 12, 3, 13, NONE};
    private static final String[] SAKIKO_NAMES = {"优雅谢幕", "沉思", "翻阅乐谱", "喝咖啡", "阅读歌词"};
    private static final String[] UIKA_NAMES = {"抒情姿势", "练声", "喝咖啡", "查看手机歌词", "静静凝视"};
    private static final String[] MUTSUMI_NAMES = {"恬静姿势", "调试吉他", "吃黄瓜", "", ""};
    private static final String[] UMIRI_NAMES = {"冷静站姿", "肩部热身", "喝水", "吃蛋白棒", "关心队友"};
    private static final String[] NYAMU_NAMES = {"偶像 Pose", "整理刘海", "拍照", "集体自拍", ""};

    private static int[] actions(MemberRole role) {
        return switch (role) {
            case OBLIVIONIS -> SAKIKO;
            case DOLORIS -> UIKA;
            case MORTIS -> MUTSUMI;
            case TIMORIS -> UMIRI;
            case AMORIS -> NYAMU;
        };
    }
    private static String[] labels(MemberRole role) {
        return switch (role) {
            case OBLIVIONIS -> SAKIKO_NAMES;
            case DOLORIS -> UIKA_NAMES;
            case MORTIS -> MUTSUMI_NAMES;
            case TIMORIS -> UMIRI_NAMES;
            case AMORIS -> NYAMU_NAMES;
        };
    }
    public static int action(MemberRole role, int slot) {
        if (role == null || slot < 0 || slot >= SLOT_COUNT) return NONE;
        return actions(role)[slot];
    }
    public static String label(MemberRole role, int slot) {
        if (role == null || slot < 0 || slot >= SLOT_COUNT) return "";
        return labels(role)[slot];
    }
}

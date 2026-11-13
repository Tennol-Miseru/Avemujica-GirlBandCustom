package com.miseru.avemujica.entity;

/** Server-authored band orders. NONE means the NPC is under individual control. */
public enum TeamMode {
    NONE("个人任务"), FOLLOW("跟随玩家"), REHEARSAL("团队排练"), PERFORMANCE("正式演奏"),
    REST("全员休息"), STANDBY("原地待命"), FREE("自由活动");

    public final String label;
    TeamMode(String label) { this.label = label; }
    public static TeamMode fromId(int id) {
        return values()[Math.max(0, Math.min(values().length - 1, id))];
    }
}

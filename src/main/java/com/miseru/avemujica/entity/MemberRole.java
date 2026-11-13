package com.miseru.avemujica.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum MemberRole {
    DOLORIS("doloris", "三角初华 · Doloris", 16, 3.3f, 0.8f, SoundEvents.NOTE_BLOCK_GUITAR.value(), 0xFFD34E),
    MORTIS("mortis", "若叶睦 · Mortis", 26, 5.6f, 1.65f, SoundEvents.NOTE_BLOCK_GUITAR.value(), 0x7ECF74),
    TIMORIS("timoris", "八幡海铃 · Timoris", 29, 2.8f, 1.15f, SoundEvents.NOTE_BLOCK_BASS.value(), 0x8196AF),
    AMORIS("amoris", "祐天寺若麦 · Amoris", 34, 3.6f, 1.9f, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0xAA73E8),
    OBLIVIONIS("oblivionis", "丰川祥子 · Oblivionis", 22, 2.5f, 0.8f, SoundEvents.NOTE_BLOCK_PLING.value(), 0x6CCAF3);

    public final String id;
    public final String display;
    public final int attackInterval;
    public final float damage;
    public final float knockback;
    public final SoundEvent sound;
    public final int themeColor;

    MemberRole(String id, String display, int attackInterval, float damage, float knockback, SoundEvent sound, int themeColor) {
        this.id = id;
        this.display = display;
        this.attackInterval = attackInterval;
        this.damage = damage;
        this.knockback = knockback;
        this.sound = sound;
        this.themeColor = themeColor;
    }

    public float colorR() { return ((themeColor >> 16) & 255) / 255.0F; }
    public float colorG() { return ((themeColor >> 8) & 255) / 255.0F; }
    public float colorB() { return (themeColor & 255) / 255.0F; }

    public static MemberRole byId(String name) {
        for (MemberRole role : values()) if (role.id.equalsIgnoreCase(name)) return role;
        return DOLORIS;
    }
}

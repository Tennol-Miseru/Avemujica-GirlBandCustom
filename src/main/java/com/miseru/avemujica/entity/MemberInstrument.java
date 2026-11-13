package com.miseru.avemujica.entity;

import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.world.item.Item;

/** Stable save IDs, original animation profiles and MIDI defaults. */
public enum MemberInstrument {
    GUITAR("guitar", "吉他", MemberRole.DOLORIS, "guitar"),
    ELECTRIC("electric_guitar", "电吉他", MemberRole.MORTIS, "electric"),
    BASS("bass", "贝斯", MemberRole.TIMORIS, "bass"),
    KEYBOARD("keyboard", "合成器键盘", MemberRole.OBLIVIONIS, "pling"),
    DRUMS("drums", "架子鼓", MemberRole.AMORIS, "drums");

    private static final MemberInstrument[] TYPES = values();
    public final String id, label, preset;
    public final MemberRole pose;

    MemberInstrument(String id, String label, MemberRole pose, String preset) {
        this.id = id;
        this.label = label;
        this.pose = pose;
        this.preset = preset;
    }

    public static MemberInstrument defaultFor(MemberRole role) {
        for (MemberInstrument instrument : TYPES) if (instrument.pose == role) return instrument;
        throw new IllegalArgumentException("Missing default instrument for " + role);
    }
    public static MemberInstrument find(String id) {
        for (MemberInstrument instrument : TYPES) if (instrument.id.equals(id)) return instrument;
        return null;
    }
    public static MemberInstrument at(int index, MemberRole role) {
        return index >= 0 && index < TYPES.length ? TYPES[index] : defaultFor(role);
    }
    public boolean guitars() { return this == GUITAR || this == ELECTRIC; }
    public boolean strings() { return guitars() || this == BASS; }

    public Item model() {
        return switch (this) {
            case GUITAR -> ModRegistry.GUITAR;
            case ELECTRIC -> ModRegistry.RHYTHM_GUITAR;
            case BASS -> ModRegistry.BASS;
            case KEYBOARD -> ModRegistry.STAGE_KEYBOARD_ITEM;
            case DRUMS -> ModRegistry.DRUM_KIT_ITEM;
        };
    }
    public Item heldItem() {
        return switch (this) {
            case KEYBOARD -> ModRegistry.KEYBOARD;
            case DRUMS -> ModRegistry.DRUMSTICK;
            default -> model();
        };
    }

    /** Translate melodic notes into drum strikes; retain genuine GM percussion unchanged. */
    public int notePitch(int pitch, boolean percussionTrack) {
        if (this != DRUMS || percussionTrack) return pitch;
        return switch (Math.floorMod(pitch, 12)) {
            case 0, 5, 7 -> 36;
            case 2, 4, 9, 11 -> 38;
            default -> 42;
        };
    }
}

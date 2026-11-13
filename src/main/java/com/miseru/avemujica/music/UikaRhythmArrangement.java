package com.miseru.avemujica.music;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A separate, lightweight rhythm-guitar performance from the chord/harmonic
 * context of a bundled MIDI that has no dedicated Uika guitar track.
 * Never rewrites the MIDI, removes Mortis notes, or copies the full lead line.
 * Explicit authored Doloris/rhythm-guitar tracks take precedence elsewhere.
 */
public final class UikaRhythmArrangement {
    private UikaRhythmArrangement() { }

    public record Event(int tick, int pitch, int velocity) { }

    public static List<Event> derive(List<Event> guitar) {
        if (guitar.isEmpty()) return List.of();
        Map<Integer, List<Event>> chords = new LinkedHashMap<>();
        for (Event event : guitar) {
            if (event.velocity() <= 0 || event.pitch() < 24 || event.pitch() > 108) continue;
            chords.computeIfAbsent(event.tick(), unused -> new ArrayList<>()).add(event);
        }
        List<Event> rhythm = new ArrayList<>();
        int lastTick = -10000;
        int onsets = 0;
        for (var chord : chords.entrySet()) {
            int tick = chord.getKey();
            // Roughly every third distinct chord hit, with a 0.7-second gap.
            // The bundled source has far denser repeating six/eight-note chords.
            if (onsets++ % 3 != 0 || tick - lastTick < 14) continue;
            Event voiced = chord.getValue().stream()
                    .min(Comparator.comparingInt(n -> Math.abs(n.pitch() - 59)))
                    .orElse(null);
            if (voiced == null) continue;
            int pitch = voiced.pitch();
            while (pitch < 52) pitch += 12;
            while (pitch > 67) pitch -= 12;
            rhythm.add(new Event(tick, pitch, Math.min(84, Math.max(48, voiced.velocity() - 20))));
            lastTick = tick;
        }
        return List.copyOf(rhythm);
    }
}

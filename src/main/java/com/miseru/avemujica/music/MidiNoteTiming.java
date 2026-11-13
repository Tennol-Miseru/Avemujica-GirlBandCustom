package com.miseru.avemujica.music;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;

/** Matches MIDI key releases to presses, including simultaneous same-pitch notes
 * and the channel's CC64 sustain-pedal lifetime. No Minecraft dependencies;
 * all event times are in fractional Minecraft ticks after tempo conversion.
 */
public final class MidiNoteTiming {
    private record Start(int index, int channel, double tick) {}
    private final Map<Integer, ArrayDeque<Start>> down = new HashMap<>();
    private final ArrayList<Start>[] held;
    private final boolean[] pedal = new boolean[16];
    private final BiConsumer<Integer, Integer> complete;

    @SuppressWarnings("unchecked")
    public MidiNoteTiming(BiConsumer<Integer, Integer> complete) {
        this.complete = complete;
        held = (ArrayList<Start>[])new ArrayList<?>[16];
        for (int i = 0; i < 16; i++) held[i] = new ArrayList<>();
    }

    private static int key(int track, int channel, int pitch) {
        return (track << 11) | (channel << 7) | pitch;
    }

    public void noteOn(int track, int channel, int pitch, int index, double tick) {
        down.computeIfAbsent(key(track, channel, pitch), k -> new ArrayDeque<>())
                .addLast(new Start(index, channel, tick));
    }

    public void noteOff(int track, int channel, int pitch, double tick) {
        int key = key(track, channel, pitch);
        ArrayDeque<Start> queue = down.get(key);
        if (queue == null || queue.isEmpty()) return;
        Start start = queue.removeFirst(); // FIFO matches stacked same-key NOTE_ON events
        if (queue.isEmpty()) down.remove(key);
        if (pedal[channel]) held[channel].add(start);
        else release(start, tick);
    }

    public void control(int channel, int controller, int value, double tick) {
        if (controller == 64) {
            boolean newDown = value >= 64;
            if (pedal[channel] && !newDown) releaseHeld(channel, tick);
            pedal[channel] = newDown;
        } else if (controller == 120 || controller == 123) {
            // Sound off / all notes off: never leave a phantom sustain.
            pedal[channel] = false;
            releaseHeld(channel, tick);
            Iterator<Map.Entry<Integer, ArrayDeque<Start>>> it = down.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (((entry.getKey() >> 7) & 15) != channel) continue;
                for (Start start : entry.getValue()) release(start, tick);
                it.remove();
            }
        }
    }

    private void releaseHeld(int channel, double tick) {
        for (Start s : held[channel]) release(s, tick);
        held[channel].clear();
    }

    private void release(Start s, double tick) {
        int ticks = Math.max(1, Math.min(1200, (int)Math.round(tick - s.tick)));
        complete.accept(s.index, ticks);
    }

    public void finish(double tick) {
        // Broken MIDI files can omit NOTE_OFF. Do not manufacture long drones.
        for (ArrayDeque<Start> queue : down.values())
            for (Start s : queue) complete.accept(s.index, 5);
        down.clear();
        for (ArrayList<Start> tail : held) {
            for (Start s : tail) release(s, tick);
            tail.clear();
        }
    }
}

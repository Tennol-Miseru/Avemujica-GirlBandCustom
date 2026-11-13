package com.miseru.avemujica.career;

import com.miseru.avemujica.entity.MemberInstrument;
import com.miseru.avemujica.entity.MemberRole;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/** Per-player persistent band careers, all owned by the overworld's SavedData storage. */
public class BandCareerData extends SavedData {
    private static final String FILE = "avemujica_careers_v2";
    public final Map<UUID, Career> careers = new HashMap<>();

    public static BandCareerData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BandCareerData::new, BandCareerData::load, null), FILE);
    }

    public Career career(UUID player) {
        Career value = careers.get(player);
        if (value == null) {
            value = new Career();
            careers.put(player, value);
            setDirty();
        }
        return value;
    }

    public double combatMultiplier(UUID player) {
        if (player == null) return 1D;
        Career c = careers.get(player);
        if (c == null) return 1D;
        return Math.min(1.5D, 1D + c.skill / 500D + c.soundLevel * .035D + c.bond / 700D);
    }

    private static BandCareerData load(CompoundTag tag, HolderLookup.Provider lookup) {
        BandCareerData data = new BandCareerData();
        ListTag all = tag.getList("Careers", Tag.TAG_COMPOUND);
        for (int i = 0; i < all.size(); i++) {
            CompoundTag t = all.getCompound(i);
            try { data.careers.put(t.getUUID("Player"), Career.load(t)); }
            catch (IllegalArgumentException ignored) { }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        ListTag all = new ListTag();
        for (Map.Entry<UUID, Career> entry : careers.entrySet()) {
            CompoundTag t = entry.getValue().save();
            t.putUUID("Player", entry.getKey());
            all.add(t);
        }
        root.put("Careers", all);
        return root;
    }

    public static final class Career {
        public int fans = 0, fame = 0, bond = 0, skill = 0, morale = 65;
        public int soundLevel = 0, lightLevel = 0, shows = 0;
        // Band XP controls the quality of rewards obtainable through village tours.
        public int bandXp = 0, villageShows = 0;
        public final Map<String, Long> tourHistory = new LinkedHashMap<>();
        // Sold merch becomes occasional village decorations, queued for loaded, visited villages.
        public final List<Merchandising.DecorJob> decorJobs = new ArrayList<>();
        public final Map<String,Integer> decorPlaced = new LinkedHashMap<>();
        /** Octopus consignment inventory. Never consume goods directly from player inventory. */
        public final int[] consignmentStock = new int[Merchandising.TYPES];
        /** Sales revenues are collected separately inside the octopus sales screen. */
        public int consignmentEmeralds = 0;
        public int consignmentDailySold = 0, consignmentTourSold = 0;
        /** Server game-time due date, only progressed while the owner is online. */
        public long nextConsignmentSaleTick = 0L;
        // Published tour and manually-authored camera feed (newest at the end). Max 512 metadata posts.
        public final List<TourPost> tourPosts = new ArrayList<>();
        /** Owner-only pinned post. The key is validated against existing posts. */
        public String pinnedPostKey = "";
        public int accountTotalLikes = 0;
        public int cameraPostCount = 0;
        public long lastCameraPostTick = -100000L;
        public static final int MAX_TOUR_POSTS = 512;
        public void publish(TourPost post) {
            tourPosts.add(post);
            accountTotalLikes = (int)Math.min(Integer.MAX_VALUE, (long)accountTotalLikes + post.likes());
            if (tourPosts.size() > MAX_TOUR_POSTS) {
                TourPost removed = tourPosts.remove(0);
                if (removed.feedKey().equals(pinnedPostKey)) pinnedPostKey = "";
            }
        }
        // Server-owned escrow: rewards remain claimable even while the owner is offline.
        public final Map<String, Integer> pendingRewards = new LinkedHashMap<>();
        // Prevent duplicate band members when their original chunks are unloaded.
        public final Map<String, UUID> octopusMembers = new LinkedHashMap<>();
        // Server-authoritative per-role NPC snapshots, persisted under the owning PLAYER UUID.
        // Deliberately independent of entity/chunk survival and Minecraft respawn.
        public final Map<String, CompoundTag> memberProfiles = new LinkedHashMap<>();
        /** Actual deaths only. Recall from an unloaded chunk is not a death. */
        public final Set<String> awaitingRevival = new LinkedHashSet<>();
        /** Last successful resurrection + 180 seconds, keyed by member role. */
        public final Map<String, Long> revivalReadyAt = new LinkedHashMap<>();
        public String selectedMidi = "default"; // One shared ensemble MIDI, saved per player.
        // Blue octopus nameplate preference: older saves keep the original visible default.
        // Per-owner and persisted separately from individual NPC snapshots.
        public boolean memberNameTagsVisible = true;
        // The sky is a CLIENT-SIDE visual option, independent of concert buffs.
        // Older saves default to the existing visible red moon.
        public boolean redMoonEnabled = true;
        public boolean redMoonControlUnlocked = false;
        // Player-owned sound choices use string IDs, not enum ordinals, for forward compatibility.
        public final Map<String, String> instrumentPresets = new LinkedHashMap<>();
        // Owner-specific mix levels for the five members + Uika's separate guitar.
        // No saved entry = legacy 100%, values are bounded to 0..200.
        public final Map<String, Integer> instrumentVolumes = new LinkedHashMap<>();
        /** Appearance-only instrument finishes. Missing key retains the existing original model. */
        public final Map<String, Integer> instrumentColors = new LinkedHashMap<>();
        public final Map<String, MemberInstrument> memberInstruments = new LinkedHashMap<>();
        public boolean uikaInstrumentEnabled = true;

        public MemberInstrument instrumentFor(MemberRole role) {
            MemberInstrument selected = memberInstruments.get(role.id);
            return selected == null ? MemberInstrument.defaultFor(role) : selected;
        }
        public boolean usesInstrument(MemberRole role) {
            return role != MemberRole.DOLORIS || uikaInstrumentEnabled;
        }
        /** Opt-out switches and per-member cooldowns for rare daily gifts; default is enabled. */
        /** Per-member opt-out for spontaneous idle/social animations. Legacy saves default ON. */
        public final Map<String, Boolean> dailyActionsEnabled = new LinkedHashMap<>();
        public boolean dailyActionsEnabled(String role) { return dailyActionsEnabled.getOrDefault(role, true); }
        /** Which members the owner wants to summon. Absent in pre-1.1.120 saves = all enabled. */
        public final Map<String, Boolean> memberSummonEnabled = new LinkedHashMap<>();
        public boolean summonEnabled(String role) { return memberSummonEnabled.getOrDefault(role, true); }
        public final Map<String, Boolean> dailyGiftEnabled = new LinkedHashMap<>();
        public final Map<String, Long> lastDailyGiftTick = new LinkedHashMap<>();
        public boolean giftsEnabled(String role) { return dailyGiftEnabled.getOrDefault(role, true); }

        // Pedalboards are independent of instrument assignment. Absent key = complete bypass,
        // preserving the exact v1.1.7 timbre/mix until a player deliberately opts in.
        public final Map<String, String> guitarPedalPresets = new LinkedHashMap<>();
        public final Map<String, Integer> guitarPedalMix = new LinkedHashMap<>();
        public int showType = 0; // 0 = ordinary show, 1 = village tour
        public String showVillageKey = "";
        public boolean firstVillageTour = false;
        // Active stage-time countdown: unloaded chunks must not fast-forward 45s shows.
        // -1 is the legacy v0.7.7 timer, still readable for in-progress old saves.
        public int showTicksLeft = -1;
        public long lastLoadedShowTick = 0L;

        public static int threshold(int level) { return 70 * level * (level - 1) / 2; }
        public int bandLevel() {
            int lv = 1;
            while (lv < 10 && bandXp >= threshold(lv + 1)) lv++;
            return lv;
        }
        public int nextLevelXp() { return bandLevel() >= 10 ? 0 : threshold(bandLevel() + 1); }
        public void grantXp(int amount) { bandXp = Mth.clamp(bandXp + Math.max(0, amount), 0, 1000000); }
        public void addLoot(String key, int amount) {
            if (amount > 0) pendingRewards.merge(key, amount, (a, b) -> Math.min(100000, a + b));
        }
        public int pendingCount() { return pendingRewards.values().stream().mapToInt(Integer::intValue).sum(); }
        public boolean recruited = false;
        public boolean starterOctopusGranted = false;
        /** Last request to Sakiko for a lost octopus; prevents unlimited free duplication. */
        public long lastOctopusReplacementTick = -6000L;
        public long lastPractice = -100000L, lastPromote = -100000L;
        public long endShowAt = 0L;
        public int stageX, stageY, stageZ;
        public int deskX, deskY, deskZ;

        public boolean performing() { return endShowAt > 0L; }
        public void clamp() {
            fans = Mth.clamp(fans, 0, 10000000);
            fame = Mth.clamp(fame, 0, 100); bond = Mth.clamp(bond, 0, 100);
            skill = Mth.clamp(skill, 0, 100); morale = Mth.clamp(morale, 0, 100);
            soundLevel = Mth.clamp(soundLevel, 0, 5); lightLevel = Mth.clamp(lightLevel, 0, 5);
            bandXp = Mth.clamp(bandXp, 0, 1000000);
            villageShows = Mth.clamp(villageShows, 0, 10000000);
            consignmentEmeralds = Mth.clamp(consignmentEmeralds, 0, Merchandising.MAX_UNCLAIMED);
        }
        private CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Fans", fans); t.putInt("Fame", fame);
            t.putInt("Bond", bond); t.putInt("Skill", skill); t.putInt("Morale", morale);
            t.putInt("Sound", soundLevel); t.putInt("Light", lightLevel); t.putInt("Shows", shows);
            t.putBoolean("Recruited", recruited); t.putBoolean("StarterOctopusGranted", starterOctopusGranted); t.putLong("LastOctopusReplacementTick", lastOctopusReplacementTick); t.putLong("LastPractice", lastPractice);
            t.putLong("LastPromote", lastPromote); t.putLong("EndShowAt", endShowAt);
            t.putInt("StageX", stageX); t.putInt("StageY", stageY); t.putInt("StageZ", stageZ);
            t.putInt("DeskX", deskX); t.putInt("DeskY", deskY); t.putInt("DeskZ", deskZ);
            t.putInt("BandXp", bandXp); t.putInt("VillageShows", villageShows);
            t.putInt("ShowType", showType); t.putString("ShowVillageKey", showVillageKey);
            t.putString("SelectedMidi", selectedMidi);
            t.putBoolean("MemberNameTagsVisible", memberNameTagsVisible);
            t.putBoolean("RedMoonEnabled", redMoonEnabled);
            t.putBoolean("RedMoonControlUnlocked", redMoonControlUnlocked);
            ListTag instruments = new ListTag();
            for (Map.Entry<String, String> entry : instrumentPresets.entrySet()) {
                CompoundTag row = new CompoundTag();
                row.putString("Role", entry.getKey()); row.putString("Preset", entry.getValue());
                instruments.add(row);
            }
            t.put("MidiInstruments", instruments);
            ListTag volumes = new ListTag();
            for (Map.Entry<String, Integer> entry : instrumentVolumes.entrySet()) {
                if (!entry.getKey().matches("[a-z0-9_]{1,40}")) continue;
                CompoundTag row = new CompoundTag();
                row.putString("Role", entry.getKey());
                row.putInt("Percent", Mth.clamp(entry.getValue(), 0, 200));
                volumes.add(row);
            }
            t.put("MidiVolumes", volumes);
            ListTag finishes = new ListTag();
            for (com.miseru.avemujica.entity.MemberRole role : com.miseru.avemujica.entity.MemberRole.values()) {
                int color = instrumentColors.getOrDefault(role.id, 0);
                if (!InstrumentPalette.valid(color) || color == 0) continue;
                CompoundTag row = new CompoundTag();
                row.putString("Role", role.id);
                row.putInt("Color", color);
                finishes.add(row);
            }
            t.put("InstrumentColors", finishes);
            CompoundTag lineup = new CompoundTag();
            memberInstruments.forEach((role, instrument) -> lineup.putString(role, instrument.id));
            t.put("MemberInstruments", lineup);
            t.putBoolean("UikaInstrumentEnabled", uikaInstrumentEnabled);
            ListTag gifts = new ListTag();
            for (String role : java.util.List.of("doloris", "mortis", "timoris", "amoris")) {
                CompoundTag row = new CompoundTag();
                row.putString("Role", role);
                row.putBoolean("Enabled", giftsEnabled(role));
                row.putLong("LastTick", lastDailyGiftTick.getOrDefault(role, -1L));
                gifts.add(row);
            }
            t.put("DailyGifts", gifts);
            ListTag actions = new ListTag();
            for (com.miseru.avemujica.entity.MemberRole role : com.miseru.avemujica.entity.MemberRole.values()) {
                CompoundTag row = new CompoundTag();
                row.putString("Role", role.id);
                row.putBoolean("Enabled", dailyActionsEnabled(role.id));
                actions.add(row);
            }
            t.put("DailyActions", actions);
            ListTag summonChoices = new ListTag();
            for (com.miseru.avemujica.entity.MemberRole role : com.miseru.avemujica.entity.MemberRole.values()) {
                CompoundTag row = new CompoundTag();
                row.putString("Role", role.id);
                row.putBoolean("Enabled", summonEnabled(role.id));
                summonChoices.add(row);
            }
            t.put("MemberSummonChoices", summonChoices);
            ListTag pedals = new ListTag();
            for (Map.Entry<String, String> entry : guitarPedalPresets.entrySet()) {
                CompoundTag row = new CompoundTag();
                if (entry.getKey() == null || entry.getValue() == null) continue;
                row.putString("Role", entry.getKey()); row.putString("Preset", entry.getValue());
                Integer mix = guitarPedalMix.get(entry.getKey());
                row.putInt("Mix", Mth.clamp(mix == null ? 100 : mix, 0, 100));
                pedals.add(row);
            }
            t.put("GuitarPedals", pedals);
            t.putBoolean("FirstVillageTour", firstVillageTour);
            t.putInt("ShowTicksLeft", showTicksLeft);
            t.putLong("LastLoadedShowTick", lastLoadedShowTick);
            ListTag visits = new ListTag();
            for (Map.Entry<String, Long> entry : tourHistory.entrySet()) {
                CompoundTag visit = new CompoundTag();
                visit.putString("Key", entry.getKey()); visit.putLong("Time", entry.getValue());
                visits.add(visit);
            }
            t.put("VillageHistory", visits);
            ListTag decor = new ListTag();
            for (Merchandising.DecorJob job : decorJobs) {
                CompoundTag row = new CompoundTag();
                row.putString("Key", job.villageKey());
                row.putInt("X",job.x()); row.putInt("Y",job.y()); row.putInt("Z",job.z());
                row.putInt("Type",job.type()); row.putLong("Due",job.due());
                row.putInt("Attempts",job.attempts()); decor.add(row);
            }
            t.put("MerchDecorJobs", decor);
            ListTag decorCounts = new ListTag();
            for (var placed : decorPlaced.entrySet()) {
                CompoundTag row = new CompoundTag();
                row.putString("Key", placed.getKey()); row.putInt("Count", placed.getValue());
                decorCounts.add(row);
            }
            t.put("MerchDecorPlaced", decorCounts);
            t.putIntArray("ConsignmentStock", consignmentStock);
            t.putInt("ConsignmentEmeralds", consignmentEmeralds);
            t.putInt("ConsignmentDailySold", consignmentDailySold);
            t.putInt("ConsignmentTourSold", consignmentTourSold);
            t.putLong("NextConsignmentSaleTick", nextConsignmentSaleTick);
            ListTag posts = new ListTag();
            for (TourPost post : tourPosts) posts.add(post.save());
            t.put("TourPosts", posts);
            t.putString("AccountPinnedPost", pinnedPostKey);
            t.putInt("AccountTotalLikes", accountTotalLikes);
            t.putInt("CameraPostCount", cameraPostCount);
            t.putLong("LastCameraPostTick", lastCameraPostTick);
            ListTag loot = new ListTag();
            for (Map.Entry<String, Integer> entry : pendingRewards.entrySet()) {
                CompoundTag reward = new CompoundTag();
                reward.putString("Item", entry.getKey()); reward.putInt("Count", entry.getValue());
                loot.add(reward);
            }
            t.put("PendingRewards", loot);
            ListTag octopus = new ListTag();
            for (Map.Entry<String, UUID> entry : octopusMembers.entrySet()) {
                CompoundTag member = new CompoundTag();
                member.putString("Role", entry.getKey()); member.putUUID("UUID", entry.getValue());
                octopus.add(member);
            }
            t.put("OctopusMembers", octopus);
            ListTag profiles = new ListTag();
            for (Map.Entry<String, CompoundTag> entry : memberProfiles.entrySet()) {
                CompoundTag row = new CompoundTag();
                row.putString("Role", entry.getKey());
                row.put("Data", entry.getValue().copy());
                profiles.add(row);
            }
            t.put("MemberProfiles", profiles);
            ListTag fallen = new ListTag();
            for (String role : awaitingRevival) {
                CompoundTag row = new CompoundTag();
                row.putString("Role", role);
                fallen.add(row);
            }
            t.put("AwaitingRevival", fallen);
            ListTag revivals = new ListTag();
            for (Map.Entry<String, Long> entry : revivalReadyAt.entrySet()) {
                CompoundTag row = new CompoundTag();
                row.putString("Role", entry.getKey());
                row.putLong("ReadyAt", entry.getValue());
                revivals.add(row);
            }
            t.put("RevivalReadyAt", revivals);
            return t;
        }
        private static Career load(CompoundTag t) {
            Career c = new Career();
            // Legacy virtual Cash is deliberately not converted into real emeralds.
            c.fans = t.getInt("Fans"); c.fame = t.getInt("Fame");
            c.bond = t.getInt("Bond"); c.skill = t.getInt("Skill"); c.morale = t.getInt("Morale");
            c.soundLevel = t.getInt("Sound"); c.lightLevel = t.getInt("Light"); c.shows = t.getInt("Shows");
            c.recruited = t.getBoolean("Recruited"); c.starterOctopusGranted = t.getBoolean("StarterOctopusGranted");
            c.lastOctopusReplacementTick = t.contains("LastOctopusReplacementTick") ? t.getLong("LastOctopusReplacementTick") : -6000L; c.lastPractice = t.getLong("LastPractice");
            c.lastPromote = t.getLong("LastPromote"); c.endShowAt = t.getLong("EndShowAt");
            c.stageX = t.getInt("StageX"); c.stageY = t.getInt("StageY"); c.stageZ = t.getInt("StageZ");
            c.deskX = t.contains("DeskX") ? t.getInt("DeskX") : c.stageX;
            c.deskY = t.contains("DeskY") ? t.getInt("DeskY") : c.stageY;
            c.deskZ = t.contains("DeskZ") ? t.getInt("DeskZ") : c.stageZ;
            c.bandXp = t.getInt("BandXp"); c.villageShows = t.getInt("VillageShows");
            c.showType = t.getInt("ShowType"); c.showVillageKey = t.getString("ShowVillageKey");
            String selected = t.getString("SelectedMidi");
            c.selectedMidi = selected.isBlank() ? "default" : selected;
            c.memberNameTagsVisible = !t.contains("MemberNameTagsVisible", Tag.TAG_BYTE)
                    || t.getBoolean("MemberNameTagsVisible");
            c.redMoonEnabled = !t.contains("RedMoonEnabled", Tag.TAG_BYTE)
                    || t.getBoolean("RedMoonEnabled");
            c.redMoonControlUnlocked = t.getBoolean("RedMoonControlUnlocked");
            for (Tag tag : t.getList("MidiInstruments", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) tag;
                String role = row.getString("Role"), preset = row.getString("Preset");
                if (role.matches("[a-z0-9_]{1,40}") && preset.matches("[a-z0-9_]{1,40}"))
                    c.instrumentPresets.put(role, preset);
            }
            for (Tag tag : t.getList("MidiVolumes", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag)tag;
                String role = row.getString("Role");
                if (role.matches("[a-z0-9_]{1,40}"))
                    c.instrumentVolumes.put(role, Mth.clamp(row.getInt("Percent"), 0, 200));
            }
            CompoundTag lineup = t.getCompound("MemberInstruments");
            for (MemberRole role : MemberRole.values()) {
                MemberInstrument selectedInstrument = MemberInstrument.find(lineup.getString(role.id));
                if (selectedInstrument != null && selectedInstrument != MemberInstrument.defaultFor(role))
                    c.memberInstruments.put(role.id, selectedInstrument);
            }
            c.uikaInstrumentEnabled = !t.contains("UikaInstrumentEnabled", Tag.TAG_BYTE)
                    || t.getBoolean("UikaInstrumentEnabled");
            for (Tag tag : t.getList("InstrumentColors", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) tag;
                String roleId = row.getString("Role");
                for (com.miseru.avemujica.entity.MemberRole role : com.miseru.avemujica.entity.MemberRole.values()) {
                    if (role.id.equals(roleId) && InstrumentPalette.valid(row.getInt("Color")))
                        c.instrumentColors.put(role.id, row.getInt("Color"));
                }
            }
            for (Tag tag : t.getList("DailyGifts", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag)tag;
                String role = row.getString("Role");
                if (java.util.List.of("doloris", "mortis", "timoris", "amoris").contains(role)) {
                    c.dailyGiftEnabled.put(role, row.getBoolean("Enabled"));
                    if (row.contains("LastTick")) c.lastDailyGiftTick.put(role, row.getLong("LastTick"));
                }
            }
            for (Tag tag : t.getList("DailyActions", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) tag;
                String roleId = row.getString("Role");
                for (com.miseru.avemujica.entity.MemberRole role : com.miseru.avemujica.entity.MemberRole.values())
                    if (role.id.equals(roleId)) c.dailyActionsEnabled.put(role.id, row.getBoolean("Enabled"));
            }
            for (Tag tag : t.getList("MemberSummonChoices", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) tag;
                String roleId = row.getString("Role");
                for (com.miseru.avemujica.entity.MemberRole role : com.miseru.avemujica.entity.MemberRole.values())
                    if (role.id.equals(roleId)) c.memberSummonEnabled.put(role.id, row.getBoolean("Enabled"));
            }
            for (Tag tag : t.getList("GuitarPedals", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) tag;
                String role = row.getString("Role"), preset = row.getString("Preset");
                if ((role.equals("doloris") || role.equals("doloris_guitar") || role.equals("mortis"))
                        && preset.matches("[a-z0-9_]{1,40}")) {
                    c.guitarPedalPresets.put(role, preset);
                    // Historical saves may not contain Mix. Default to 100, as before.
                    c.guitarPedalMix.put(role, Mth.clamp(row.contains("Mix", Tag.TAG_INT) ? row.getInt("Mix") : 100, 0, 100));
                }
            }
            c.firstVillageTour = t.getBoolean("FirstVillageTour");
            c.showTicksLeft = t.contains("ShowTicksLeft") ? t.getInt("ShowTicksLeft") : -1;
            c.lastLoadedShowTick = t.getLong("LastLoadedShowTick");
            for (Tag item : t.getList("VillageHistory", Tag.TAG_COMPOUND)) {
                CompoundTag visit = (CompoundTag)item;
                if (!visit.getString("Key").isEmpty()) c.tourHistory.put(visit.getString("Key"), visit.getLong("Time"));
            }
            for (Tag item : t.getList("MerchDecorJobs", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag)item;
                String key = row.getString("Key"); int type=row.getInt("Type");
                if (!key.isBlank() && key.length()<=96 && type>=0 && type<Merchandising.TYPES && c.decorJobs.size()<32)
                    c.decorJobs.add(new Merchandising.DecorJob(key,row.getInt("X"),row.getInt("Y"),
                            row.getInt("Z"),type,row.getLong("Due"),Math.max(0,row.getInt("Attempts"))));
            }
            for (Tag item : t.getList("MerchDecorPlaced", Tag.TAG_COMPOUND)) {
                CompoundTag row=(CompoundTag)item;
                String key=row.getString("Key");
                if (!key.isBlank() && key.length()<=96 && c.decorPlaced.size()<512)
                    c.decorPlaced.put(key,Math.max(0,Math.min(5,row.getInt("Count"))));
            }
            for (Tag item : t.getList("TourPosts", Tag.TAG_COMPOUND)) {
                if (c.tourPosts.size() >= MAX_TOUR_POSTS) c.tourPosts.remove(0);
                c.tourPosts.add(TourPost.load((CompoundTag)item));
            }
            String pin = t.getString("AccountPinnedPost");
            c.pinnedPostKey = pin.length() <= 64 && c.tourPosts.stream()
                    .anyMatch(p -> p.feedKey().equals(pin)) ? pin : "";
            c.cameraPostCount = Math.max(0, t.getInt("CameraPostCount"));
            c.lastCameraPostTick = t.contains("LastCameraPostTick") ? t.getLong("LastCameraPostTick") : -100000L;
            if (t.contains("AccountTotalLikes")) c.accountTotalLikes = Math.max(0,t.getInt("AccountTotalLikes"));
            else for (TourPost p : c.tourPosts)
                c.accountTotalLikes = (int)Math.min(Integer.MAX_VALUE, (long)c.accountTotalLikes + p.likes());
            int[] merchStock = t.getIntArray("ConsignmentStock");
            for (int i = 0; i < Math.min(merchStock.length, Merchandising.TYPES); i++)
                c.consignmentStock[i] = Mth.clamp(merchStock[i], 0, Merchandising.MAX_STOCK);
            c.consignmentEmeralds = Mth.clamp(t.getInt("ConsignmentEmeralds"), 0, Merchandising.MAX_UNCLAIMED);
            c.consignmentDailySold = Math.max(0, t.getInt("ConsignmentDailySold"));
            c.consignmentTourSold = Math.max(0, t.getInt("ConsignmentTourSold"));
            c.nextConsignmentSaleTick = Math.max(0L, t.getLong("NextConsignmentSaleTick"));
            for (Tag item : t.getList("PendingRewards", Tag.TAG_COMPOUND)) {
                CompoundTag reward = (CompoundTag)item;
                if (!reward.getString("Item").isEmpty() && reward.getInt("Count") > 0)
                    c.pendingRewards.put(reward.getString("Item"), Mth.clamp(reward.getInt("Count"), 1, 100000));
            }
            for (Tag item : t.getList("OctopusMembers", Tag.TAG_COMPOUND)) {
                CompoundTag member = (CompoundTag)item;
                if (member.hasUUID("UUID") && !member.getString("Role").isBlank())
                    c.octopusMembers.put(member.getString("Role"), member.getUUID("UUID"));
            }
            for (Tag item : t.getList("MemberProfiles", Tag.TAG_COMPOUND)) {
                CompoundTag member = (CompoundTag)item;
                if (!member.getString("Role").isBlank() && member.contains("Data", Tag.TAG_COMPOUND))
                    c.memberProfiles.put(member.getString("Role"), member.getCompound("Data").copy());
            }
            for (Tag item : t.getList("AwaitingRevival", Tag.TAG_COMPOUND)) {
                String role = ((CompoundTag)item).getString("Role");
                if (role.matches("(oblivionis|doloris|mortis|timoris|amoris)")) c.awaitingRevival.add(role);
            }
            for (Tag item : t.getList("RevivalReadyAt", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag)item;
                String role = row.getString("Role");
                if (role.matches("(oblivionis|doloris|mortis|timoris|amoris)"))
                    c.revivalReadyAt.put(role, Math.max(0L, row.getLong("ReadyAt")));
            }
            c.clamp(); return c;
        }
    }
}

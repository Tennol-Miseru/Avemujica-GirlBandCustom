package com.miseru.avemujica.registry;

import com.miseru.avemujica.AveMujicaMod;
import com.miseru.avemujica.block.ManagementDeskBlock;
import com.miseru.avemujica.block.TourStageBlock;
import com.miseru.avemujica.block.StagePartBlock;
import com.miseru.avemujica.block.ConcertLightOrbBlock;
import com.miseru.avemujica.block.ConcertLightOrbBlockEntity;
import com.miseru.avemujica.block.StageLogoBlockEntity;
import com.miseru.avemujica.block.StageLogoStageBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.miseru.avemujica.item.TourStageItem;
import com.miseru.avemujica.block.StagePart;
import com.miseru.avemujica.block.MerchDisplayBlock;
import com.miseru.avemujica.block.PhotoPosterBlock;
import com.miseru.avemujica.block.PhotoPosterPartBlock;
import com.miseru.avemujica.block.PhotoPosterBlockEntity;
import com.miseru.avemujica.block.MerchStallBlock;
import com.miseru.avemujica.block.MerchStallBlockEntity;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.NoteBoltEntity;
import com.miseru.avemujica.entity.RifleRoundEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.item.SummonMemberItem;
import com.miseru.avemujica.item.BlueOctopusItem;
import com.miseru.avemujica.item.MujicaCameraItem;
import com.miseru.avemujica.item.BandDebugToolItem;
import com.miseru.avemujica.item.PoseDebuggerItem;
import com.miseru.avemujica.menu.PoseDebugMenu;
import com.miseru.avemujica.menu.BandDebugMenu;
import com.miseru.avemujica.menu.SakikoConsoleMenu;
import com.miseru.avemujica.menu.BandManagementMenu;
import com.miseru.avemujica.menu.MemberInteractionMenu;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.EnumMap;

public final class ModRegistry {
    private ModRegistry() {}
    private static ResourceLocation id(String key) { return ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, key); }
    public static final MenuType<SakikoConsoleMenu> SAKIKO_CONSOLE_MENU = Registry.register(
            BuiltInRegistries.MENU, id("sakiko_console"),
            new MenuType<>(SakikoConsoleMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<MemberInteractionMenu> MEMBER_INTERACTION_MENU = Registry.register(
            BuiltInRegistries.MENU, id("member_interaction"),
            new MenuType<>(MemberInteractionMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<BandManagementMenu> BAND_MANAGEMENT_MENU = Registry.register(
            BuiltInRegistries.MENU, id("band_management"),
            new MenuType<>(BandManagementMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<BandDebugMenu> BAND_DEBUG_MENU = Registry.register(
            BuiltInRegistries.MENU, id("band_debug"),
            new MenuType<>(BandDebugMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<PoseDebugMenu> POSE_DEBUG_MENU = Registry.register(
            BuiltInRegistries.MENU, id("pose_debug"),
            new MenuType<>(PoseDebugMenu::new, FeatureFlags.VANILLA_SET));
    // Kept as a hidden legacy block ID so older worlds do not lose existing placed blocks.
    // There is no corresponding registered item, recipe, UI entry or gameplay dependency.
    public static final ManagementDeskBlock MANAGEMENT_DESK = Registry.register(BuiltInRegistries.BLOCK,
            id("management_desk"), new ManagementDeskBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN).strength(2.5F)));
    public static final EntityType<BandMemberEntity> BAND_MEMBER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            id("band_member"), EntityType.Builder.of(BandMemberEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).clientTrackingRange(8).updateInterval(3).build("avemujica:band_member"));
    public static final EntityType<NoteBoltEntity> NOTE_BOLT = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            id("note_bolt"), EntityType.Builder.<NoteBoltEntity>of(NoteBoltEntity::new, MobCategory.MISC).sized(0.3f,0.3f).clientTrackingRange(7).updateInterval(1).build("avemujica:note_bolt"));
    public static final EntityType<RifleRoundEntity> RIFLE_ROUND = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            id("rifle_round"), EntityType.Builder.<RifleRoundEntity>of(RifleRoundEntity::new, MobCategory.MISC)
                    .sized(.17F, .17F).clientTrackingRange(8).updateInterval(1).build("avemujica:rifle_round"));
    private static Item item(String id, Item item) { return Registry.register(BuiltInRegistries.ITEM, id(id), item); }
    public static final Block STAGE_KEYBOARD = Registry.register(BuiltInRegistries.BLOCK,
            id("stage_keyboard"), new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(1.8f)));
    public static final Block DRUM_KIT_BLOCK = Registry.register(BuiltInRegistries.BLOCK,
            id("drum_kit"), new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(1.8f)));
    public static final Block TOUR_STAGE = Registry.register(BuiltInRegistries.BLOCK,
            id("tour_stage"), new TourStageBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_DEEPSLATE).strength(2.4f)));
    public static final Block TOUR_STAGE_LEVEL2 = Registry.register(BuiltInRegistries.BLOCK,
            id("tour_stage_level2"), new StageLogoStageBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_DEEPSLATE).strength(2.4f)));
    public static final BlockEntityType<StageLogoBlockEntity> STAGE_LEVEL2_LOGO_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("stage_level2_logo"),
            FabricBlockEntityTypeBuilder.create(StageLogoBlockEntity::new, TOUR_STAGE_LEVEL2).build());

    public static boolean isTourStage(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(TOUR_STAGE) || state.is(TOUR_STAGE_LEVEL2);
    }
    public static final Block STAGE_STOOL = Registry.register(BuiltInRegistries.BLOCK,
            id("stage_stool"), new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_SLAB).noOcclusion().strength(1.2f)));
    public static final StagePartBlock STAGE_PART = Registry.register(BuiltInRegistries.BLOCK,
            id("stage_part"), new StagePartBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_DEEPSLATE)
                    .noOcclusion().strength(2.4f).lightLevel(state -> {
                        StagePart part = state.getValue(StagePartBlock.PART);
                        return part.isLevelTwoFullLight() ? 15 : (part.isLamp() ? 13 : 0);
                    })));
    public static final ConcertLightOrbBlock CONCERT_LIGHT_ORB = Registry.register(BuiltInRegistries.BLOCK,
            id("concert_light_orb"), new ConcertLightOrbBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SEA_LANTERN)
                    .strength(1.6F).lightLevel(state -> state.getValue(ConcertLightOrbBlock.LIT) ? 13 : 0)));
    public static final BlockEntityType<ConcertLightOrbBlockEntity> CONCERT_LIGHT_ORB_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("concert_light_orb"),
            FabricBlockEntityTypeBuilder.create(ConcertLightOrbBlockEntity::new, CONCERT_LIGHT_ORB).build());
    public static final Item CONCERT_LIGHT_ORB_ITEM = item("concert_light_orb",
            new net.minecraft.world.item.BlockItem(CONCERT_LIGHT_ORB, new Item.Properties().stacksTo(16)));
    public static final Item STAGE_KEYBOARD_ITEM = item("stage_keyboard",new net.minecraft.world.item.BlockItem(STAGE_KEYBOARD,new Item.Properties().stacksTo(16)));
    public static final Item DRUM_KIT_ITEM = item("drum_kit",new net.minecraft.world.item.BlockItem(DRUM_KIT_BLOCK,new Item.Properties().stacksTo(16)));
    public static final Item TOUR_STAGE_ITEM = item("tour_stage", new TourStageItem(TOUR_STAGE,new Item.Properties().stacksTo(16)));
    public static final Item TOUR_STAGE_LEVEL2_ITEM = item("tour_stage_level2",
            new TourStageItem(TOUR_STAGE_LEVEL2, new Item.Properties().stacksTo(16)));
    public static final Item STAGE_STOOL_ITEM = item("stage_stool",new net.minecraft.world.item.BlockItem(STAGE_STOOL,new Item.Properties().stacksTo(16)));
    public static final Item NOTE_ICON = item("note_icon",new Item(new Item.Properties()));
    public static final Item GUITAR = item("lead_guitar", new Item(new Item.Properties().stacksTo(1)));
    public static final Item RHYTHM_GUITAR = item("rhythm_guitar", new Item(new Item.Properties().stacksTo(1)));
    public static final Item BASS = item("bass", new Item(new Item.Properties().stacksTo(1)));
    public static final Item DRUMSTICK = item("drumstick", new Item(new Item.Properties().stacksTo(1)));
    public static final Item MICROPHONE = item("microphone", new Item(new Item.Properties().stacksTo(1)));
    public static final Item KEYBOARD = item("keyboard", new Item(new Item.Properties().stacksTo(1)));
    // Combat weapons are rendered on NPCs; these items are deliberately not player-crafted.
    public static final Item CHAINSAW = item("stage_chainsaw", new Item(new Item.Properties().stacksTo(1)));
    public static final Item RIFLE = item("stage_rifle", new Item(new Item.Properties().stacksTo(1)));
    public static final Item PHONE = item("smartphone", new Item(new Item.Properties().stacksTo(1)));
    public static final Item BLUE_OCTOPUS = item("blue_octopus", new BlueOctopusItem(new Item.Properties().stacksTo(1)));
    public static final Item DEBUG_TOOL = item("debug_tool", new BandDebugToolItem(new Item.Properties().stacksTo(1)));
    public static final Item POSE_DEBUGGER = item("pose_debugger", new PoseDebuggerItem(new Item.Properties().stacksTo(1)));
    public static final Item CAMERA = item("camera", new MujicaCameraItem(new Item.Properties().stacksTo(1)));
    // Player-facing placeable merchandise. Legacy merch_doll is kept only for world/save compatibility.
    public static final PhotoPosterBlock MERCH_POSTER = Registry.register(BuiltInRegistries.BLOCK, id("merch_poster"),
            new PhotoPosterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion()
                    .noCollission().strength(.7F).lightLevel(state -> 15)));
    // Invisible structure cells are placed and removed as part of the anchor poster; no item.
    public static final PhotoPosterPartBlock MERCH_POSTER_PART = Registry.register(
            BuiltInRegistries.BLOCK, id("merch_poster_part"),
            new PhotoPosterPartBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                    .noOcclusion().noCollission().strength(.7F).lightLevel(state -> 15)));
    // Keep the original poster block/item ID and crafting recipe.
    public static final BlockEntityType<PhotoPosterBlockEntity> PHOTO_POSTER_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("photo_poster"),
            FabricBlockEntityTypeBuilder.create(PhotoPosterBlockEntity::new, MERCH_POSTER).build());
    public static final Block MERCH_LIGHTSTICK = Registry.register(BuiltInRegistries.BLOCK, id("merch_lightstick"),
            new MerchDisplayBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).noOcclusion()
                    .noCollission().strength(.7F).lightLevel(state -> 9), Block.box(3,0,3,13,13,13)));
    public static final Block MERCH_RECORD = Registry.register(BuiltInRegistries.BLOCK, id("merch_record"),
            new MerchDisplayBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS).noOcclusion()
                    .noCollission().strength(.7F), Block.box(2,0,5,14,14,11)));
    public static final Block MERCH_DOLL = Registry.register(BuiltInRegistries.BLOCK, id("merch_doll"),
            new MerchDisplayBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion()
                    .noCollission().strength(.7F), Block.box(1,0,3,15,10,13)));
    public static final Item MERCH_POSTER_ITEM = item("merch_poster", new net.minecraft.world.item.BlockItem(
            MERCH_POSTER, new Item.Properties().stacksTo(64)));
    public static final Item MERCH_LIGHTSTICK_ITEM = item("merch_lightstick", new net.minecraft.world.item.BlockItem(
            MERCH_LIGHTSTICK, new Item.Properties().stacksTo(64)));
    public static final Item MERCH_RECORD_ITEM = item("merch_record", new net.minecraft.world.item.BlockItem(
            MERCH_RECORD, new Item.Properties().stacksTo(64)));
    public static final Item MERCH_DOLL_ITEM = item("merch_doll", new net.minecraft.world.item.BlockItem(
            MERCH_DOLL, new Item.Properties().stacksTo(64)));
    public static final Block MERCH_DOLL_OBLIVIONIS = Registry.register(BuiltInRegistries.BLOCK, id("merch_doll_oblivionis"),
            new MerchDisplayBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion()
                    .noCollission().strength(.7F), Block.box(2,0,2,14,15,14)));
    public static final Block MERCH_DOLL_DOLORIS = Registry.register(BuiltInRegistries.BLOCK, id("merch_doll_doloris"),
            new MerchDisplayBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion()
                    .noCollission().strength(.7F), Block.box(2,0,2,14,15,14)));
    public static final Block MERCH_DOLL_MORTIS = Registry.register(BuiltInRegistries.BLOCK, id("merch_doll_mortis"),
            new MerchDisplayBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion()
                    .noCollission().strength(.7F), Block.box(2,0,2,14,15,14)));
    public static final Block MERCH_DOLL_TIMORIS = Registry.register(BuiltInRegistries.BLOCK, id("merch_doll_timoris"),
            new MerchDisplayBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion()
                    .noCollission().strength(.7F), Block.box(2,0,2,14,15,14)));
    public static final Block MERCH_DOLL_AMORIS = Registry.register(BuiltInRegistries.BLOCK, id("merch_doll_amoris"),
            new MerchDisplayBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion()
                    .noCollission().strength(.7F), Block.box(2,0,2,14,15,14)));
    public static final Item MERCH_DOLL_OBLIVIONIS_ITEM = item("merch_doll_oblivionis", new net.minecraft.world.item.BlockItem(
            MERCH_DOLL_OBLIVIONIS, new Item.Properties().stacksTo(64)));
    public static final Item MERCH_DOLL_DOLORIS_ITEM = item("merch_doll_doloris", new net.minecraft.world.item.BlockItem(
            MERCH_DOLL_DOLORIS, new Item.Properties().stacksTo(64)));
    public static final Item MERCH_DOLL_MORTIS_ITEM = item("merch_doll_mortis", new net.minecraft.world.item.BlockItem(
            MERCH_DOLL_MORTIS, new Item.Properties().stacksTo(64)));
    public static final Item MERCH_DOLL_TIMORIS_ITEM = item("merch_doll_timoris", new net.minecraft.world.item.BlockItem(
            MERCH_DOLL_TIMORIS, new Item.Properties().stacksTo(64)));
    public static final Item MERCH_DOLL_AMORIS_ITEM = item("merch_doll_amoris", new net.minecraft.world.item.BlockItem(
            MERCH_DOLL_AMORIS, new Item.Properties().stacksTo(64)));
    public static final MerchStallBlock MERCH_STALL = Registry.register(BuiltInRegistries.BLOCK, id("merch_stall"),
            new MerchStallBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL).noOcclusion().strength(1.5F)));
    public static final BlockEntityType<MerchStallBlockEntity> MERCH_STALL_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("merch_stall"),
            FabricBlockEntityTypeBuilder.create(MerchStallBlockEntity::new, MERCH_STALL).build());
    public static final Item MERCH_STALL_ITEM = item("merch_stall",new net.minecraft.world.item.BlockItem(
            MERCH_STALL,new Item.Properties().stacksTo(16)));
    // Four real edible snacks. Keep the old protein_bar registry id for existing saves.
    public static final Item PROTEIN_BAR = item("protein_bar", new Item(new Item.Properties().stacksTo(16)
            .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(5).saturationModifier(.65F).build())));
    public static final Item DONUT = item("donut", new Item(new Item.Properties().stacksTo(32)
            .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(5).saturationModifier(.35F).build())));
    public static final Item COFFEE = item("coffee", new com.miseru.avemujica.item.CoffeeItem(new Item.Properties().stacksTo(16)
            .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(.1F).alwaysEdible().build())));
    public static final Item CUCUMBER = item("cucumber", new Item(new Item.Properties().stacksTo(64)
            .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationModifier(.35F).build())));
    public static final EnumMap<MemberRole, SummonMemberItem> SUMMONERS = new EnumMap<>(MemberRole.class);
    public static final CreativeModeTab TAB;
    static {
        for (MemberRole role : MemberRole.values()) SUMMONERS.put(role,
                (SummonMemberItem)item(role.id + "_token", new SummonMemberItem(role, new Item.Properties().stacksTo(16))));
        TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,id("ave_mujica"),
                FabricItemGroup.builder().title(Component.translatable("itemGroup.avemujica"))
                .icon(() -> SUMMONERS.get(MemberRole.OBLIVIONIS).getDefaultInstance())
                .displayItems((ctx,out) -> {
                    // 1 · Members (all five portraits, fixed band order)
                    for (MemberRole role : MemberRole.values()) out.accept(SUMMONERS.get(role));
                    // 2 · Everyday core equipment
                    out.accept(BLUE_OCTOPUS);
                    out.accept(TOUR_STAGE_ITEM);
                    out.accept(TOUR_STAGE_LEVEL2_ITEM);
                    // 3 · Food and character interaction
                    out.accept(COFFEE);
                    out.accept(CUCUMBER);
                    out.accept(PROTEIN_BAR);
                    out.accept(DONUT);
                    // 4 · Tour keepsakes (legacy merch kiosk and lightstick are hidden)
                    out.accept(MERCH_POSTER_ITEM);
                    out.accept(MERCH_RECORD_ITEM);
                    out.accept(MERCH_DOLL_OBLIVIONIS_ITEM);
                    out.accept(MERCH_DOLL_DOLORIS_ITEM);
                    out.accept(MERCH_DOLL_MORTIS_ITEM);
                    out.accept(MERCH_DOLL_TIMORIS_ITEM);
                    out.accept(MERCH_DOLL_AMORIS_ITEM);
                    // Camera & pose debugger remain registered only for old saves/admins.
                    // Intentionally absent: NPC instruments, stand-alone keyboard,
                    // drum kit and stool, microphone, smartphone, legacy level/debug
                    // tool, retired concert orb and engine-only NOTE_ICON.
                    // Registries and old inventories remain compatible.
                }).build());
    }
    public static Item instrument(MemberRole role) { return switch (role) {
        case DOLORIS -> GUITAR; // Uika remains singer + rhythm guitarist
        case MORTIS -> RHYTHM_GUITAR;
        case TIMORIS -> BASS;
        case AMORIS -> DRUMSTICK;
        case OBLIVIONIS -> KEYBOARD;
    }; }
    public static void register() {} // forces class initialization
}

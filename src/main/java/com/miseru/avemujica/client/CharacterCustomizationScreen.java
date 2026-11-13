package com.miseru.avemujica.client;

import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.entity.MemberInstrument;
import com.miseru.avemujica.network.MemberInstrumentChoicePayload;
import com.miseru.avemujica.network.MemberInstrumentStatePayload;
import net.minecraft.client.gui.components.Tooltip;
import com.miseru.avemujica.network.CharacterCustomizationStatePayload;
import com.miseru.avemujica.network.CharacterDailyActionPayload;
import com.miseru.avemujica.network.CharacterSummonChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Unified owner-side character customization: client-only skins/dialogue + persistent server AI toggle. */
public final class CharacterCustomizationScreen extends ScaledMujicaScreen {
    private static final String PACK_ID = "file/AveMujica_Custom_Assets";
    private static final int W=524, H=374;
    private static final MemberRole[] ORDER = {
            MemberRole.OBLIVIONIS, MemberRole.DOLORIS, MemberRole.MORTIS,
            MemberRole.TIMORIS, MemberRole.AMORIS
    };
    private static final String[] LABELS = {"祥子", "初华", "睦", "海铃", "喵梦"};
    private final Path gameDir = FabricLoader.getInstance().getGameDir();
    private final boolean[] daily = new boolean[MemberRole.values().length];
    private final boolean[] summonEnabled = new boolean[MemberRole.values().length];
    private final int[] slotIndices = new int[ORDER.length];
    private final Button[] roleButtons = new Button[ORDER.length];
    private Button dailyButton, summonButton, instrumentButton, resetInstrument, uikaInstrument;
    private MemberInstrumentStatePayload instrumentState;
    private EditBox skinInput, voiceInput;
    private String skinPath="", voicePath="";
    private int selectedRole=0, left, top;
    private String status = "皮肤、语音仅替换本机资源；日常动作设置在服务器保存";
    private String cachedVoiceKey="", cachedVoiceText="";

    public CharacterCustomizationScreen(CharacterCustomizationStatePayload packet) {
        super(Component.literal("Ave Mujica · 角色自定义"));
        update(packet);
    }

    public void update(CharacterCustomizationStatePayload packet) {
        Arrays.fill(daily, true);
        Arrays.fill(summonEnabled, true);
        for (int i=0; i<daily.length && i<packet.enabled().length; i++) daily[i]=packet.enabled()[i];
        for (int i=0; i<summonEnabled.length && i<packet.summonEnabled().length; i++)
            summonEnabled[i]=packet.summonEnabled()[i];
        refreshLabels();
    }

    public void updateInstruments(MemberInstrumentStatePayload packet) {
        instrumentState = packet;
        refreshLabels();
    }

    private MemberInstrument selectedInstrument() {
        MemberRole role = currentRole();
        return instrumentState == null ? MemberInstrument.defaultFor(role)
                : MemberInstrument.at(instrumentState.instruments()[role.ordinal()], role);
    }

    private void chooseInstrument(int choice) {
        if (instrumentState == null || !ClientPlayNetworking.canSend(MemberInstrumentChoicePayload.TYPE)) return;
        ClientPlayNetworking.send(new MemberInstrumentChoicePayload(currentRole().ordinal(), choice));
    }

    private MemberRole currentRole() { return ORDER[selectedRole]; }
    private List<String> slots() { return CustomAssetImporter.voiceSlots(currentRole().id); }
    private String currentSlot() {
        List<String> list=slots();
        return list.isEmpty()?"":list.get(Math.floorMod(slotIndices[selectedRole],list.size()));
    }

    @Override protected void init() {
        layout(W,H);
        left=uiLeft(); top=uiTop();
        for(int i=0;i<ORDER.length;i++) {
            final int index=i;
            roleButtons[i]=addRenderableWidget(Button.builder(Component.literal(LABELS[i]), b->switchRole(index))
                    .bounds(left+12+i*101,top+47,96,23).build());
        }
        addRenderableWidget(Button.builder(Component.literal("关闭"), b->onClose())
                .bounds(left+W-66,top+8,53,21).build());
        dailyButton=addRenderableWidget(Button.builder(Component.literal("日常动作"),b->toggleDaily())
                .bounds(left+12,top+83,151,23).build());
        summonButton=addRenderableWidget(Button.builder(Component.literal("参与召集"),b->toggleSummon())
                .bounds(left+172,top+83,186,23).build());
        instrumentButton = addRenderableWidget(Button.builder(Component.empty(), b -> chooseInstrument(
                (selectedInstrument().ordinal() + 1) % MemberInstrument.values().length))
                .bounds(left+12,top+114,178,22).build());
        instrumentButton.setTooltip(Tooltip.create(Component.literal("点击切换实际演奏乐器，动作和音色随之改变")));
        resetInstrument = addRenderableWidget(Button.builder(Component.literal("恢复默认乐器"), b ->
                chooseInstrument(MemberInstrument.defaultFor(currentRole()).ordinal()))
                .bounds(left+198,top+114,112,22).build());
        uikaInstrument = addRenderableWidget(Button.builder(Component.empty(), b -> chooseInstrument(
                instrumentState.uikaEnabled() ? MemberInstrumentChoicePayload.DISABLE_UIKA
                        : MemberInstrumentChoicePayload.ENABLE_UIKA))
                .bounds(left+318,top+114,194,22).build());
        uikaInstrument.setTooltip(Tooltip.create(Component.literal("关闭后只唱歌，保留原来的选琴和人声设置")));
        // Paths can be pasted as-is; file names do not need to match character IDs.
        skinInput=new EditBox(font,left+12,top+167,500,20,Component.literal("自选 PNG 路径"));
        skinInput.setMaxLength(1024);
        skinInput.setHint(Component.literal("选择文件或粘贴任意名称的 64×64 PNG 路径"));
        skinInput.setValue(skinPath);
        skinInput.setResponder(value->skinPath=value);
        addRenderableWidget(skinInput);
        addRenderableWidget(Button.builder(Component.literal("导入皮肤"),b->importSkin())
                .bounds(left+12,top+194,102,22).build());
        addRenderableWidget(Button.builder(Component.literal("恢复原皮肤"),b->resetSkin())
                .bounds(left+120,top+194,102,22).build());
        addRenderableWidget(Button.builder(Component.literal("选择 PNG 文件"),b->openPicker(".png"))
                .bounds(left+228,top+194,135,22).build());
        addRenderableWidget(Button.builder(Component.literal("打开素材文件夹"),b->openFolder())
                .bounds(left+369,top+194,143,22).build());
        addRenderableWidget(Button.builder(Component.literal("◀ 上一条"),b->shiftSlot(-1))
                .bounds(left+12,top+243,94,23).build());
        addRenderableWidget(Button.builder(Component.literal("下一条 ▶"),b->shiftSlot(1))
                .bounds(left+113,top+243,94,23).build());
        addRenderableWidget(Button.builder(Component.literal("上一页 -5"),b->shiftSlot(-5))
                .bounds(left+214,top+243,102,23).build());
        addRenderableWidget(Button.builder(Component.literal("下一页 +5"),b->shiftSlot(5))
                .bounds(left+323,top+243,102,23).build());
        voiceInput=new EditBox(font,left+12,top+275,500,20,Component.literal("自选 OGG 路径"));
        voiceInput.setMaxLength(1024);
        voiceInput.setHint(Component.literal("选择文件或粘贴任意名称的 Ogg Vorbis 路径"));
        voiceInput.setValue(voicePath);
        voiceInput.setResponder(value->voicePath=value);
        addRenderableWidget(voiceInput);
        addRenderableWidget(Button.builder(Component.literal("替换此条语音"),b->importVoice())
                .bounds(left+12,top+303,126,22).build());
        addRenderableWidget(Button.builder(Component.literal("恢复此条语音"),b->resetVoice())
                .bounds(left+144,top+303,126,22).build());
        addRenderableWidget(Button.builder(Component.literal("选择 OGG 文件"),b->openPicker(".ogg"))
                .bounds(left+276,top+303,117,22).build());
        addRenderableWidget(Button.builder(Component.literal("打开文件夹"),b->openFolder())
                .bounds(left+399,top+303,113,22).build());
        refreshLabels();
    }

    private void switchRole(int index) {
        selectedRole=index;
        skinPath=""; voicePath="";
        if(skinInput!=null) skinInput.setValue("");
        if(voiceInput!=null) voiceInput.setValue("");
        status="已切换至"+LABELS[index]+"；换肤换语音保存在本机，召集和日常动作由服务器保存";
        refreshLabels();
    }
    private void refreshLabels() {
        boolean ready = instrumentState != null;
        if (instrumentButton != null) {
            instrumentButton.setMessage(Component.literal("乐器：" + selectedInstrument().label + " ▶"));
            instrumentButton.active = ready;
        }
        if (resetInstrument != null)
            resetInstrument.active = ready && selectedInstrument() != MemberInstrument.defaultFor(currentRole());
        if (uikaInstrument != null) {
            uikaInstrument.visible = currentRole() == MemberRole.DOLORIS;
            uikaInstrument.active = ready && uikaInstrument.visible;
            uikaInstrument.setMessage(Component.literal(ready && !instrumentState.uikaEnabled()
                    ? "初华使用乐器：关闭（仅人声）" : "初华使用乐器：开启"));
        }
        for(int i=0;i<ORDER.length;i++) if(roleButtons[i]!=null)
            roleButtons[i].setMessage(Component.literal((i==selectedRole?"▶ ":"")+LABELS[i]));
        if(dailyButton!=null) dailyButton.setMessage(Component.literal("日常动作："+
                (daily[currentRole().ordinal()]?"开启":"关闭")));
        if(summonButton!=null) summonButton.setMessage(Component.literal("参与召集："+
                (summonEnabled[currentRole().ordinal()]?"是":"否（已隐藏）")));
    }
    private void shiftSlot(int amount) {
        int size=slots().size();
        if(size==0) return;
        slotIndices[selectedRole]=Math.floorMod(slotIndices[selectedRole]+amount,size);
    }
    private void toggleDaily() {
        if(!ClientPlayNetworking.canSend(CharacterDailyActionPayload.TYPE)) {
            status="服务器尚不支持角色自定义设置"; return;
        }
        int idx=currentRole().ordinal();
        ClientPlayNetworking.send(new CharacterDailyActionPayload(idx,!daily[idx]));
        status="正在保存"+LABELS[selectedRole]+"的日常动作开关…";
    }
    private void toggleSummon() {
        if(!ClientPlayNetworking.canSend(CharacterSummonChoicePayload.TYPE)) {
            status="服务器尚不支持成员召集开关"; return;
        }
        int idx=currentRole().ordinal();
        ClientPlayNetworking.send(new CharacterSummonChoicePayload(idx,!summonEnabled[idx]));
        status=summonEnabled[idx]
                ? "正在隐藏"+LABELS[selectedRole]+"；立即从世界撤离，保存角色养成"
                : "正在允许"+LABELS[selectedRole]+"参与下一次召集…";
    }
    private Path selectedFile(String source) {
        String value=source.trim();
        if(value.startsWith("\"") && value.endsWith("\"") && value.length()>1)
            value=value.substring(1,value.length()-1);
        if(value.isBlank()) throw new IllegalArgumentException("请先在文件夹中选择文件或填写路径");
        return Path.of(value);
    }
    private void importSkin() {
        try {
            CustomAssetImporter.selectCharacterSkin(gameDir,currentRole().id,selectedFile(skinPath));
            reloadPack();
            status="已将皮肤映射到"+LABELS[selectedRole]+"；无需重命名 PNG";
        } catch(Exception e) {status="皮肤导入失败："+e.getMessage();}
    }
    private void resetSkin() {
        try {
            CustomAssetImporter.resetCharacterSkin(gameDir,currentRole().id);
            reloadPack(); status="已恢复"+LABELS[selectedRole]+"的默认皮肤";
        } catch(Exception e) {status="恢复皮肤失败："+e.getMessage();}
    }
    private void importVoice() {
        String slot=currentSlot();
        if(slot.isEmpty()) {status="当前角色没有语音槽位";return;}
        try {
            CustomAssetImporter.selectCharacterVoice(gameDir,currentRole().id,slot,selectedFile(voicePath));
            reloadPack();status="已替换"+LABELS[selectedRole]+"的 "+slot+" 对话语音";
        } catch(Exception e) {status="语音导入失败："+e.getMessage();}
    }
    private void resetVoice() {
        String slot=currentSlot();
        if(slot.isEmpty()) return;
        try {
            CustomAssetImporter.resetCharacterVoice(gameDir,currentRole().id,slot);
            reloadPack();status="已恢复"+LABELS[selectedRole]+"的 "+slot+" 默认语音";
        } catch(Exception e) {status="恢复语音失败："+e.getMessage();}
    }
    private void reloadPack() throws Exception {
        CustomAssetImporter.Result result=CustomAssetImporter.install(gameDir);
        PackRepository repo=minecraft.getResourcePackRepository();
        repo.reload();
        if(!repo.isAvailable(PACK_ID)) throw new IllegalStateException("未找到生成的资源包，请在设置中启用");
        repo.addPack(PACK_ID);
        minecraft.options.updateResourcePacks(repo);
        minecraft.reloadResourcePacks();
        if(!result.warnings().isEmpty()) status="素材已导入；其他文件警告："+result.warnings().get(0);
    }
    private void openFolder() {
        try {
            Path dir=CustomAssetImporter.candidateDir(gameDir);
            Files.createDirectories(dir);
            Util.getPlatform().openFile(dir.toFile());
            status="把任意命名的 PNG / OGG 放进文件夹，再点“选择文件”";
        } catch(Exception e) {status="无法打开目录："+e.getMessage();}
    }
    private void openPicker(String suffix) {
        minecraft.setScreen(new CharacterCandidateScreen(this,suffix));
    }
    public void chooseCandidate(String suffix,Path path) {
        if(suffix.equals(".png")) {skinPath=path.toString();if(skinInput!=null)skinInput.setValue(skinPath);}
        else {voicePath=path.toString();if(voiceInput!=null)voiceInput.setValue(voicePath);}
        status="已选择："+path.getFileName()+"；请点击对应的导入按钮";
    }

    @Override public void render(GuiGraphics g,int mx,int my,float partialTick) {
        renderBackground(g,mx,my,partialTick);
        beginUi(g);
        OctopusSkin.drawPanelFrame(g, left, top, W, H, 38);
        g.drawString(font,"蓝色小章鱼 · 角色自定义",left+15,top+12,OctopusTheme.current().text,false);
        g.drawString(font,"选择成员",left+13,top+39,OctopusTheme.current().muted,false);
        g.drawString(font,"关闭后即撤离",left+374,top+91,OctopusTheme.current().muted,false);
        g.fill(left+10,top+145,left+W-10,top+146,OctopusSkin.borderSoft());
        g.drawString(font,"皮肤 · 64×64 PNG  (本地资源包)",left+13,top+153,OctopusTheme.current().muted,false);
        g.fill(left+10,top+228,left+W-10,top+229,OctopusSkin.borderSoft());
        List<String> list=slots();
        String slot=currentSlot();
        String line="对话语音 · "+LABELS[selectedRole]+" "+slot+" ["+
                (list.isEmpty()?0:Math.floorMod(slotIndices[selectedRole],list.size())+1)+"/"+list.size()+"]";
        String key=currentRole().id+":"+slot;
        if(!key.equals(cachedVoiceKey)) {
            cachedVoiceKey=key;
            cachedVoiceText=CustomAssetImporter.voiceDescription(currentRole().id,slot);
        }
        if(!cachedVoiceText.isEmpty()) line+="  "+cachedVoiceText;
        g.drawString(font,font.plainSubstrByWidth(line,505),left+13,top+233,OctopusTheme.current().muted,false);
        g.drawString(font,font.plainSubstrByWidth(status,500),left+12,top+343,OctopusTheme.current().secondary,false);
        g.drawString(font,"素材不上传服务器；联机其他玩家需自行安装同一资源包",left+12,top+358,OctopusTheme.current().muted,false);
        super.render(g,logicalMouse(mx),logicalMouse(my),partialTick);
        OctopusSkin.drawCenteredLogo(g, left, top, W, H);
        endUi(g);
    }
    @Override protected void renderUiBackdrop(GuiGraphics g,int mx,int my,float tick) {
        OctopusSkin.drawBackdrop(g, width, height);
    }
    @Override public boolean isPauseScreen() {return false;}
}

package com.miseru.avemujica.client;

import com.miseru.avemujica.career.InstrumentPalette;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.network.InstrumentColorChoosePayload;
import com.miseru.avemujica.network.InstrumentColorStatePayload;
import com.miseru.avemujica.entity.MemberInstrument;
import com.miseru.avemujica.network.MemberInstrumentStatePayload;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Five independent paint selectors, no external dependency or asset file picker. */
public final class InstrumentColorScreen extends ScaledMujicaScreen {
    private static final int W = 507, H = 266;
    private static final MemberRole[] ORDER = {
            MemberRole.OBLIVIONIS, MemberRole.DOLORIS, MemberRole.MORTIS,
            MemberRole.TIMORIS, MemberRole.AMORIS
    };
    private static final String[] TITLES = {"祥子", "初华", "睦", "海铃", "喵梦"};
    private final int[] selected = new int[MemberRole.values().length];
    private final MemberInstrument[] instruments = new MemberInstrument[ORDER.length];
    private final ItemStack[][] previews = new ItemStack[ORDER.length][];
    private final Button[][] buttons = new Button[ORDER.length][InstrumentPalette.COUNT];
    private int left, top;

    public InstrumentColorScreen(InstrumentColorStatePayload packet) {
        super(Component.literal("Ave Mujica · 乐器换色"));
        for (int row = 0; row < ORDER.length; row++) setPreview(row, MemberInstrument.defaultFor(ORDER[row]));
        update(packet);
    }
    public void update(InstrumentColorStatePayload packet) {
        for (int i=0; i<selected.length && i<packet.colors().length; i++)
            selected[i] = InstrumentPalette.safe(packet.colors()[i]);
        refresh();
    }

    public void updateInstruments(MemberInstrumentStatePayload packet) {
        for (int row = 0; row < ORDER.length; row++) {
            MemberRole role = ORDER[row];
            setPreview(row, MemberInstrument.at(packet.instruments()[role.ordinal()], role));
        }
    }

    private void setPreview(int row, MemberInstrument type) {
        if (instruments[row] == type) return;
        instruments[row] = type;
        previews[row] = InstrumentColorItems.variants(type.model());
    }

    @Override protected void init() {
        layout(W, H);
        left = uiLeft(); top = uiTop();
        for (int row = 0; row < ORDER.length; row++) {
            final int role = ORDER[row].ordinal();
            for (int finish=0; finish<InstrumentPalette.COUNT; finish++) {
                final int color=finish;
                Button b = addRenderableWidget(Button.builder(
                        Component.literal(color == 0 ? "原" : "◆")
                                .withStyle(style -> style.withColor(InstrumentPalette.RGB[color])),
                        ignored -> choose(role, color))
                        .bounds(left + 131 + finish*35, top + 57 + row*35, 31, 23).build());
                b.setTooltip(Tooltip.create(Component.literal(InstrumentPalette.NAMES[color])));
                buttons[row][finish] = b;
            }
        }
        addRenderableWidget(Button.builder(Component.literal("完成"), ignored -> onClose())
                .bounds(left + W - 91, top + H - 36, 77, 22).build());
        refresh();
    }
    private void choose(int role, int color) {
        if (!ClientPlayNetworking.canSend(InstrumentColorChoosePayload.TYPE)) return;
        ClientPlayNetworking.send(new InstrumentColorChoosePayload(role, color));
        // Server replies with the authoritative snapshot; no client-side inventory state changes.
    }
    private void refresh() {
        for (int row=0; row<ORDER.length; row++) {
            for (int col=0; col<InstrumentPalette.COUNT; col++) {
                Button b = buttons[row][col];
                final int rgb = InstrumentPalette.RGB[col];
                if (b != null) b.setMessage(Component.literal((selected[ORDER[row].ordinal()] == col ? "✓" : col==0 ? "原" : "◆"))
                        .withStyle(style -> style.withColor(rgb)));
            }
        }
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        beginUi(g);
        OctopusSkin.drawPanelFrame(g, left, top, W, H, 38);
        g.drawString(font, "Ave Mujica · 乐器外观换色", left+15, top+12, OctopusTheme.current().text, false);
        for (int i=0; i<ORDER.length; i++) {
            int y = top + 57 + i * 35;
            g.fill(left + 9, y - 4, left + W - 10, y + 27, (i%2==0)?OctopusSkin.rowA():OctopusSkin.rowB());
            g.drawString(font, TITLES[i] + " · " + instruments[i].label, left+15, y+8, OctopusTheme.current().text, false);
            g.renderItem(InstrumentColorItems.at(previews[i], selected[ORDER[i].ordinal()]), left + 109, y + 3);
        }
        g.drawString(font, "仅调整乐器外观 · 原色不变 · 已保存到角色存档", left+14, top+230, OctopusTheme.current().muted, false);
        super.render(g, logicalMouse(mx), logicalMouse(my), partialTick);
        OctopusSkin.drawCenteredLogo(g, left, top, W, H);
        endUi(g);
    }
    @Override protected void renderUiBackdrop(GuiGraphics g, int mx, int my, float tick) {
        OctopusSkin.drawBackdrop(g, width, height);
    }
    @Override public boolean isPauseScreen() { return false; }
}

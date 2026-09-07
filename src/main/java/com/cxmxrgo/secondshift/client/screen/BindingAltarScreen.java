package com.cxmxrgo.secondshift.client.screen;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import com.cxmxrgo.secondshift.network.SelectTradesPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/**
 * Binding Altar screen (D-05 / D-06 / D-07 / GUI-01 / GUI-03) — the client-only render half of the
 * HARD GATE, now the full real UI per 05-UI-SPEC.md: a 200x222 canvas with an editable, pre-filled
 * name field, a scrollable click-to-toggle candidate row list ({@link TradeCandidateList}), and a
 * Confirm button that sends the player's actual choices.
 *
 * <p><b>Client-class isolation (Dist.CLIENT rule, mirrors {@code SoulAltarRenderer}):</b> this
 * class lives ONLY in {@code client/screen/} and must never be referenced from common code
 * ({@code menu/}, {@code content/}). It is constructed only from
 * {@code ClientModBusEvents.onRegisterScreens} via {@code RegisterMenuScreensEvent}.
 *
 * <p><b>Plan 05-06:</b> replaces Plan 05-05's interim placeholder Confirm logic (first up-to-2
 * candidates, empty name) with the real name-field + click-to-toggle candidate-row selection this
 * class now owns.
 */
public class BindingAltarScreen extends AbstractContainerScreen<BindingAltarMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SecondShift.MODID, "textures/gui/binding_altar.png");

    private static final int COLOR_EMPTY_STATE = 0xFFFF5555;
    private static final int COLOR_MUTED = 0xFFA0A0A0;
    private static final int COLOR_DISPLAY_GOLD = 0xFFFFD700;

    private EditBox nameBox;
    private TradeCandidateList candidateList;

    public BindingAltarScreen(BindingAltarMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 200;
        this.imageHeight = 222;
        // Bug A fix (Phase 5 checkpoint): the player-inventory grid is shifted down by
        // BindingAltarMenu.INVENTORY_Y_SHIFT (56px) so it sits below the candidate list (local y
        // 40-100) and Confirm button (local y 106-126) instead of overlapping them. The label sits
        // 10px above the shifted row1 (vanilla's own label-to-slot convention), landing at 130.
        this.inventoryLabelY = 84 + BindingAltarMenu.INVENTORY_Y_SHIFT - 10;
    }

    @Override
    protected void init() {
        super.init();

        this.nameBox = new EditBox(this.font, leftPos + 8 + 34, topPos + 20, 200 - 8 - 34 - 8, 14,
                Component.translatable("gui.secondshift.binding_altar.name_label"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(this.getMenu().getDefaultName());
        this.addRenderableWidget(this.nameBox);

        // Bug A fix (Phase 5 checkpoint): list shrunk to local y 40-100 (60px / 3 rows) and the
        // Confirm button moved to local y 106-126 so both sit above the shifted player-inventory
        // grid (row1 now at local y 84 + INVENTORY_Y_SHIFT = 140) with clearance for the
        // "Inventory" label at local y 130 — no more overlap between the trade UI and the vanilla
        // slot grid/hotbar.
        List<MerchantOffer> candidates = this.getMenu().getCandidateOffers();
        if (!candidates.isEmpty()) {
            this.candidateList = new TradeCandidateList(
                    this.minecraft, 200 - 16, 60, topPos + 40, 20,
                    candidates, this.getMenu().isAutoLocked(), index -> {});
            this.candidateList.setX(leftPos + 8);
            this.addRenderableWidget(this.candidateList);
        }

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.secondshift.binding_altar.confirm"),
                        btn -> {
                            int[] indices = this.candidateList == null
                                    ? new int[0]
                                    : this.candidateList.getSelectedIndices().stream().mapToInt(Integer::intValue).toArray();
                            String name = this.nameBox.getValue();
                            PacketDistributor.sendToServer(new SelectTradesPayload(indices, name));
                        })
                .bounds(leftPos + 8, topPos + 106, 184, 20)
                .build());
    }

    /**
     * Fixes the "pressing E closes the screen while typing a name" bug: {@code
     * AbstractContainerScreen#keyPressed} closes the container whenever the raw keypress matches
     * the {@code keyInventory} keybind (E by default) and no focused widget already consumed the
     * event. A vanilla {@link EditBox} does NOT consume plain alphanumeric keys in {@code
     * keyPressed} (those go through the separate {@code charTyped} callback) — only special keys
     * (backspace, arrows, ctrl+combos) — so typing a bare "e" while the name field is focused fell
     * through to the container-close check. {@link EditBox#canConsumeInput()} is vanilla's own
     * purpose-built escape hatch for exactly this case (used by vanilla's own text-entry screens):
     * it reports true whenever the box is active/focused/visible, regardless of which specific key
     * was pressed, so we can unconditionally swallow the keypress while the name field has focus.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameBox != null && this.nameBox.isFocused() && this.nameBox.canConsumeInput()) {
            // Let the box handle special keys itself (backspace, arrows, ctrl+combos); regardless
            // of its own return value, we ALWAYS report the key as consumed here so the
            // container-close keybind check in super.keyPressed never runs while typing.
            this.nameBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);

        guiGraphics.drawString(this.font,
                Component.translatable("gui.secondshift.binding_altar.name_label"), 8, 24, 0xFF404040, false);

        Optional<VillagerProfession> profession = this.getMenu().getProfession();
        if (profession.isPresent()) {
            Component professionName = Component.translatable(
                    "entity.minecraft.villager." + BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession.get()).getPath());
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(8.0f, 3.0f, 0.0f);
            guiGraphics.pose().scale(1.5f, 1.5f, 1.5f);
            guiGraphics.drawString(this.font, professionName.copy().withStyle(net.minecraft.network.chat.Style.EMPTY.withBold(true)),
                    0, 0, COLOR_DISPLAY_GOLD, false);
            guiGraphics.pose().popPose();
        }

        if (this.candidateList == null) {
            guiGraphics.drawString(this.font, "Nothing to Offer", 8, 44, COLOR_EMPTY_STATE, false);
            List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(
                    Component.translatable("message.secondshift.altar.empty_pool"), 184);
            int y = 56;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                guiGraphics.drawString(this.font, line, 8, y, COLOR_EMPTY_STATE, false);
                y += 10;
            }
        }

        // GUI-03: happiness has nothing real to show until Phase 9 — static muted placeholder only.
        // Round-3 checkpoint fix: previously shared the header row with the profession heading at a
        // fixed x=120 — a long/translated profession name (e.g. pt_br "Bibliotecário", scaled 1.5x)
        // visually ran into it. Moved to its own dedicated row below both the heading and the name
        // field, in the small fixed gap between the name box's bottom (local y=34) and the
        // candidate-list recess's top (local y=40) — deliberately avoids measuring the heading's
        // rendered width (a static Phase-9 placeholder isn't worth that complexity).
        guiGraphics.drawString(this.font, "Happiness: N/A", 100, 34, COLOR_MUTED, false);
    }
}

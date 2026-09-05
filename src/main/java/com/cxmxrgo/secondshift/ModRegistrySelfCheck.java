package com.cxmxrgo.secondshift;

import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.cxmxrgo.secondshift.registry.ModBlockEntities;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModCreativeTab;
import com.cxmxrgo.secondshift.registry.ModItems;
import com.cxmxrgo.secondshift.registry.ModMenus;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Startup guardrails (D-08 / D-09 / D-10).
 *
 * <p><b>1. Unbound-registry check.</b> Streams every mod {@code DeferredRegister}'s
 * entries, keeps the ones that never bound, and hard-aborts loading with a named list if
 * any remain. This turns the prior draft's cryptic client-init {@code DeferredHolder#value()}
 * NPE into a readable "Unbound registry entries: [secondshift:...]" failure. Covers all
 * four Phase-2 registers (items, blocks, block-entity types, creative tab).
 *
 * <p><b>2. descriptionId / lang-key resolution check (Phase 2, RESEARCH Open Question 3
 * — resolved YES).</b> Every registered item/block's {@code descriptionId}, plus the
 * creative-tab title and the three advancement title/description pairs, must resolve to a
 * key in the mod's own {@code assets/secondshift/lang/en_us.json}. Deleting a key
 * hard-aborts startup with the missing key named — this prevents the "raw
 * {@code item.secondshift.*} key shown in the UI" failure (POL-03).
 *
 * <p>This second check reads the mod's bundled {@code en_us.json} <b>directly from the
 * classpath</b> rather than going through {@code net.minecraft.locale.Language}. Reason:
 * {@code Language.getInstance()} is not populated with mod translations at
 * {@code FMLLoadCompleteEvent} on the dedicated server (NeoForge's {@code LanguageHook}
 * only loads mod lang files once a {@code MinecraftServer} exists, which is after mod
 * loading completes). Reading the jar resource is deterministic, side-effect free, and
 * runs identically on the client and the dedicated server — <b>no {@code Dist.CLIENT}
 * gate needed</b>.
 *
 * <p>Both checks hard-throw <b>directly</b> from the {@code FMLLoadCompleteEvent} handler
 * (not via {@code event.enqueueWork(...)}): an exception raised inside {@code enqueueWork}
 * is caught by FML's {@code DeferredWorkQueue}, logged, and the mod is merely flagged
 * "broken" while the game limps on. Throwing straight from the mod-bus handler makes it a
 * fatal {@code ModLoadingException} — the hard abort D-08 requires.
 */
@EventBusSubscriber(modid = SecondShift.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModRegistrySelfCheck {

    private static final String LANG_RESOURCE = "/assets/secondshift/lang/en_us.json";

    /** Non-descriptionId lang keys that must also resolve (creative tab + advancement chain). */
    private static final List<String> EXTRA_LANG_KEYS = List.of(
            "itemGroup.secondshift.main",
            "advancement.secondshift.necromantic_apprentice.title",
            "advancement.secondshift.necromantic_apprentice.description",
            "advancement.secondshift.first_harvest.title",
            "advancement.secondshift.first_harvest.description",
            "advancement.secondshift.soul_mason.title",
            "advancement.secondshift.soul_mason.description",
            "container.secondshift.binding_altar",
            "gui.secondshift.binding_altar.confirm",
            "gui.secondshift.binding_altar.name_label",
            "gui.secondshift.binding_altar.trade_locked",
            "message.secondshift.altar.no_job_block",
            "message.secondshift.altar.not_a_workstation",
            "message.secondshift.altar.no_soul_block",
            "message.secondshift.altar.occupied",
            "message.secondshift.altar.closed.altar_gone",
            "message.secondshift.altar.closed.job_gone",
            "message.secondshift.altar.closed.too_far",
            "message.secondshift.altar.empty_pool");

    private ModRegistrySelfCheck() {}

    @SubscribeEvent
    static void onLoadComplete(FMLLoadCompleteEvent event) {
        // D-10: add every registry/Mod* DeferredRegister to this Stream.of(...) as later
        // phases introduce them, and mirror it in the SecondShift constructor.
        List<String> unbound = Stream.of(ModItems.ITEMS, ModBlocks.BLOCKS, ModBlockEntities.BLOCK_ENTITIES, ModCreativeTab.TABS, ModMenus.MENUS, ModAttachments.ATTACHMENT_TYPES)
                .flatMap(dr -> dr.getEntries().stream())
                .filter(holder -> !holder.isBound())
                .map(holder -> holder.getId().toString())
                .sorted()
                .toList();
        if (!unbound.isEmpty()) {
            throw new IllegalStateException("Unbound registry entries: " + unbound);
        }
    }

    @SubscribeEvent
    static void onLoadCompleteLangKeys(FMLLoadCompleteEvent event) {
        JsonObject lang = readLangFile();

        List<String> missing = new ArrayList<>();

        Stream.of(ModItems.ITEMS, ModBlocks.BLOCKS)
                .flatMap(dr -> dr.getEntries().stream())
                .map(holder -> {
                    Object value = holder.value();
                    if (value instanceof Item item) {
                        return item.getDescriptionId();
                    }
                    if (value instanceof Block block) {
                        return block.getDescriptionId();
                    }
                    return null;
                })
                .filter(key -> key != null && !lang.has(key))
                .forEach(missing::add);

        for (String key : EXTRA_LANG_KEYS) {
            if (!lang.has(key)) {
                missing.add(key);
            }
        }

        if (!missing.isEmpty()) {
            missing.sort(String::compareTo);
            throw new IllegalStateException("Unresolved lang keys: " + missing);
        }
    }

    private static JsonObject readLangFile() {
        try (InputStream in = ModRegistrySelfCheck.class.getResourceAsStream(LANG_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing mod language file on the classpath: " + LANG_RESOURCE);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read mod language file: " + LANG_RESOURCE, e);
        }
    }
}

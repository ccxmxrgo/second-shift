package com.cxmxrgo.secondshift;

import com.cxmxrgo.secondshift.registry.ModItems;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

import java.util.List;
import java.util.stream.Stream;

/**
 * Unbound-registry guardrail (D-08 / D-09 / D-10).
 *
 * <p>Streams every mod {@code DeferredRegister}'s entries, keeps the ones that never
 * bound, and hard-aborts loading with a named list if any remain. This turns the
 * prior draft's cryptic client-init {@code DeferredHolder#value()} NPE into a
 * readable "Unbound registry entries: [secondshift:...]" failure.
 *
 * <p>Hard-throws on <b>both</b> dev and the packaged jar (D-08). {@code FMLLoadCompleteEvent}
 * fires on the client, the dedicated server, and {@code runData} (PITFALLS §1), so all
 * three surfaces are covered.
 *
 * <p>Scope is {@code isBound()} only (D-09) — no descriptionId / lang-key resolution check
 * in Phase 1.
 */
@EventBusSubscriber(modid = SecondShift.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModRegistrySelfCheck {

    // Per PITFALLS §2, FML 4.0.43 ignores the `bus` attribute and derives the bus from
    // the event type (FMLLoadCompleteEvent implements IModBusEvent -> mod bus); the
    // Bus.MOD above is documentation-only, matching STACK.md §2 / ARCHITECTURE.md.
    private ModRegistrySelfCheck() {}

    // AutomaticEventSubscriber registers only @SubscribeEvent static methods (PITFALLS §2).
    //
    // The check throws DIRECTLY from the handler rather than from event.enqueueWork(...):
    // an exception raised inside enqueueWork is caught by FML's DeferredWorkQueue, logged,
    // and the mod is merely flagged "broken" while the client limps on to a degraded state.
    // Throwing straight from the mod-bus handler makes it a fatal ModLoadingException — the
    // hard abort D-08 requires. getEntries()/isBound()/getId() are safe off the main thread.
    @SubscribeEvent
    static void onLoadComplete(FMLLoadCompleteEvent event) {
        List<String> unbound = Stream.of(
                        // D-10: add every registry/Mod* DeferredRegister to this
                        // Stream.of(...) as later phases introduce them.
                        ModItems.ITEMS)
                .flatMap(dr -> dr.getEntries().stream())
                .filter(holder -> !holder.isBound())
                .map(holder -> holder.getId().toString())
                .sorted()
                .toList();
        if (!unbound.isEmpty()) {
            throw new IllegalStateException("Unbound registry entries: " + unbound);
        }
    }
}

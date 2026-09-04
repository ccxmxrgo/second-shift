package com.cxmxrgo.secondshift;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Second Shift entrypoint.
 *
 * <p>The constructor is the single wiring point for the whole mod: every
 * {@code DeferredRegister} is attached to the mod bus here, in one visible block
 * (added in plan 01-02 Task 2). Keeping that wiring in one place is what
 * structurally prevents the unbound-registry crash that killed the prior draft.
 */
@Mod(SecondShift.MODID)
public class SecondShift {

    public static final String MODID = "secondshift";

    private static final Logger LOGGER = LogUtils.getLogger();

    public SecondShift(IEventBus modBus, ModContainer container) {
        LOGGER.info("[SecondShift] loading {} on NeoForge", container.getModInfo().getVersion());
        // Registers, listeners, and the self-check are wired in Task 2.
    }
}

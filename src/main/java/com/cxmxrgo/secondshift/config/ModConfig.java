package com.cxmxrgo.secondshift.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Phase 8 (STOCK-02): the mod's one config value so far — the real-time interval (in ticks) an
 * employee's trades restock on, independent of vanilla's own POI/work-schedule/day-count-based
 * restock gating. Registered against {@code ModConfig.Type.COMMON} (not CLIENT/SERVER) from
 * {@code SecondShift}'s constructor since restock timing is server-authoritative logic that has
 * no per-world override need — a single common config file is the simplest correct choice.
 *
 * <p>Default (12000 ticks = 10 real minutes) deliberately shorter than vanilla's own 2-restocks-
 * per-day cadence (~24000-tick day) — the whole point of STOCK-01 is that this mod's restock
 * never feels tied to vanilla's slower, POI-gated rhythm.
 */
public final class ModConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<Integer> RESTOCK_INTERVAL_TICKS;
    public static final ModConfigSpec.ConfigValue<Integer> HAPPINESS_QUIT_THRESHOLD_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("employees");
        RESTOCK_INTERVAL_TICKS = builder
                .comment(
                        "How often (in game ticks, 20 per real second) a bound employee's trades",
                        "restock — independent of vanilla's day/night cycle, POI access, or work",
                        "schedule. Default 12000 (10 real minutes).")
                .defineInRange("restockIntervalTicks", 12000, 200, 480000, Integer.class);
        HAPPINESS_QUIT_THRESHOLD_TICKS = builder
                .comment(
                        "How many CONTINUOUS ticks an employee can stay Unhappy before it quits",
                        "(drops its Soul Block, reverts to an ordinary villager, frees its altar).",
                        "Default 12000 (10 real minutes).")
                .defineInRange("happinessQuitThresholdTicks", 12000, 200, 1000000, Integer.class);
        builder.pop();

        SPEC = builder.build();
    }

    private ModConfig() {}
}

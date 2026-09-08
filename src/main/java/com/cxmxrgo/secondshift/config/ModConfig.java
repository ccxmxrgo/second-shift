package com.cxmxrgo.secondshift.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's {@code ModConfig.Type.COMMON} config — server-authoritative tunables with no
 * per-world override need, so a single common file is the simplest correct choice for all of
 * them (restock/happiness timers, the Fragment yield, and the Phase 6 trait-immunity toggles
 * alike). Registered from {@code SecondShift}'s constructor; the Mods-menu Config button (POL-06)
 * is wired from {@code ClientModBusEvents} via NeoForge's own generic {@code ConfigurationScreen}
 * — no hand-rolled config screen needed.
 */
public final class ModConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<Integer> RESTOCK_INTERVAL_TICKS;
    public static final ModConfigSpec.ConfigValue<Integer> HAPPINESS_QUIT_THRESHOLD_TICKS;
    public static final ModConfigSpec.ConfigValue<Integer> HAPPINESS_UNHAPPY_MAX;
    public static final ModConfigSpec.ConfigValue<Integer> HAPPINESS_OK_MAX;

    public static final ModConfigSpec.ConfigValue<Integer> SOUL_FRAGMENT_DROP_COUNT;

    public static final ModConfigSpec.BooleanValue CONVERSION_IMMUNITY_ENABLED;
    public static final ModConfigSpec.BooleanValue BREEDING_LOCK_ENABLED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("employees");
        RESTOCK_INTERVAL_TICKS = builder
                .comment(
                        "How often (in game ticks, 20 per real second) a bound employee's trades",
                        "restock — independent of vanilla's day/night cycle, POI access, or work",
                        "schedule. Default 12000 (10 real minutes).")
                .defineInRange("restockIntervalTicks", 12000, 200, 480000, Integer.class);

        builder.push("happiness");
        HAPPINESS_QUIT_THRESHOLD_TICKS = builder
                .comment(
                        "How many CONTINUOUS ticks an employee can stay Unhappy before it quits",
                        "(drops its Soul Block, reverts to an ordinary villager, frees its altar).",
                        "Default 12000 (10 real minutes).")
                .defineInRange("happinessQuitThresholdTicks", 12000, 200, 1000000, Integer.class);
        HAPPINESS_UNHAPPY_MAX = builder
                .comment("The 0-100 happiness meter is Unhappy at or below this value. Default 33.")
                .defineInRange("happinessUnhappyMax", 33, 0, 98, Integer.class);
        HAPPINESS_OK_MAX = builder
                .comment(
                        "The meter is OK above happinessUnhappyMax and at or below this value;",
                        "Happy above it. Default 66. Must stay greater than happinessUnhappyMax",
                        "(not enforced by the config loader itself — a misconfigured pair simply",
                        "makes the OK band disappear, it will not crash).")
                .defineInRange("happinessOkMax", 66, 1, 99, Integer.class);
        builder.pop(); // happiness

        CONVERSION_IMMUNITY_ENABLED = builder
                .comment(
                        "EMP-03/04: whether employees are immune to zombie/witch conversion.",
                        "Disabling this is NOT recommended for normal play — it exists as an",
                        "honest opt-out, not a balance lever.")
                .define("conversionImmunityEnabled", true);
        BREEDING_LOCK_ENABLED = builder
                .comment(
                        "EMP-05: whether employees are held at a breeding-precondition-breaking age",
                        "(preventing them from breeding). Disabling this is NOT recommended for",
                        "normal play — it exists as an honest opt-out, not a balance lever.")
                .define("breedingLockEnabled", true);
        builder.pop(); // employees

        builder.push("economy");
        SOUL_FRAGMENT_DROP_COUNT = builder
                .comment("How many Soul Fragments a Harvester kill drops. Default 1 (ECON-02).")
                .defineInRange("soulFragmentDropCount", 1, 1, 64, Integer.class);
        builder.pop(); // economy

        SPEC = builder.build();
    }

    private ModConfig() {}
}

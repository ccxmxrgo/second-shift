package com.cxmxrgo.secondshift.event;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.config.ModConfig;
import com.cxmxrgo.secondshift.content.item.HarvesterItem;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.employee.FoodChecker;
import com.cxmxrgo.secondshift.employee.Happiness;
import com.cxmxrgo.secondshift.employee.QuartersChecker;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Employee character/lifecycle traits (Phase 6, 06-CONTEXT.md) — conversion immunity, breeding
 * prevention, the altar tether, Harvester-based voluntary recovery, and drop-recovery on every
 * other death. Every handler here gates on {@code hasData(EMPLOYEE)} first (the single most
 * important invariant in the mod — see CLAUDE.md), so wild villagers are never touched (EMP-09).
 *
 * <p>Self-registers via {@code @EventBusSubscriber} with no wiring line in {@code SecondShift},
 * matching {@link HarvesterEvents}'s established shape (final class, private constructor,
 * package-private static {@code @SubscribeEvent} methods).
 */
@EventBusSubscriber(modid = SecondShift.MODID)
public final class EmployeeEvents {

    /** Every this-many ticks, an employee's own tether/breeding-lock check runs, offset by the
     * entity's own {@code tickCount} so employees self-stagger rather than all checking on the
     * same world tick (06-CONTEXT.md D-04). */
    private static final int CHECK_INTERVAL_TICKS = 40;

    /** Below this age, the breeding-lock is re-asserted to {@link EmployeeManager#BREEDING_LOCK_AGE}
     * — 1200 ticks (60s) of headroom against a {@value #CHECK_INTERVAL_TICKS}-tick (2s) check
     * interval is an enormous margin (06-CONTEXT.md D-03). */
    private static final int BREEDING_LOCK_REASSERT_FLOOR = 1200;

    /** Soft tether radius (blocks): the employee walks itself home past this distance. */
    private static final double SOFT_TETHER_RADIUS = 24.0D;

    /** Hard tether radius (blocks): the employee is teleported home past this distance. */
    private static final double HARD_TETHER_RADIUS = 48.0D;

    private static final double WALK_HOME_SPEED = 0.6D;

    /** Radius (blocks) the PROG-03 promotion signal reaches real players. */
    private static final double PROMOTION_SIGNAL_RADIUS = 32.0D;

    /** Phase 9 (09-CONTEXT.md D-06): the happiness meter (quarters/food scan + price/streak
     * update) recomputes on a slower cadence than the 40-tick heartbeat — a flood fill and a
     * container scan are meaningfully more expensive than the other checks sharing that
     * heartbeat. A clean multiple of {@link #CHECK_INTERVAL_TICKS} so both gates stay aligned. */
    private static final int HAPPINESS_CHECK_INTERVAL_TICKS = 400;

    /** HAPP-03: how far the meter moves toward 0 or 100 per {@link #HAPPINESS_CHECK_INTERVAL_TICKS}
     * check — ~10 checks (~200 real seconds) to swing fully from one extreme to the other. */
    private static final int HAPPINESS_STEP = 10;

    /** Phase 7 (07-CONTEXT.md D-03): non-persisted "has this employee already been signaled for
     * THIS tier" tracker, keyed by employee UUID -> the vanilla level last signaled for. In-memory
     * by design (same acceptable-to-lose-on-restart pattern as {@link EmployeeFiring}'s pending
     * queue) — losing this on restart just means one redundant re-signal, never a silently
     * dropped promotion. */
    private static final Map<UUID, Integer> lastSignaledLevel = new ConcurrentHashMap<>();

    private EmployeeEvents() {}

    /** Hygiene hook — called by every other employee-removal path ({@link HarvesterEvents},
     * {@link EmployeeFiring}) so {@link #lastSignaledLevel} never retains an entry for a UUID that
     * will never tick again. Public (not package-private) so the {@code gametest} package can
     * exercise it directly. */
    public static void clearSignal(UUID employeeId) {
        lastSignaledLevel.remove(employeeId);
    }

    /**
     * EMP-03 / EMP-04: cancel every conversion (zombie AND witch, deliberately not filtered by
     * {@code getOutcome()} — an employee is immune to any conversion this hook backs, including
     * one a future NeoForge version or another mod routes through it). Verified against decompiled
     * sources: both vanilla call sites ({@code Zombie#killedEntity} for villager→zombie-villager,
     * {@code Villager#thunderHit} for villager→witch) are gated on {@code
     * EventHooks.canLivingConvert}, which this event backs exactly.
     *
     * <p>Note: a zombie killing an employee on Easy difficulty never attempts conversion at all —
     * vanilla simply kills the villager. That path is covered by {@link #onOtherDeath}'s drop
     * recovery, not by this immunity.
     *
     * <p>POL-06: gated on {@code ModConfig#CONVERSION_IMMUNITY_ENABLED} — an honest opt-out, not a
     * balance lever (see that config value's own comment).
     */
    @SubscribeEvent
    static void onConversionPre(LivingConversionEvent.Pre event) {
        if (ModConfig.CONVERSION_IMMUNITY_ENABLED.get() && event.getEntity().hasData(ModAttachments.EMPLOYEE.get())) {
            event.setCanceled(true);
        }
    }

    /**
     * EMP-05 (D-03) breeding lock, and EMP-07 (D-04) the altar tether — combined into one
     * per-employee periodic check since both are cheap, both gate on the same attachment, and
     * both only need to run every {@value #CHECK_INTERVAL_TICKS} ticks. Fires on both logical
     * sides (vanilla {@code EntityTickEvent.Post} contract) — the {@code ServerLevel} guard below
     * is what keeps this server-only.
     */
    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        if (!villager.hasData(ModAttachments.EMPLOYEE.get())) {
            return;
        }
        if (villager.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        // D-03: re-assert the breeding-precondition break before it decays too far. POL-06:
        // gated on ModConfig#BREEDING_LOCK_ENABLED — an honest opt-out, not a balance lever.
        if (ModConfig.BREEDING_LOCK_ENABLED.get() && villager.getAge() < BREEDING_LOCK_REASSERT_FLOOR) {
            villager.setAge(EmployeeManager.BREEDING_LOCK_AGE);
        }

        // Phase 7 PROG-02/03 (07-CONTEXT.md D-02/D-03): revert vanilla's auto-appended trades the
        // instant a level-up is observed, and signal the player once per newly-reached tier.
        EmployeeData data = villager.getData(ModAttachments.EMPLOYEE.get());
        int vanillaLevel = villager.getVillagerData().getLevel();
        if (vanillaLevel > data.tier()) {
            villager.setOffers(data.offers()); // idempotent — safe every check until the ritual resolves it
            if (!Integer.valueOf(vanillaLevel).equals(lastSignaledLevel.put(villager.getUUID(), vanillaLevel))) {
                signalPromotable(level, villager, vanillaLevel);
            }
        }

        // Phase 9 (HAPP-01..07, 09-CONTEXT.md D-06): the slower quarters/food/happiness recompute.
        // Runs (and may quit the employee — HAPP-06) BEFORE the restock check, since STOCK-04
        // needs this tick's freshly-recomputed happiness, and a just-quit employee must not also
        // restock or tether-check on the same tick.
        if (villager.tickCount % HAPPINESS_CHECK_INTERVAL_TICKS == 0
                && recomputeHappinessAndMaybeQuit(level, villager, data)) {
            return; // the employee just quit — hasData(EMPLOYEE) is now false, nothing left to do
        }

        // Phase 8 STOCK-01/02/03, Phase 9 STOCK-04: mod-owned real-time restock, independent of
        // vanilla's own POI/work-schedule/day-count restock gating (never calling
        // Villager#shouldRestock() at all). A single elapsed-vs-interval comparison, resetting the
        // timer to "now" on trigger, guarantees at most one restock per check regardless of how
        // long the employee was unloaded (success criterion 4 — no burst). Paused entirely while
        // Unhappy (STOCK-04) — reads the meter attachment directly rather than recomputing it,
        // since the expensive recompute above already keeps it fresh every
        // HAPPINESS_CHECK_INTERVAL_TICKS ticks. Gated on hasData(EMPLOYEE) at the top of this
        // method already (STOCK-03) — a wild villager never reaches this line.
        long lastRestock = villager.getData(ModAttachments.RESTOCK_TIMER.get());
        long now = level.getGameTime();
        boolean unhappy = Happiness.fromMeter(villager.getData(ModAttachments.HAPPINESS.get())) == Happiness.UNHAPPY;
        if (!unhappy && now - lastRestock >= ModConfig.RESTOCK_INTERVAL_TICKS.get()) {
            villager.restock();
            villager.setData(ModAttachments.RESTOCK_TIMER.get(), now);
        }

        // D-04: the altar tether. Suspended while the player is moving the employee deliberately.
        if (villager.isPassenger() || villager.isLeashed()) {
            return;
        }
        Optional<BlockPos> altarPos = data.altarPos();
        if (altarPos.isEmpty() || !level.getBlockState(altarPos.get()).is(ModBlocks.SOUL_ALTAR.get())) {
            return; // no recorded altar, or it's gone/replaced — behaves like a normal villager
        }
        BlockPos altar = altarPos.get();
        double distSqr = villager.distanceToSqr(altar.getX() + 0.5D, altar.getY(), altar.getZ() + 0.5D);
        if (distSqr > HARD_TETHER_RADIUS * HARD_TETHER_RADIUS) {
            BlockPos home = altar.above(2); // the same spawn position bind() used
            villager.moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D,
                    villager.getYRot(), villager.getXRot());
        } else if (distSqr > SOFT_TETHER_RADIUS * SOFT_TETHER_RADIUS) {
            villager.getNavigation().moveTo(altar.getX() + 0.5D, altar.getY(), altar.getZ() + 0.5D, WALK_HOME_SPEED);
        }
    }

    /**
     * Phase 9 (HAPP-01..07, 09-CONTEXT.md D-01..D-05): the slow happiness recompute — checks
     * quarters (HAPP-01) and food (HAPP-02), moves the 0-100 meter toward the appropriate extreme
     * (HAPP-03), applies the resulting price adjustment to every current offer (HAPP-04), tracks
     * the continuous-Unhappy streak, and quits the employee (HAPP-06) once that streak crosses the
     * configured threshold. Returns {@code true} if the employee just quit — the caller must stop
     * touching it as an employee immediately (its {@code EMPLOYEE} attachment is gone).
     */
    private static boolean recomputeHappinessAndMaybeQuit(ServerLevel level, Villager villager, EmployeeData data) {
        boolean conditionsMet = data.altarPos().isPresent()
                && QuartersChecker.hasValidQuarters(level, data.altarPos().get().above(2))
                && FoodChecker.tryConsumeFood(level, data.altarPos().get());

        int meter = villager.getData(ModAttachments.HAPPINESS.get());
        meter = conditionsMet
                ? Math.min(100, meter + HAPPINESS_STEP)
                : Math.max(0, meter - HAPPINESS_STEP);
        villager.setData(ModAttachments.HAPPINESS.get(), meter);

        Happiness tier = Happiness.fromMeter(meter);
        for (MerchantOffer offer : villager.getOffers()) {
            offer.setSpecialPriceDiff(tier.priceAdjustment());
        }

        int streak = villager.getData(ModAttachments.UNHAPPY_STREAK_TICKS.get());
        streak = (tier == Happiness.UNHAPPY) ? streak + HAPPINESS_CHECK_INTERVAL_TICKS : 0;
        villager.setData(ModAttachments.UNHAPPY_STREAK_TICKS.get(), streak);

        if (streak >= ModConfig.HAPPINESS_QUIT_THRESHOLD_TICKS.get()) {
            Component name = villager.getCustomName() != null
                    ? villager.getCustomName() : Component.translatable("entity.minecraft.villager");
            EmployeeManager.quit(level, villager, data);
            clearSignal(villager.getUUID()); // hygiene — matches every other employee-removal path
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                    villager.getBoundingBox().inflate(PROMOTION_SIGNAL_RADIUS))) {
                player.sendSystemMessage(Component.translatable("message.secondshift.employee.quit", name));
            }
            return true;
        }
        return false;
    }

    /**
     * PROG-03: the "unmissable" promotion-ready signal — {@code HAPPY_VILLAGER} particles above
     * the employee (server-broadcast, no per-player targeting needed) plus an action-bar message
     * to every real player within {@link #PROMOTION_SIGNAL_RADIUS} blocks, naming the employee and
     * its new tier so the player knows which altar to visit.
     */
    private static void signalPromotable(ServerLevel level, Villager villager, int newTier) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                villager.getX(), villager.getY() + 2.2D, villager.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.0D);

        Component name = villager.getCustomName() != null ? villager.getCustomName()
                : Component.translatable("entity.minecraft.villager");
        Component message = Component.empty()
                .append(name.copy().withStyle(ChatFormatting.GREEN))
                .append(Component.translatable("message.secondshift.employee.ready_for_promotion", newTier)
                        .withStyle(ChatFormatting.YELLOW));

        List<ServerPlayer> nearby = level.getEntitiesOfClass(ServerPlayer.class,
                villager.getBoundingBox().inflate(PROMOTION_SIGNAL_RADIUS));
        for (ServerPlayer player : nearby) {
            player.displayClientMessage(message, true);
            player.sendSystemMessage(message);
        }
    }

    /**
     * ECON-04 (D-05): the voluntary release path. Sneak + right-click an employee with the
     * Harvester. A plain right-click must keep opening the vanilla trade screen — binding release
     * to that would make an employee untradeable while its owner holds the mod's signature tool —
     * so this is sneak-gated, which is a no-op in vanilla and cannot shadow trading.
     *
     * <p>Does not duplicate {@link HarvesterEvents}' reap logic: it applies a real {@code
     * playerAttack} damage source, which {@code DamageSource#getWeaponItem()} resolves to the
     * held Harvester, so the existing instakill + 1-Fragment + FX path in {@link HarvesterEvents}
     * runs verbatim — this handler's only job is recognizing the sneak-release gesture and
     * triggering that real damage event.
     */
    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager) || !villager.hasData(ModAttachments.EMPLOYEE.get())) {
            return;
        }
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) {
            return; // plain right-click must keep opening the trade screen
        }
        ItemStack held = player.getItemInHand(event.getHand());
        if (!(held.getItem() instanceof HarvesterItem)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        villager.hurt(villager.level().damageSources().playerAttack(player), Float.MAX_VALUE);
    }

    /**
     * EMP-06: drop-recovery on every death that is NOT the Harvester reap (handled by {@link
     * HarvesterEvents}, which also releases the altar for that path) and NOT the ALTAR-06 firing
     * smite ({@link EmployeeFiring#fire} removes the {@code EmployeeData} attachment before
     * killing, so {@code hasData} below is already false for that death and this handler
     * correctly does nothing — see that class's doc comment).
     *
     * <p>Spawned here, not from {@code LivingDropsEvent}, for the same reason {@link
     * HarvesterEvents}'s Fragment guarantee is: {@code LivingDropsEvent} is entirely suppressed by
     * {@code doMobLoot=false} and {@code shouldDropLoot()}, and losing a four-Fragment employee to
     * a game rule would be a genuinely bad outcome. Yield is 1 Soul Block + 2 slimeballs — a fixed
     * count (not a random range) so GameTests can assert an exact number.
     *
     * <p><b>Why the whole Soul Block comes back here but only a Fragment comes back from the
     * Harvester:</b> the Harvester reap is a choice made at leisure, so it costs 3 of the 4
     * Fragments — deciding to let an employee go should sting. Dying to a creeper is not a
     * choice; making bad luck cost the same as a decision would just be punishing.
     */
    @SubscribeEvent
    static void onOtherDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide()) {
            return;
        }
        if (!villager.hasData(ModAttachments.EMPLOYEE.get())) {
            return; // not an employee, or already stripped by the ALTAR-06 firing smite
        }
        if (HarvesterEvents.isHarvesterKillOfVillager(villager, event.getSource())) {
            return; // that path's own onDeath handler already released the altar
        }
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }

        EmployeeData data = villager.getData(ModAttachments.EMPLOYEE.get());
        EmployeeManager.releaseAltar(level, data.altarPos(), villager.getUUID());
        lastSignaledLevel.remove(villager.getUUID()); // Phase 7 hygiene — no leak for a dead employee

        ItemEntity soulBlock = new ItemEntity(level, villager.getX(), villager.getY() + 0.5D, villager.getZ(),
                new ItemStack(ModItems.SOUL_BLOCK_ITEM.get()));
        soulBlock.setDefaultPickUpDelay();
        level.addFreshEntity(soulBlock);

        ItemEntity slimeballs = new ItemEntity(level, villager.getX(), villager.getY() + 0.5D, villager.getZ(),
                new ItemStack(Items.SLIME_BALL, 2));
        slimeballs.setDefaultPickUpDelay();
        level.addFreshEntity(slimeballs);

        level.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }
}

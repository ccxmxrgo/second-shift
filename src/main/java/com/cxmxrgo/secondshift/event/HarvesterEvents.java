package com.cxmxrgo.secondshift.event;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.content.item.HarvesterItem;
import com.cxmxrgo.secondshift.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * The Harvester soul-reap (ECON-02 / D-08 / D-09 / D-10).
 *
 * <p>The one genuinely custom mechanic of Phase 2: a single melee hit on any
 * {@link Villager} with the {@link HarvesterItem} kills it instantly and drops exactly
 * one {@code secondshift:soul_fragment}, regardless of health / armor / Resistance /
 * absorption. Every downstream consequence (death, XP, particles, advancements) is left
 * to vanilla — only <em>the decision</em> "this hit kills this villager" is custom.
 *
 * <p><b>D-08 instakill</b> — game-bus {@link LivingDamageEvent.Pre} (post-mitigation:
 * armor and Resistance are already applied by the time this fires, only absorption is
 * handled after). {@code setNewDamage(health + absorption + 1)} is therefore
 * unconditionally lethal. The pre-mitigation incoming-damage hook is deliberately not
 * used — Resistance V can survive even {@code Float.MAX_VALUE} before mitigation.
 *
 * <p><b>D-10 fragment drop</b> — spawned from {@link LivingDeathEvent} (not
 * {@link LivingDropsEvent}) so the guarantee survives {@code doMobLoot=false} and any mod
 * that cancels/consumes {@code LivingDropsEvent}: exactly one Fragment {@link ItemEntity}
 * added via {@code level.addFreshEntity(...)}. A separate {@link LivingDropsEvent} handler
 * only calls {@code getDrops().clear()} to strip vanilla drops. Looting is intentionally
 * ignored (D-07). Non-Harvester kills never touch either path, so vanilla villager loot is
 * unchanged.
 *
 * <p><b>D-09 target scope</b> — {@code target instanceof net.minecraft.world.entity.npc.Villager}
 * exactly (includes babies; excludes the wandering trader, whose class does not extend
 * {@code Villager}, and the zombie villager, which extends {@code Zombie}). The check is
 * deliberately the concrete {@code Villager} type, never a shared villager supertype.
 *
 * <p>The bus is derived from the event type (game bus); the class self-registers via the
 * annotation, matching {@code ModRegistrySelfCheck} — no wiring line in {@code SecondShift}.
 */
@EventBusSubscriber(modid = SecondShift.MODID)
public final class HarvesterEvents {

    private HarvesterEvents() {}

    @SubscribeEvent
    static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (!isHarvesterKillOfVillager(target, source)) {
            return;
        }
        // Post-mitigation override: guaranteed lethal past armor / Resistance V / absorption.
        // (LivingDamageEvent.Pre fires after armor + potion reductions; only absorption is
        // subtracted afterwards, so adding it back here keeps the hit lethal.)
        event.setNewDamage(target.getHealth() + target.getAbsorptionAmount() + 1.0F);

        // D-07: the reap costs the Harvester one point of durability. HarvesterItem is a plain
        // Item (no SwordItem#hurtEnemy attack path), and the kill is decided here rather than
        // through the weapon's attack path, so per-hit damage must be applied explicitly —
        // this is what makes durability(250) real and Unbreaking/Mending meaningful.
        // getWeaponItem() delegates to the attacker's live main-hand stack, so this damages
        // the actual held Harvester.
        if (source.getEntity() instanceof LivingEntity reaper) {
            ItemStack weapon = source.getWeaponItem();
            if (weapon != null && weapon.getItem() instanceof HarvesterItem) {
                weapon.hurtAndBreak(1, reaper, EquipmentSlot.MAINHAND);
            }
        }
    }

    /**
     * D-08 / D-10 guaranteed Soul Fragment. Spawned from {@link LivingDeathEvent} rather
     * than {@link LivingDropsEvent} so the guarantee is <b>not</b> gated by the
     * {@code doMobLoot} game rule or {@code shouldDropLoot()} (both of which suppress the
     * entire {@code LivingDropsEvent} pipeline). {@code onDrops} still runs, but only to
     * strip vanilla drops. A cancelled death means no reap, so bail on {@code isCanceled()}.
     */
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (!isHarvesterKillOfVillager(target, event.getSource())) {
            return;
        }

        Level level = target.level();
        // ECON-02: always exactly 1 Fragment. Looting is intentionally ignored (D-07).
        ItemEntity fragment = new ItemEntity(level,
                target.getX(), target.getY() + 0.5D, target.getZ(),
                new ItemStack(ModItems.SOUL_FRAGMENT.get()));
        fragment.setDefaultPickUpDelay();
        level.addFreshEntity(fragment);

        // D-10 / G-1: layered soul-harvest FX — all vanilla, server-broadcast, on the exact
        // reap path (right after the Fragment spawn) so it cannot desync from the reap.
        if (level instanceof ServerLevel serverLevel) {
            Player killer = event.getSource().getEntity() instanceof Player p ? p : null;
            playSoulHarvestFx(serverLevel, target, killer);
        }
    }

    /**
     * D-10 / G-1 soul-harvest FX — a layered, vanilla-only death-moment effect fired on the
     * exact same code path as the guaranteed Soul Fragment spawn (see {@link #onDeath}), so
     * the cosmetic burst can never desync from the actual reap.
     *
     * <p>All emission is server-side and fire-and-forget: {@link ServerLevel#sendParticles}
     * broadcasts to nearby players and {@code playSound(null, ...)} is positional. On a
     * headless dedicated server with no client / no nearby player every call is a harmless
     * no-op, so this method never throws and carries no asserts.
     *
     * @param level  the server level the corpse died in
     * @param corpse the dying villager — the FX anchor
     * @param killer the player who landed the reap; may be {@code null} (indirect source)
     */
    private static void playSoulHarvestFx(ServerLevel level, LivingEntity corpse, Player killer) {
        double x = corpse.getX();
        double z = corpse.getZ();
        double feetY = corpse.getY();
        double chestY = feetY + corpse.getBbHeight() * 0.5D;
        double topY = feetY + corpse.getBbHeight();

        // Chest burst — a sculk-catalyst-style soul bloom at the heart.
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, chestY, z, 40, 0.3D, 0.4D, 0.3D, 0.02D);

        // Rising SOUL column — ~2 blocks of souls lifting off the body.
        for (int i = 0; i < 8; i++) {
            double y = feetY + (i / 7.0D) * 2.0D;
            level.sendParticles(ParticleTypes.SOUL, x, y, z, 2, 0.05D, 0.02D, 0.05D, 0.01D);
        }

        // Single white flash at the chest.
        level.sendParticles(ParticleTypes.FLASH, x, chestY, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);

        // Soul stream to the killer, then an arrival burst at their chest.
        if (killer != null) {
            Vec3 from = new Vec3(x, chestY, z);
            Vec3 to = new Vec3(killer.getX(), killer.getY() + killer.getBbHeight() * 0.5D, killer.getZ());
            for (int j = 1; j <= 6; j++) {
                Vec3 p = from.lerp(to, j / 6.0D);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 3, 0.05D, 0.05D, 0.05D, 0.02D);
            }
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y, to.z, 20, 0.2D, 0.3D, 0.2D, 0.05D);
        }

        // Soul-colored pseudo-bolt — a vertical particle line dropping onto the villager.
        // visual-only particle line — deliberately NOT a LightningBolt; 02-04 SoulAltarBlock
        // owns the bolt-entity visual and these must read as different events.
        for (int k = 0; k < 12; k++) {
            double y = (topY + 6.0D) - (k / 11.0D) * 6.0D;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0.02D, 0.0D, 0.02D, 0.0D);
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.02D, 0.0D, 0.02D, 0.0D);
        }

        // Layered sounds at the corpse (SCULK_CATALYST_BLOOM pitched down + SOUL_ESCAPE,
        // plus a low eerie ELDER_GUARDIAN_CURSE tail).
        level.playSound(null, x, chestY, z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 1.0F, 0.7F);
        level.playSound(null, x, chestY, z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, x, chestY, z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.25F, 1.0F);

        // Sound at the killer — the soul arriving.
        if (killer != null) {
            level.playSound(null, killer.getX(), killer.getY(), killer.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.PLAYERS, 0.7F, 1.2F);
        }
    }

    /**
     * D-10 drop scrub. The Fragment itself is spawned in {@link #onDeath}; this handler only
     * strips any vanilla drops the villager would otherwise leave. Runs independently of
     * {@link #onDeath} — with {@code doMobLoot=false} it simply never fires (nothing to
     * scrub) and the Fragment still drops.
     */
    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity target = event.getEntity();
        if (!isHarvesterKillOfVillager(target, event.getSource())) {
            return; // non-Harvester kill: leave vanilla drops untouched (ECON-02)
        }
        event.getDrops().clear();
    }

    /**
     * True only for a server-side kill of a plain {@link Villager} by a player wielding a
     * {@link HarvesterItem}.
     *
     * <p>Phase 6 (ECON-04): a separate branch keyed on
     * {@code ((Villager) target).hasData(EMPLOYEE)} drops 1 Fragment, never a Soul Block.
     */
    private static boolean isHarvesterKillOfVillager(LivingEntity target, DamageSource src) {
        if (target.level().isClientSide) {
            return false;
        }
        if (!(target instanceof Villager)) {
            return false; // D-09: excludes the wandering trader + the zombie villager
        }
        if (!(src.getEntity() instanceof Player)) {
            return false;
        }
        // DamageSource#getWeaponItem() (A1: present on 21.1.248 — delegates to the direct
        // entity's main-hand item) is the server-side weapon snapshot; it survives a hotbar
        // swap between the hit and the death.
        ItemStack weapon = src.getWeaponItem();
        return weapon != null && weapon.getItem() instanceof HarvesterItem;
    }
}

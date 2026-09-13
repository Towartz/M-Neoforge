package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.neoforge.NeoForgeUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Set;

public class TriggerBot extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgTiming = this.settings.createGroup("Timing");
    private final SettingGroup sgMace = this.settings.createGroup("Auto Mace");

    private final Setting<Set<EntityType<?>>> entities = this.sgGeneral.add(
        new EntityTypeListSetting.Builder()
            .name("entities")
            .description("Entities to attack.")
            .onlyAttackable()
            .build()
    );

    private final Setting<Boolean> babies = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("babies")
            .description("Whether to attack baby animal variants.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> weaponsOnly = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("weapons-only")
            .description("Only attacks when holding a sword, axe, or mace.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> smartDelay = this.sgTiming.add(
        new BoolSetting.Builder()
            .name("smart-delay")
            .description("Uses vanilla weapon cooldown (100% charged).")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> hitDelay = this.sgTiming.add(
        new IntSetting.Builder()
            .name("hit-delay")
            .description("Tick delay between attacks when smart delay is off.")
            .defaultValue(0)
            .min(0)
            .sliderMax(60)
            .visible(() -> !smartDelay.get())
            .build()
    );

    private final Setting<Boolean> randomDelay = this.sgTiming.add(
        new BoolSetting.Builder()
            .name("random-delay")
            .description("Adds random jitter between attacks.")
            .defaultValue(false)
            .visible(() -> !smartDelay.get())
            .build()
    );

    private final Setting<Integer> randomDelayMax = this.sgTiming.add(
        new IntSetting.Builder()
            .name("random-delay-max")
            .description("Maximum random jitter in ticks.")
            .defaultValue(4)
            .min(0)
            .sliderMax(20)
            .visible(() -> randomDelay.get() && !smartDelay.get())
            .build()
    );

    private final Setting<Boolean> onlyCrits = this.sgTiming.add(
        new BoolSetting.Builder()
            .name("only-crits")
            .description("Only attacks while falling to land a critical hit.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> autoMace = this.sgMace.add(
        new BoolSetting.Builder()
            .name("auto-mace")
            .description("Automatically swaps to a Mace when falling to land a smash attack.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> minFallDistance = this.sgMace.add(
        new DoubleSetting.Builder()
            .name("min-fall-distance")
            .description("Minimum fall distance before auto-swapping to Mace.")
            .defaultValue(1.5)
            .min(0.5)
            .sliderMax(10.0)
            .visible(autoMace::get)
            .build()
    );

    private final Setting<Boolean> swapBack = this.sgMace.add(
        new BoolSetting.Builder()
            .name("swap-back")
            .description("Swaps back to previous hotbar slot after attacking.")
            .defaultValue(true)
            .visible(autoMace::get)
            .build()
    );

    private int hitDelayTimer = 0;

    public TriggerBot() {
        super(Categories.Combat, "trigger-bot", "Automatically attacks targeted entities directly under your crosshair.");
    }

    @Override
    public void onDeactivate() {
        hitDelayTimer = 0;
    }

    private boolean entityCheck(Entity entity) {
        if (mc.player == null || entity == null || entity.equals(mc.player) || entity.equals(mc.cameraEntity)) return false;
        if (!entity.isAlive()) return false;
        if (entity instanceof LivingEntity living && living.isDeadOrDying()) return false;
        if (!entities.get().contains(entity.getType())) return false;

        if (weaponsOnly.get()) {
            ItemStack held = mc.player.getMainHandItem();
            if (!NeoForgeUtils.isWeapon(held)) {
                return false;
            }
        }

        if (entity instanceof TamableAnimal tameable && tameable.getOwnerUUID() != null && tameable.getOwnerUUID().equals(mc.player.getUUID())) {
            return false;
        }

        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (AntiBot.isBot(player)) return false;
        }

        if (entity instanceof AgeableMob ageable && !babies.get() && ageable.isBaby()) return false;

        return true;
    }

    private boolean delayCheck() {
        if (mc.player == null) return false;

        if (onlyCrits.get()) {
            boolean isFalling = mc.player.fallDistance > 0.0F && !mc.player.onGround() && !mc.player.onClimbable() && !mc.player.isInWater();
            if (!isFalling) return false;
        }

        if (smartDelay.get()) {
            return mc.player.getAttackStrengthScale(0.5F) >= 1.0F;
        } else if (hitDelayTimer > 0) {
            hitDelayTimer--;
            return false;
        } else {
            hitDelayTimer = hitDelay.get();
            if (randomDelay.get()) {
                hitDelayTimer += (int) Math.round(Math.random() * (double) randomDelayMax.get());
            }
            return true;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || !mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR || mc.gameMode == null) return;

        if (mc.hitResult instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            if (entityCheck(target) && delayCheck()) {
                int prevSlot = mc.player.getInventory().selected;
                boolean swapped = false;

                if (autoMace.get() && mc.player.fallDistance >= minFallDistance.get() && mc.player.getDeltaMovement().y < 0) {
                    FindItemResult mace = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof MaceItem);
                    if (mace.found()) {
                        InvUtils.swap(mace.slot(), false);
                        swapped = true;
                    }
                }

                mc.gameMode.attack(mc.player, target);
                mc.player.swing(InteractionHand.MAIN_HAND);

                if (swapped && swapBack.get()) {
                    InvUtils.swap(prevSlot, false);
                }
            }
        }
    }
}

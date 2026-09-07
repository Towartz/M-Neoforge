package meteordevelopment.meteorclient.systems.modules.combat.plus;

import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;

public class TriggerBot extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgTiming = this.settings.createGroup("Timing");
    private final SettingGroup sgMace = this.settings.createGroup("Auto Mace");

    private final Setting<Set<EntityType<?>>> entities = this.sgGeneral.add(new EntityTypeListSetting.Builder().name("entities").description("Entities to attack.").onlyAttackable().build());
    private final Setting<Boolean> babies = this.sgGeneral.add(new BoolSetting.Builder().name("babies").description("Whether or not to attack baby variants.").defaultValue(true).build());
    private final Setting<Boolean> smartDelay = this.sgTiming.add(new BoolSetting.Builder().name("smart-delay").description("Uses the vanilla cooldown to attack entities.").defaultValue(true).build());
    private final Setting<Integer> hitDelay = this.sgTiming.add(new IntSetting.Builder().name("hit-delay").description("How fast you hit the entity in ticks.").defaultValue(0).min(0).sliderMax(60).visible(() -> !this.smartDelay.get()).build());
    private final Setting<Boolean> randomDelayEnabled = this.sgTiming.add(new BoolSetting.Builder().name("random-delay-enabled").description("Adds random delay between hits.").defaultValue(false).visible(() -> !this.smartDelay.get()).build());
    private final Setting<Integer> randomDelayMax = this.sgTiming.add(new IntSetting.Builder().name("random-delay-max").description("The maximum value for random delay.").defaultValue(4).min(0).sliderMax(20).visible(() -> this.randomDelayEnabled.get() && !this.smartDelay.get()).build());
    private final Setting<Boolean> onlyCrits = this.sgTiming.add(new BoolSetting.Builder().name("only-crits").description("Attack enemy only if this attack crits after jump.").defaultValue(false).build());

    private final Setting<Boolean> autoMace = this.sgMace.add(new BoolSetting.Builder().name("auto-mace").description("Automatically swaps to a Mace when falling to perform a devastating smash attack.").defaultValue(true).build());
    private final Setting<Double> minFallDistance = this.sgMace.add(new DoubleSetting.Builder().name("min-fall-distance").description("Minimum fall distance before swapping to Mace.").defaultValue(1.5).min(0.5).sliderMax(10.0).visible(this.autoMace::get).build());
    private final Setting<Boolean> swapBack = this.sgMace.add(new BoolSetting.Builder().name("swap-back").description("Swaps back to previously selected hotbar slot after attacking.").defaultValue(true).visible(this.autoMace::get).build());

    private int hitDelayTimer;

    public TriggerBot() {
        super(Categories.PlusCombat, "Trigger-bot", "Automatically attacks targeted entities under crosshair.");
    }

    @Override
    public void onDeactivate() {
        this.hitDelayTimer = 0;
    }

    private boolean entityCheck(Entity entity) {
        if (this.mc.player == null || entity == null || entity.equals(this.mc.player) || entity.equals(this.mc.cameraEntity)) return false;
        if (!entity.isAlive()) return false;
        if (entity instanceof LivingEntity living && living.isDeadOrDying()) return false;
        if (!this.entities.get().contains(entity.getType())) return false;

        if (entity instanceof TamableAnimal tameable && tameable.getOwnerUUID() != null && tameable.getOwnerUUID().equals(this.mc.player.getUUID())) {
            return false;
        }

        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
            if (!Friends.get().shouldAttack(player)) return false;

            AntiBotPlus antiBot = Modules.get().get(AntiBotPlus.class);
            if (antiBot != null && antiBot.isBot(player)) return false;

            Teams teams = Modules.get().get(Teams.class);
            if (teams != null && teams.isInYourTeam(player)) return false;
        }

        if (entity instanceof AgeableMob ageable && !this.babies.get() && ageable.isBaby()) return false;

        return true;
    }

    private boolean delayCheck(Entity target) {
        if (this.mc.player == null) return false;
        if (this.onlyCrits.get() && !CriticalsPlus.allowCrit() && CriticalsPlus.needCrit(target)) {
            return false;
        }

        if (this.smartDelay.get()) {
            return this.mc.player.getAttackStrengthScale(0.5F) >= 1.0F;
        } else if (this.hitDelayTimer > 0) {
            this.hitDelayTimer--;
            return false;
        } else {
            this.hitDelayTimer = this.hitDelay.get();
            if (this.randomDelayEnabled.get()) {
                this.hitDelayTimer += (int) Math.round(Math.random() * (double) this.randomDelayMax.get());
            }
            return true;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || !this.mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR) return;

        if (this.mc.hitResult instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            if (this.entityCheck(target) && this.delayCheck(target)) {
                int prevSlot = this.mc.player.getInventory().selected;
                boolean swapped = false;
                if (this.autoMace.get() && this.mc.player.fallDistance >= this.minFallDistance.get() && this.mc.player.getDeltaMovement().y < 0) {
                    FindItemResult mace = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof MaceItem);
                    if (mace.found()) {
                        InvUtils.swap(mace.slot(), false);
                        swapped = true;
                    }
                }

                this.mc.gameMode.attack(this.mc.player, target);
                this.mc.player.swing(InteractionHand.MAIN_HAND);

                if (swapped && this.swapBack.get()) {
                    InvUtils.swap(prevSlot, false);
                }
            }
        }
    }
}

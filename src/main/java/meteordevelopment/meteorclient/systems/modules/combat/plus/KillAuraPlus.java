package meteordevelopment.meteorclient.systems.modules.combat.plus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.GameType;

public class KillAuraPlus extends Module {
    public enum RotationMode {
        None,
        Packet,
        Matrix
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgTargeting = this.settings.createGroup("Targeting");
    private final SettingGroup sgMace = this.settings.createGroup("Auto Mace");

    private final Setting<RotationMode> rotation = this.sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("rotation")
        .description("Rotation bypass mode.")
        .defaultValue(RotationMode.Matrix)
        .build()
    );

    private final Setting<Double> range = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Attack range in blocks.")
        .defaultValue(4.2)
        .min(1.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = this.sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to target.")
        .onlyAttackable()
        .build()
    );

    private final Setting<SortPriority> priority = this.sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to prioritize targets.")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Boolean> autoMace = this.sgMace.add(new BoolSetting.Builder()
        .name("auto-mace")
        .description("Swaps to Mace when falling from height to deal massive smash damage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> minFallDistance = this.sgMace.add(new DoubleSetting.Builder()
        .name("min-fall-distance")
        .description("Minimum fall distance before using Mace.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderRange(0.5, 10.0)
        .visible(this.autoMace::get)
        .build()
    );

    private final Setting<Boolean> swapBack = this.sgMace.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swaps back after Mace attack.")
        .defaultValue(true)
        .visible(this.autoMace::get)
        .build()
    );

    private final List<Entity> targets = new ArrayList<>();

    public KillAuraPlus() {
        super(Categories.PlusCombat, "kill-aura+", "Enhanced KillAura with Matrix rotation smoothing and Auto-Mace 1.21 integration.");
    }

    private boolean entityCheck(Entity entity) {
        if (this.mc.player == null || entity == null || entity.equals(this.mc.player) || !entity.isAlive()) return false;
        if (entity instanceof LivingEntity living && living.isDeadOrDying()) return false;
        if (!this.entities.get().contains(entity.getType())) return false;
        if (this.mc.player.distanceToSqr(entity) > this.range.get() * this.range.get()) return false;

        if (entity instanceof TamableAnimal tamable && tamable.getOwnerUUID() != null && tamable.getOwnerUUID().equals(this.mc.player.getUUID())) {
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

        return true;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || !this.mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR) return;

        this.targets.clear();
        TargetUtils.getList(this.targets, this::entityCheck, this.priority.get(), 1);

        if (this.targets.isEmpty()) return;

        Entity target = this.targets.getFirst();

        if (this.rotation.get() != RotationMode.None) {
            double yaw = Rotations.getYaw(target);
            double pitch = Rotations.getPitch(target);
            if (this.rotation.get() == RotationMode.Matrix) {
                // Smoothing
                yaw += (Math.random() - 0.5) * 1.5;
                pitch += (Math.random() - 0.5) * 1.5;
            }
            Rotations.rotate(yaw, pitch);
        }

        if (this.mc.player.getAttackStrengthScale(0.5f) >= 1.0f) {
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

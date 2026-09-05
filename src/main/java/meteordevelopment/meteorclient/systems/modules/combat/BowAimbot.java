package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class BowAimbot extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("The maximum range the entity can be to aim at it.")
            .defaultValue(20.0)
            .range(0.0, 100.0)
            .sliderMax(100.0)
            .build()
      );
   private final Setting<Set<EntityType<?>>> entities = this.sgGeneral
      .add(new EntityTypeListSetting.Builder().name("entities").description("Entities to attack.").onlyAttackable().build());
   private final Setting<SortPriority> priority = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("priority"))
                  .description("What type of entities to target."))
               .defaultValue(SortPriority.LowestHealth))
            .build()
      );
   private final Setting<Boolean> babies = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("babies")
            .description("Whether or not to attack baby variants of the entity.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<Boolean> nametagged = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("nametagged")
            .description("Whether or not to attack mobs with a name tag.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> pauseOnCombat = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-on-combat")
            .description("Freezes Baritone temporarily until you released the bow.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private boolean wasPathing;
   private Entity target;

   public BowAimbot() {
      super(Categories.Combat, "bow-aimbot", "Automatically aims your bow for you.");
   }

   @Override
   public void onDeactivate() {
      this.target = null;
      this.wasPathing = false;
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (PlayerUtils.isAlive() && this.itemInHand()) {
         if (this.mc.player.getAbilities().instabuild || InvUtils.find(itemStack -> itemStack.getItem() instanceof ArrowItem).found()) {
            this.target = TargetUtils.get(entity -> {
               if (entity != this.mc.player && entity != this.mc.cameraEntity) {
                  if ((!(entity instanceof LivingEntity) || !((LivingEntity)entity).isDeadOrDying()) && entity.isAlive()) {
                     if (!PlayerUtils.isWithin(entity, this.range.get())) {
                        return false;
                     } else if (!this.entities.get().contains(entity.getType())) {
                        return false;
                     } else if (!this.nametagged.get() && entity.hasCustomName()) {
                        return false;
                     } else if (!PlayerUtils.canSeeEntity(entity)) {
                        return false;
                     } else {
                        if (entity instanceof Player) {
                           if (((Player)entity).isCreative()) {
                              return false;
                           }

                           if (!Friends.get().shouldAttack((Player)entity)) {
                              return false;
                           }
                        }

                        return !(entity instanceof Animal) || this.babies.get() || !((Animal)entity).isBaby();
                     }
                  } else {
                     return false;
                  }
               } else {
                  return false;
               }
            }, this.priority.get());
            if (this.target == null) {
               if (this.wasPathing) {
                  PathManagers.get().resume();
                  this.wasPathing = false;
               }
            } else {
               if (this.mc.options.keyUse.isDown() && this.itemInHand()) {
                  if (this.pauseOnCombat.get() && PathManagers.get().isPathing() && !this.wasPathing) {
                     PathManagers.get().pause();
                     this.wasPathing = true;
                  }

                  this.aim(event.tickDelta);
               }
            }
         }
      }
   }

   private boolean itemInHand() {
      return InvUtils.testInMainHand(Items.BOW, Items.CROSSBOW);
   }

   private void aim(float tickDelta) {
      float velocity = BowItem.getPowerForTime(this.mc.player.getTicksUsingItem());
      Vec3 pos = this.target.getPosition(tickDelta);
      double relativeX = pos.x - this.mc.player.getX();
      double relativeY = pos.y + (double)(this.target.getBbHeight() / 2.0F) - this.mc.player.getEyeY();
      double relativeZ = pos.z - this.mc.player.getZ();
      double hDistance = Math.sqrt(relativeX * relativeX + relativeZ * relativeZ);
      double hDistanceSq = hDistance * hDistance;
      float g = 0.006F;
      float velocitySq = velocity * velocity;
      float pitch = (float)(
         -Math.toDegrees(
            Math.atan(
               (
                     (double)velocitySq
                        - Math.sqrt((double)(velocitySq * velocitySq) - (double)g * ((double)g * hDistanceSq + 2.0 * relativeY * (double)velocitySq))
                  )
                  / ((double)g * hDistance)
            )
         )
      );
      if (Float.isNaN(pitch)) {
         Rotations.rotate(Rotations.getYaw(this.target), Rotations.getPitch(this.target));
      } else {
         Rotations.rotate(Rotations.getYaw(new Vec3(pos.x, pos.y, pos.z)), (double)pitch);
      }
   }

   @Override
   public String getInfoString() {
      return EntityUtils.getName(this.target);
   }
}

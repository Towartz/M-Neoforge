package meteordevelopment.meteorclient.systems.modules.world;

import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.item.SpawnEggItem;

public class AutoMount extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> checkSaddle = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("check-saddle")
            .description("Checks if the entity contains a saddle before mounting.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(new BoolSetting.Builder().name("rotate").description("Faces the entity you mount.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Set<EntityType<?>>> entities = this.sgGeneral
      .add(new EntityTypeListSetting.Builder().name("entities").description("Rideable entities.").filter(EntityUtils::isRideable).build());

   public AutoMount() {
      super(Categories.World, "auto-mount", "Automatically mounts entities.");
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (!this.mc.player.isPassenger()) {
         if (!this.mc.player.isShiftKeyDown()) {
            if (!(this.mc.player.getMainHandItem().getItem() instanceof SpawnEggItem)) {
               for (Entity entity : this.mc.level.entitiesForRendering()) {
                  if (this.entities.get().contains(entity.getType())
                     && PlayerUtils.isWithin(entity, 4.0)
                     && (
                        !(entity instanceof Pig) && !(entity instanceof SkeletonHorse) && !(entity instanceof Strider) && !(entity instanceof ZombieHorse)
                           || ((Saddleable)entity).isSaddled()
                     )
                     && (entity instanceof Llama || !(entity instanceof Saddleable saddleable) || !this.checkSaddle.get() || saddleable.isSaddled())) {
                     this.interact(entity);
                     return;
                  }
               }
            }
         }
      }
   }

   private void interact(Entity entity) {
      if (this.rotate.get()) {
         Rotations.rotate(
            Rotations.getYaw(entity), Rotations.getPitch(entity), -100, () -> this.mc.gameMode.interact(this.mc.player, entity, InteractionHand.MAIN_HAND)
         );
      } else {
         this.mc.gameMode.interact(this.mc.player, entity, InteractionHand.MAIN_HAND);
      }
   }
}

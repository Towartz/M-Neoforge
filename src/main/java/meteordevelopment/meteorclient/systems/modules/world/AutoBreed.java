package meteordevelopment.meteorclient.systems.modules.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;

public class AutoBreed extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Set<EntityType<?>>> entities = this.sgGeneral
      .add(
         new EntityTypeListSetting.Builder()
            .name("entities")
            .description("Entities to breed.")
            .defaultValue(
               EntityType.HORSE,
               EntityType.DONKEY,
               EntityType.COW,
               EntityType.MOOSHROOM,
               EntityType.SHEEP,
               EntityType.PIG,
               EntityType.CHICKEN,
               EntityType.WOLF,
               EntityType.CAT,
               EntityType.OCELOT,
               EntityType.RABBIT,
               EntityType.LLAMA,
               EntityType.TURTLE,
               EntityType.PANDA,
               EntityType.FOX,
               EntityType.BEE,
               EntityType.STRIDER,
               EntityType.HOGLIN
            )
            .onlyAttackable()
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(new DoubleSetting.Builder().name("range").description("How far away the animals can be to be bred.").min(0.0).defaultValue(4.5).build());
   private final Setting<InteractionHand> hand = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("hand-for-breeding"))
                  .description("The hand to use for breeding."))
               .defaultValue(InteractionHand.MAIN_HAND))
            .build()
      );
   private final Setting<AutoBreed.EntityAge> mobAgeFilter = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mob-age-filter"))
                  .description("Determines the age of the mobs to target (baby, adult, or both)."))
               .defaultValue(AutoBreed.EntityAge.Adult))
            .build()
      );
   private final List<Entity> animalsFed = new ArrayList<>();

   public AutoBreed() {
      super(Categories.World, "auto-breed", "Automatically breeds specified animals.");
   }

   @Override
   public void onActivate() {
      this.animalsFed.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      for (Entity entity : this.mc.level.entitiesForRendering()) {
         if (entity instanceof Animal) {
            Animal animal = (Animal)entity;
            if (this.entities.get().contains(animal.getType())) {
               switch ((AutoBreed.EntityAge)this.mobAgeFilter.get()) {
                  case Baby:
                     if (!animal.isBaby()) {
                        continue;
                     }
                     break;
                  case Adult:
                     if (animal.isBaby()) {
                        continue;
                     }
                  case Both:
                     break;
                  default:
                     throw new MatchException(null, null);
               }

               if (!this.animalsFed.contains(animal)
                  && PlayerUtils.isWithin(animal, this.range.get())
                  && animal.isFood(this.hand.get() == InteractionHand.MAIN_HAND ? this.mc.player.getMainHandItem() : this.mc.player.getOffhandItem())) {
                  Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity), -100, () -> {
                     this.mc.gameMode.interact(this.mc.player, animal, this.hand.get());
                     this.mc.player.swing(this.hand.get());
                     this.animalsFed.add(animal);
                  });
                  return;
               }
            }
         }
      }
   }

   public static enum EntityAge {
      Baby,
      Adult,
      Both;
   }
}

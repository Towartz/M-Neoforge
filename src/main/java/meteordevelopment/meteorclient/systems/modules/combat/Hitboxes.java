package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.Set;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;

public class Hitboxes extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Set<EntityType<?>>> entities = this.sgGeneral
      .add(new EntityTypeListSetting.Builder().name("entities").description("Which entities to target.").defaultValue(EntityType.PLAYER).build());
   private final Setting<Double> value = this.sgGeneral
      .add(new DoubleSetting.Builder().name("expand").description("How much to expand the hitbox of the entity.").defaultValue(0.5).build());
   private final Setting<Boolean> ignoreFriends = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-friends").description("Doesn't expand the hitboxes of friends.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> onlyOnWeapon = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("only-on-weapon")
            .description("Only modifies hitbox when holding a weapon in hand.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );

   public Hitboxes() {
      super(Categories.Combat, "hitboxes", "Expands an entity's hitboxes.");
   }

   public double getEntityValue(Entity entity) {
      if (this.isActive() && this.testWeapon() && (!this.ignoreFriends.get() || !(entity instanceof Player) || !Friends.get().isFriend((Player)entity))) {
         return this.entities.get().contains(entity.getType()) ? this.value.get() : 0.0;
      } else {
         return 0.0;
      }
   }

   private boolean testWeapon() {
      return !this.onlyOnWeapon.get()
         ? true
         : InvUtils.testInHands(itemStack -> itemStack.getItem() instanceof SwordItem || itemStack.getItem() instanceof AxeItem);
   }
}

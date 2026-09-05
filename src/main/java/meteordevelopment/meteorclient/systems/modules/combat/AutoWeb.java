package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

public class AutoWeb extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("target-range")
            .description("The maximum distance to target players.")
            .defaultValue(4.0)
            .range(0.0, 5.0)
            .sliderMax(5.0)
            .build()
      );
   private final Setting<SortPriority> priority = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("target-priority"))
                  .description("How to filter targets within range."))
               .defaultValue(SortPriority.LowestDistance))
            .build()
      );
   private final Setting<Boolean> doubles = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("doubles")
            .description("Places webs in the target's upper hitbox as well as the lower hitbox.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(new BoolSetting.Builder().name("rotate").description("Rotates towards the webs when placing.").defaultValue(Boolean.valueOf(true)).build());
   private Player target = null;

   public AutoWeb() {
      super(Categories.Combat, "auto-web", "Automatically places webs on other players.");
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (TargetUtils.isBadTarget(this.target, this.range.get())) {
         this.target = TargetUtils.getPlayerTarget(this.range.get(), this.priority.get());
         if (TargetUtils.isBadTarget(this.target, this.range.get())) {
            return;
         }
      }

      BlockUtils.place(this.target.blockPosition(), InvUtils.findInHotbar(Items.COBWEB), this.rotate.get(), 0, false);
      if (this.doubles.get()) {
         BlockUtils.place(this.target.blockPosition().offset(0, 1, 0), InvUtils.findInHotbar(Items.COBWEB), this.rotate.get(), 0, false);
      }
   }
}

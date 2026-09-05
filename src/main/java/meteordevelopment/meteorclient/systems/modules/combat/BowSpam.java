package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Items;

public class BowSpam extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> charge = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("charge")
            .description("How long to charge the bow before releasing in ticks.")
            .defaultValue(Integer.valueOf(5))
            .range(5, 20)
            .sliderRange(5, 20)
            .build()
      );
   private final Setting<Boolean> onlyWhenHoldingRightClick = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("when-holding-right-click")
            .description("Works only when holding right click.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private boolean wasBow = false;
   private boolean wasHoldingRightClick = false;

   public BowSpam() {
      super(Categories.Combat, "bow-spam", "Spams arrows.");
   }

   @Override
   public void onActivate() {
      this.wasBow = false;
      this.wasHoldingRightClick = false;
   }

   @Override
   public void onDeactivate() {
      this.setPressed(false);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player.getAbilities().instabuild || InvUtils.find(itemStack -> itemStack.getItem() instanceof ArrowItem).found()) {
         if (!this.onlyWhenHoldingRightClick.get() || this.mc.options.keyUse.isDown()) {
            boolean isBow = this.mc.player.getMainHandItem().getItem() == Items.BOW;
            if (!isBow && this.wasBow) {
               this.setPressed(false);
            }

            this.wasBow = isBow;
            if (!isBow) {
               return;
            }

            if (this.mc.player.getTicksUsingItem() >= this.charge.get()) {
               this.mc.gameMode.releaseUsingItem(this.mc.player);
            } else {
               this.setPressed(true);
            }

            this.wasHoldingRightClick = this.mc.options.keyUse.isDown();
         } else if (this.wasHoldingRightClick) {
            this.setPressed(false);
            this.wasHoldingRightClick = false;
         }
      }
   }

   private void setPressed(boolean pressed) {
      this.mc.options.keyUse.setDown(pressed);
   }
}

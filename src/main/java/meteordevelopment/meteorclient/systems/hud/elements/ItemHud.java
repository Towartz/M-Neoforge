package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public class ItemHud extends HudElement {
   public static final HudElementInfo<ItemHud> INFO = new HudElementInfo<>(Hud.GROUP, "item", "Displays the item count.", ItemHud::new);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private final Setting<Item> item = this.sgGeneral
      .add(new ItemSetting.Builder().name("item").description("Item to display").defaultValue(Items.TOTEM_OF_UNDYING).build());
   private final Setting<ItemHud.NoneMode> noneMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("none-mode"))
                  .description("How to render the item when you don't have the specified item in your inventory."))
               .defaultValue(ItemHud.NoneMode.HideCount))
            .build()
      );
   private final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Scale of the item.")
            .defaultValue(2.0)
            .onChanged(aDouble -> this.calculateSize())
            .min(1.0)
            .sliderRange(1.0, 4.0)
            .build()
      );
   private final Setting<Integer> border = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("border")
            .description("How much space to add around the element.")
            .defaultValue(Integer.valueOf(0))
            .onChanged(integer -> this.calculateSize())
            .build()
      );
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays background.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<SettingColor> backgroundColor = this.sgBackground
      .add(
         new ColorSetting.Builder()
            .name("background-color")
            .description("Color used for the background.")
            .visible(this.background::get)
            .defaultValue(new SettingColor(25, 25, 25, 50))
            .build()
      );

   private ItemHud() {
      super(INFO);
      this.calculateSize();
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + (double)(this.border.get() * 2), height + (double)(this.border.get() * 2));
   }

   private void calculateSize() {
      this.setSize(17.0 * this.scale.get(), 17.0 * this.scale.get());
   }

   @Override
   public void render(HudRenderer renderer) {
      ItemStack itemStack = new ItemStack((ItemLike)this.item.get(), InvUtils.find(this.item.get()).count());
      if (this.noneMode.get() != ItemHud.NoneMode.HideItem || !itemStack.isEmpty()) {
         renderer.post(() -> {
            double x = (double)(this.x + this.border.get());
            double y = (double)(this.y + this.border.get());
            this.render(renderer, itemStack, (int)x, (int)y);
         });
      } else if (this.isInEditor()) {
         renderer.line((double)this.x, (double)this.y, (double)(this.x + this.getWidth()), (double)(this.y + this.getHeight()), Color.GRAY);
         renderer.line((double)this.x, (double)(this.y + this.getHeight()), (double)(this.x + this.getWidth()), (double)this.y, Color.GRAY);
      }

      if (this.background.get()) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), this.backgroundColor.get());
      }
   }

   private void render(HudRenderer renderer, ItemStack itemStack, int x, int y) {
      if (this.noneMode.get() == ItemHud.NoneMode.HideItem) {
         renderer.item(itemStack, x, y, this.scale.get().floatValue(), true);
      } else {
         String countOverride = null;
         boolean resetToZero = false;
         if (itemStack.isEmpty()) {
            if (this.noneMode.get() == ItemHud.NoneMode.ShowCount) {
               countOverride = "0";
            }

            itemStack.setCount(1);
            resetToZero = true;
         }

         renderer.item(itemStack, x, y, this.scale.get().floatValue(), true, countOverride);
         if (resetToZero) {
            itemStack.setCount(0);
         }
      }
   }

   public static enum NoneMode {
      HideItem,
      HideCount,
      ShowCount;

      @Override
      public String toString() {
         return switch (this) {
            case HideItem -> "Hide Item";
            case HideCount -> "Hide Count";
            case ShowCount -> "Show Count";
         };
      }
   }
}

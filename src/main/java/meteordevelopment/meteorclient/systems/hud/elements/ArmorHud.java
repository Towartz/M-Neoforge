package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ArmorHud extends HudElement {
   public static final HudElementInfo<ArmorHud> INFO = new HudElementInfo<>(Hud.GROUP, "armor", "Displays your armor.", ArmorHud::new);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDurability = this.settings.createGroup("Durability");
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private final Setting<ArmorHud.Orientation> orientation = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("orientation"))
                     .description("How to display armor."))
                  .defaultValue(ArmorHud.Orientation.Horizontal))
               .onChanged(val -> this.calculateSize()))
            .build()
      );
   private final Setting<Boolean> flipOrder = this.sgGeneral
      .add(new BoolSetting.Builder().name("flip-order").description("Flips the order of armor items.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("The scale.")
            .defaultValue(2.0)
            .onChanged(aDouble -> this.calculateSize())
            .min(1.0)
            .sliderRange(1.0, 5.0)
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
   private final Setting<ArmorHud.Durability> durability = this.sgDurability
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("durability"))
                     .description("How to display armor durability."))
                  .defaultValue(ArmorHud.Durability.Bar))
               .onChanged(durability1 -> this.calculateSize()))
            .build()
      );
   private final Setting<SettingColor> durabilityColor = this.sgDurability
      .add(
         new ColorSetting.Builder()
            .name("durability-color")
            .description("Color of the text.")
            .visible(() -> this.durability.get() == ArmorHud.Durability.Total || this.durability.get() == ArmorHud.Durability.Percentage)
            .defaultValue(new SettingColor())
            .build()
      );
   private final Setting<Boolean> durabilityShadow = this.sgDurability
      .add(
         new BoolSetting.Builder()
            .name("durability-shadow")
            .description("Text shadow.")
            .visible(() -> this.durability.get() == ArmorHud.Durability.Total || this.durability.get() == ArmorHud.Durability.Percentage)
            .defaultValue(Boolean.valueOf(true))
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

   public ArmorHud() {
      super(INFO);
      this.calculateSize();
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + (double)(this.border.get() * 2), height + (double)(this.border.get() * 2));
   }

   private void calculateSize() {
      switch ((ArmorHud.Orientation)this.orientation.get()) {
         case Horizontal:
            this.setSize(16.0 * this.scale.get() * 4.0 + 8.0, 16.0 * this.scale.get());
            break;
         case Vertical:
            this.setSize(16.0 * this.scale.get(), 16.0 * this.scale.get() * 4.0 + 8.0);
      }
   }

   @Override
   public void render(HudRenderer renderer) {
      if (this.background.get()) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), this.backgroundColor.get());
      }

      double x = (double)this.x;
      double y = (double)this.y;
      int slot = this.flipOrder.get() ? 3 : 0;

      for (int position = 0; position < 4; position++) {
         ItemStack itemStack = this.getItem(slot);
         double armorX;
         double armorY;
         if (this.orientation.get() == ArmorHud.Orientation.Vertical) {
            armorX = x;
            armorY = y + (double)(position * 18) * this.scale.get();
         } else {
            armorX = x + (double)(position * 18) * this.scale.get();
            armorY = y;
         }

         renderer.item(
            itemStack,
            (int)armorX,
            (int)armorY,
            this.scale.get().floatValue(),
            itemStack.isDamageableItem() && this.durability.get() == ArmorHud.Durability.Bar
         );
         if (itemStack.isDamageableItem()
            && !this.isInEditor()
            && this.durability.get() != ArmorHud.Durability.Bar
            && this.durability.get() != ArmorHud.Durability.None) {
            String message = switch ((ArmorHud.Durability)this.durability.get()) {
               case Total -> Integer.toString(itemStack.getMaxDamage() - itemStack.getDamageValue());
               case Percentage -> Integer.toString(
               Math.round((float)(itemStack.getMaxDamage() - itemStack.getDamageValue()) * 100.0F / (float)itemStack.getMaxDamage())
            );
               default -> "err";
            };
            double messageWidth = renderer.textWidth(message);
            if (this.orientation.get() == ArmorHud.Orientation.Vertical) {
               armorX = x + 8.0 * this.scale.get() - messageWidth / 2.0;
               armorY = y + (double)(18 * position) * this.scale.get() + (18.0 * this.scale.get() - renderer.textHeight());
            } else {
               armorX = x + (double)(18 * position) * this.scale.get() + 8.0 * this.scale.get() - messageWidth / 2.0;
               armorY = y + ((double)this.getHeight() - renderer.textHeight());
            }

            renderer.text(message, armorX, armorY, this.durabilityColor.get(), this.durabilityShadow.get());
         }

         if (this.flipOrder.get()) {
            slot--;
         } else {
            slot++;
         }
      }
   }

   private ItemStack getItem(int i) {
      if (this.isInEditor()) {
         return switch (i) {
            case 1 -> Items.NETHERITE_LEGGINGS.getDefaultInstance();
            case 2 -> Items.NETHERITE_CHESTPLATE.getDefaultInstance();
            case 3 -> Items.NETHERITE_HELMET.getDefaultInstance();
            default -> Items.NETHERITE_BOOTS.getDefaultInstance();
         };
      } else {
         return MeteorClient.mc.player.getInventory().getArmor(i);
      }
   }

   public static enum Durability {
      None,
      Bar,
      Total,
      Percentage;
   }

   public static enum Orientation {
      Horizontal,
      Vertical;
   }
}

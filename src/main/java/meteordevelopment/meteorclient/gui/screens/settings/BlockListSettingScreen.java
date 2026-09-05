package meteordevelopment.meteorclient.gui.screens.settings;

import java.util.List;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.mixin.IdentifierAccessor;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class BlockListSettingScreen extends RegistryListSettingScreen<Block> {
   private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("minecraft", "");

   public BlockListSettingScreen(GuiTheme theme, Setting<List<Block>> setting) {
      super(theme, "Select Blocks", setting, setting.get(), BuiltInRegistries.BLOCK);
   }

   protected boolean includeValue(Block value) {
      Predicate<Block> filter = ((BlockListSetting)this.setting).filter;
      return filter == null ? value != Blocks.AIR : filter.test(value);
   }

   protected WWidget getValueWidget(Block value) {
      return this.theme.itemWithLabel(value.asItem().getDefaultInstance(), this.getValueName(value));
   }

   protected String getValueName(Block value) {
      return Names.get(value);
   }

   protected boolean skipValue(Block value) {
      return BuiltInRegistries.BLOCK.getKey(value).getPath().endsWith("_wall_banner");
   }

   protected Block getAdditionalValue(Block value) {
      String path = BuiltInRegistries.BLOCK.getKey(value).getPath();
      if (!path.endsWith("_banner")) {
         return null;
      } else {
         return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecraft", path.substring(0, path.length() - 6) + "wall_banner"));
      }
   }
}

package meteordevelopment.meteorclient.gui.screens.settings;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BlockDataSetting;
import meteordevelopment.meteorclient.settings.IBlockData;
import meteordevelopment.meteorclient.utils.misc.IChangeable;
import meteordevelopment.meteorclient.utils.misc.ICopyable;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.apache.commons.lang3.StringUtils;

public class BlockDataSettingScreen extends WindowScreen {
   private static final List<Block> BLOCKS = new ArrayList<>(100);
   private final BlockDataSetting<?> setting;
   private WTable table;
   private String filterText = "";

   public BlockDataSettingScreen(GuiTheme theme, BlockDataSetting<?> setting) {
      super(theme, "Configure Blocks");
      this.setting = setting;
   }

   @Override
   public void initWidgets() {
      WTextBox filter = this.add(this.theme.textBox("")).minWidth(400.0).expandX().widget();
      filter.setFocused(true);
      filter.action = () -> {
         this.filterText = filter.get().trim();
         this.table.clear();
         this.initTable();
      };
      this.table = this.add(this.theme.table()).expandX().widget();
      this.initTable();
   }

   public <T extends ICopyable<T> & ISerializable<T> & IChangeable & IBlockData<T>> void initTable() {
      for (Block block : BuiltInRegistries.BLOCK) {
         T blockData = (T)this.setting.get().get(block);
         if (blockData != null && blockData.isChanged()) {
            BLOCKS.addFirst(block);
         } else {
            BLOCKS.add(block);
         }
      }

      for (Block block : BLOCKS) {
         String name = Names.get(block);
         if (StringUtils.containsIgnoreCase(name, this.filterText)) {
            T blockData = (T)this.setting.get().get(block);
            this.table.add(this.theme.itemWithLabel(block.asItem().getDefaultInstance(), Names.get(block))).expandCellX();
            this.table.add(this.theme.label(blockData != null && blockData.isChanged() ? "*" : " "));
            WButton edit = this.table.add(this.theme.button(GuiRenderer.EDIT)).widget();
            edit.action = () -> {
               T data = blockData;
               if (blockData == null) {
                  data = (T)((ICopyable)this.setting.defaultData.get()).copy();
               }

               MeteorClient.mc.setScreen(data.createScreen(this.theme, block, (BlockDataSetting<T>)this.setting));
            };
            WButton reset = this.table.add(this.theme.button(GuiRenderer.RESET)).widget();
            reset.action = () -> {
               this.setting.get().remove(block);
               this.setting.onChanged();
               if (blockData != null && blockData.isChanged()) {
                  this.table.clear();
                  this.initTable();
               }
            };
            this.table.row();
         }
      }

      BLOCKS.clear();
   }
}

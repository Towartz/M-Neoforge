package meteordevelopment.meteorclient.gui.tabs.builtin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.screens.EditSystemScreen;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.macros.Macro;
import meteordevelopment.meteorclient.systems.macros.Macros;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import net.minecraft.client.gui.screens.Screen;

public class MacrosTab extends Tab {
   public MacrosTab() {
      super("Macros");
   }

   @Override
   public TabScreen createScreen(GuiTheme theme) {
      return new MacrosTab.MacrosScreen(theme, this);
   }

   @Override
   public boolean isScreen(Screen screen) {
      return screen instanceof MacrosTab.MacrosScreen;
   }

   private static class EditMacroScreen extends EditSystemScreen<Macro> {
      public EditMacroScreen(GuiTheme theme, Macro value, Runnable reload) {
         super(theme, value, reload);
      }

      public Macro create() {
         return new Macro();
      }

      @Override
      public boolean save() {
         if (!this.value.name.get().isBlank() && !this.value.messages.get().isEmpty() && this.value.keybind.get().isSet()) {
            if (this.isNew) {
               for (Macro m : Macros.get()) {
                  if (this.value.equals(m)) {
                     return false;
                  }
               }
            }

            if (this.isNew) {
               Macros.get().add(this.value);
            } else {
               Macros.get().save();
            }

            return true;
         } else {
            return false;
         }
      }

      @Override
      public Settings getSettings() {
         return this.value.settings;
      }
   }

   private static class MacrosScreen extends WindowTabScreen {
      public MacrosScreen(GuiTheme theme, Tab tab) {
         super(theme, tab);
      }

      @Override
      public void initWidgets() {
         WTable table = this.add(this.theme.table()).expandX().minWidth(400.0).widget();
         this.initTable(table);
         this.add(this.theme.horizontalSeparator()).expandX();
         WButton create = this.add(this.theme.button("Create")).expandX().widget();
         create.action = () -> MeteorClient.mc.setScreen(new MacrosTab.EditMacroScreen(this.theme, null, this::reload));
      }

      private void initTable(WTable table) {
         table.clear();
         if (!Macros.get().isEmpty()) {
            for (Macro macro : Macros.get()) {
               table.add(this.theme.label(macro.name.get() + " (" + macro.keybind.get() + ")"));
               WButton edit = table.add(this.theme.button(GuiRenderer.EDIT)).expandCellX().right().widget();
               edit.action = () -> MeteorClient.mc.setScreen(new MacrosTab.EditMacroScreen(this.theme, macro, this::reload));
               WMinus remove = table.add(this.theme.minus()).widget();
               remove.action = () -> {
                  Macros.get().remove(macro);
                  this.reload();
               };
               table.row();
            }
         }
      }

      @Override
      public boolean toClipboard() {
         return NbtUtils.toClipboard(Macros.get());
      }

      @Override
      public boolean fromClipboard() {
         return NbtUtils.fromClipboard(Macros.get());
      }
   }
}

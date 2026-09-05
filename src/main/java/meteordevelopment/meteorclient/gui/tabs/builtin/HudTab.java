package meteordevelopment.meteorclient.gui.tabs.builtin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.screens.HudEditorScreen;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;

public class HudTab extends Tab {
   public HudTab() {
      super("HUD");
   }

   @Override
   public TabScreen createScreen(GuiTheme theme) {
      return new HudTab.HudScreen(theme, this);
   }

   @Override
   public boolean isScreen(Screen screen) {
      return screen instanceof HudTab.HudScreen;
   }

   public static class HudScreen extends WindowTabScreen {
      private final Hud hud = Hud.get();

      public HudScreen(GuiTheme theme, Tab tab) {
         super(theme, tab);
         this.hud.settings.onActivated();
      }

      @Override
      public void initWidgets() {
         this.add(this.theme.settings(this.hud.settings)).expandX();
         this.add(this.theme.horizontalSeparator()).expandX();
         WButton openEditor = this.add(this.theme.button("Edit")).expandX().widget();
         openEditor.action = () -> MeteorClient.mc.setScreen(new HudEditorScreen(this.theme));
         WHorizontalList buttons = this.add(this.theme.horizontalList()).expandX().widget();
         buttons.add(this.theme.button("Clear")).expandX().widget().action = this.hud::clear;
         buttons.add(this.theme.button("Reset to default elements")).expandX().widget().action = this.hud::resetToDefaultElements;
         this.add(this.theme.horizontalSeparator()).expandX();
         WHorizontalList bottom = this.add(this.theme.horizontalList()).expandX().widget();
         bottom.add(this.theme.label("Active: "));
         WCheckbox active = bottom.add(this.theme.checkbox(this.hud.active)).expandCellX().widget();
         active.action = () -> this.hud.active = active.checked;
         WButton resetSettings = bottom.add(this.theme.button(GuiRenderer.RESET)).widget();
         resetSettings.action = this.hud.settings::reset;
      }

      @Override
      protected void onRenderBefore(GuiGraphics drawContext, float delta) {
         if (this.hud.active) {
            HudEditorScreen.renderElements(drawContext);
         }
      }

      @Override
      public boolean toClipboard() {
         return NbtUtils.toClipboard("hud-settings", this.hud.settings.toTag());
      }

      @Override
      public boolean fromClipboard() {
         CompoundTag clipboard = NbtUtils.fromClipboard(this.hud.settings.toTag());
         if (clipboard != null) {
            this.hud.settings.fromTag(clipboard);
            return true;
         } else {
            return false;
         }
      }
   }
}

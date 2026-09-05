package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.ModuleArgumentType;
import meteordevelopment.meteorclient.commands.arguments.SettingArgumentType;
import meteordevelopment.meteorclient.commands.arguments.SettingValueArgumentType;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.commands.SharedSuggestionProvider;

public class SettingCommand extends Command {
   public SettingCommand() {
      super("settings", "Allows you to view and change module settings.", "s");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(literal("hud").executes(context -> {
         TabScreen screen = Tabs.get().get(3).createScreen(GuiThemes.get());
         screen.parent = null;
         Utils.screenToOpen = screen;
         return 1;
      }));
      builder.then(argument("module", ModuleArgumentType.create()).executes(context -> {
         Module module = (Module)context.getArgument("module", Module.class);
         WidgetScreen screen = GuiThemes.get().moduleScreen(module);
         screen.parent = null;
         Utils.screenToOpen = screen;
         return 1;
      }));
      builder.then(
         argument("module", ModuleArgumentType.create()).then(((RequiredArgumentBuilder)argument("setting", SettingArgumentType.create()).executes(context -> {
            Setting<?> setting = SettingArgumentType.get(context);
            ModuleArgumentType.get(context).info("Setting (highlight)%s(default) is (highlight)%s(default).", setting.title, setting.get());
            return 1;
         })).then(argument("value", SettingValueArgumentType.create()).executes(context -> {
            Setting<?> setting = SettingArgumentType.get(context);
            String value = SettingValueArgumentType.get(context);
            if (setting.parse(value)) {
               ModuleArgumentType.get(context).info("Setting (highlight)%s(default) changed to (highlight)%s(default).", setting.title, value);
            }

            return 1;
         })))
      );
   }
}

package baritone.command;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.command.IBaritoneChatControl;
import baritone.api.command.argument.ICommandArgument;
import baritone.api.command.exception.CommandNotEnoughArgumentsException;
import baritone.api.command.exception.CommandNotFoundException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.command.manager.ICommandManager;
import baritone.api.event.events.ChatEvent;
import baritone.api.event.events.TabCompleteEvent;
import baritone.api.utils.Helper;
import baritone.api.utils.SettingsUtil;
import baritone.behavior.Behavior;
import baritone.command.argument.ArgConsumer;
import baritone.command.argument.CommandArguments;
import baritone.command.manager.CommandManager;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent.Action;
import net.minecraft.util.Tuple;

public class ExampleBaritoneControl extends Behavior implements Helper {
   private static final Settings settings = BaritoneAPI.getSettings();
   private final ICommandManager manager;

   public ExampleBaritoneControl(Baritone baritone) {
      super(baritone);
      this.manager = baritone.getCommandManager();
   }

   @Override
   public void onSendChatMessage(ChatEvent event) {
      String msg = event.getMessage();
      String prefix = settings.prefix.value;
      boolean forceRun = msg.startsWith(IBaritoneChatControl.FORCE_COMMAND_PREFIX);
      if ((!settings.prefixControl.value || !msg.startsWith(prefix)) && !forceRun) {
         if ((settings.chatControl.value || settings.chatControlAnyway.value) && this.runCommand(msg)) {
            event.cancel();
         }
      } else {
         event.cancel();
         String commandStr = msg.substring(forceRun ? IBaritoneChatControl.FORCE_COMMAND_PREFIX.length() : prefix.length());
         if (!this.runCommand(commandStr) && !commandStr.trim().isEmpty()) {
            new CommandNotFoundException((String)CommandManager.expand(commandStr).getA()).handle(null, null);
         }
      }
   }

   private void logRanCommand(String command, String rest) {
      if (settings.echoCommands.value) {
         String msg = command + rest;
         String toDisplay = settings.censorRanCommands.value ? command + " ..." : msg;
         MutableComponent component = Component.literal(String.format("> %s", toDisplay));
         component.setStyle(
            component.getStyle()
               .withColor(ChatFormatting.WHITE)
               .withHoverEvent(new HoverEvent(Action.SHOW_TEXT, Component.literal("Click to rerun command")))
               .withClickEvent(new ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, IBaritoneChatControl.FORCE_COMMAND_PREFIX + msg))
         );
         this.logDirect(new Component[]{component});
      }
   }

   public boolean runCommand(String msg) {
      if (msg.trim().equalsIgnoreCase("damn")) {
         this.logDirect("daniel");
         return false;
      } else if (msg.trim().equalsIgnoreCase("orderpizza")) {
         try {
            Util.getPlatform().openUri("https://www.dominos.com/en/pages/order/");
         } catch (Exception var9) {
         }

         return false;
      } else if (msg.isEmpty()) {
         return this.runCommand("help");
      } else {
         Tuple<String, List<ICommandArgument>> pair = CommandManager.expand(msg);
         String command = (String)pair.getA();
         String rest = msg.substring(((String)pair.getA()).length());
         ArgConsumer argc = new ArgConsumer(this.manager, (List<ICommandArgument>)pair.getB());
         if (!argc.hasAny()) {
            Settings.Setting setting = settings.byLowerName.get(command.toLowerCase(Locale.US));
            if (setting != null) {
               this.logRanCommand(command, rest);
               if (setting.getValueClass() == Boolean.class) {
                  this.manager.execute(String.format("set toggle %s", setting.getName()));
               } else {
                  this.manager.execute(String.format("set %s", setting.getName()));
               }

               return true;
            }
         } else if (argc.hasExactlyOne()) {
            for (Settings.Setting setting : settings.allSettings) {
               if (!setting.isJavaOnly() && setting.getName().equalsIgnoreCase((String)pair.getA())) {
                  this.logRanCommand(command, rest);

                  try {
                     this.manager.execute(String.format("set %s %s", setting.getName(), argc.getString()));
                  } catch (CommandNotEnoughArgumentsException var10) {
                  }

                  return true;
               }
            }
         }

         if (this.manager.getCommand((String)pair.getA()) != null) {
            this.logRanCommand(command, rest);
         }

         return this.manager.execute(pair);
      }
   }

   @Override
   public void onPreTabComplete(TabCompleteEvent event) {
      if (settings.prefixControl.value) {
         String prefix = event.prefix;
         String commandPrefix = settings.prefix.value;
         if (prefix.startsWith(commandPrefix)) {
            String msg = prefix.substring(commandPrefix.length());
            List<ICommandArgument> args = CommandArguments.from(msg, true);
            Stream<String> stream = this.tabComplete(msg);
            if (args.size() == 1) {
               stream = stream.map(x -> commandPrefix + x);
            }

            event.completions = stream.toArray(String[]::new);
         }
      }
   }

   public Stream<String> tabComplete(String msg) {
      try {
         List<ICommandArgument> args = CommandArguments.from(msg, true);
         ArgConsumer argc = new ArgConsumer(this.manager, args);
         if (argc.hasAtMost(2)) {
            if (argc.hasExactly(1)) {
               return new TabCompleteHelper().addCommands(this.manager).addSettings().filterPrefix(argc.getString()).stream();
            }

            Settings.Setting setting = settings.byLowerName.get(argc.getString().toLowerCase(Locale.US));
            if (setting != null && !setting.isJavaOnly()) {
               if (setting.getValueClass() == Boolean.class) {
                  TabCompleteHelper helper = new TabCompleteHelper();
                  if ((Boolean)setting.value) {
                     helper.append("true", "false");
                  } else {
                     helper.append("false", "true");
                  }

                  return helper.filterPrefix(argc.getString()).stream();
               }

               return Stream.of(SettingsUtil.settingValueToString(setting));
            }
         }

         return this.manager.tabComplete(msg);
      } catch (CommandNotEnoughArgumentsException var6) {
         return Stream.empty();
      }
   }
}

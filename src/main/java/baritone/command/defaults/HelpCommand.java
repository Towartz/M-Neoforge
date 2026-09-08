package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.IBaritoneChatControl;
import baritone.api.command.ICommand;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandNotFoundException;
import baritone.api.command.helpers.Paginator;
import baritone.api.command.helpers.TabCompleteHelper;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;

public class HelpCommand extends Command {
   public HelpCommand(IBaritone baritone) {
      super(baritone, "help", "?");
   }

   @Override
   public void execute(String label, IArgConsumer args) throws CommandException {
      args.requireMax(1);
      if (args.hasAny() && !args.is(Integer.class)) {
         String commandName = args.getString().toLowerCase();
         ICommand command = this.baritone.getCommandManager().getCommand(commandName);
         if (command == null) {
            throw new CommandNotFoundException(commandName);
         }

         this.logDirect(String.format("%s - %s", String.join(" / ", command.getNames()), command.getShortDesc()));
         this.logDirect("");
         command.getLongDesc().forEach(this::logDirect);
         this.logDirect("");
         MutableComponent returnComponent = Component.literal("Click to return to the help menu");
         returnComponent.setStyle(
            returnComponent.getStyle().withClickEvent(new ClickEvent(Action.RUN_COMMAND, IBaritoneChatControl.FORCE_COMMAND_PREFIX + label))
         );
         this.logDirect(new Component[]{returnComponent});
      } else {
         Paginator.paginate(
            args,
            new Paginator<>(
               this.baritone.getCommandManager().getRegistry().descendingStream().filter(commandx -> !commandx.hiddenFromHelp()).collect(Collectors.toList())
            ),
            () -> this.logDirect("All Baritone commands (clickable):"),
            commandx -> {
               String names = String.join("/", commandx.getNames());
               String name = commandx.getNames().get(0);
               MutableComponent shortDescComponent = Component.literal(" - " + commandx.getShortDesc());
               shortDescComponent.setStyle(shortDescComponent.getStyle().withColor(ChatFormatting.DARK_GRAY));
               MutableComponent namesComponent = Component.literal(names);
               namesComponent.setStyle(namesComponent.getStyle().withColor(ChatFormatting.WHITE));
               MutableComponent hoverComponent = Component.literal("");
               hoverComponent.setStyle(hoverComponent.getStyle().withColor(ChatFormatting.GRAY));
               hoverComponent.append(namesComponent);
               hoverComponent.append("\n" + commandx.getShortDesc());
               hoverComponent.append("\n\nClick to view full help");
               String clickCommand = IBaritoneChatControl.FORCE_COMMAND_PREFIX + String.format("%s %s", label, commandx.getNames().get(0));
               MutableComponent component = Component.literal(name);
               component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));
               component.append(shortDescComponent);
               component.setStyle(
                  component.getStyle()
                     .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, hoverComponent))
                     .withClickEvent(new ClickEvent(Action.RUN_COMMAND, clickCommand))
               );
               return component;
            },
            IBaritoneChatControl.FORCE_COMMAND_PREFIX + label
         );
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      return args.hasExactlyOne()
         ? new TabCompleteHelper().addCommands(this.baritone.getCommandManager()).filterPrefix(args.getString()).stream()
         : Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "View all commands or help on specific ones";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "Using this command, you can view detailed help information on how to use certain commands of Baritone.",
         "",
         "Usage:",
         "> help - Lists all commands and their short descriptions.",
         "> help <command> - Displays help information on a specific command."
      );
   }
}

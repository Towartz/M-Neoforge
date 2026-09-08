package baritone.api.command;

import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.utils.Helper;
import java.util.List;
import java.util.stream.Stream;

public interface ICommand extends Helper {
   void execute(String var1, IArgConsumer var2) throws CommandException;

   Stream<String> tabComplete(String var1, IArgConsumer var2) throws CommandException;

   String getShortDesc();

   List<String> getLongDesc();

   List<String> getNames();

   default boolean hiddenFromHelp() {
      return false;
   }
}

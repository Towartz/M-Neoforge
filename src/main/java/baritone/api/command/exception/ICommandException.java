package baritone.api.command.exception;

import baritone.api.command.ICommand;
import baritone.api.command.argument.ICommandArgument;
import baritone.api.utils.Helper;
import java.util.List;
import net.minecraft.ChatFormatting;

public interface ICommandException {
   String getMessage();

   default void handle(ICommand command, List<ICommandArgument> args) {
      Helper.HELPER.logDirect(this.getMessage(), ChatFormatting.RED);
   }
}

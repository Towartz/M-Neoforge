package baritone.api.command.datatypes;

import baritone.api.command.exception.CommandException;
import baritone.api.command.helpers.TabCompleteHelper;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.core.Direction.Axis;

public enum ForAxis implements IDatatypeFor<Axis> {
   INSTANCE;

   public Axis get(IDatatypeContext ctx) throws CommandException {
      return Axis.valueOf(ctx.getConsumer().getString().toUpperCase(Locale.US));
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
      return new TabCompleteHelper()
         .append(Stream.of(Axis.values()).<String>map(Axis::getName).map(String::toLowerCase))
         .filterPrefix(ctx.getConsumer().getString())
         .stream();
   }
}

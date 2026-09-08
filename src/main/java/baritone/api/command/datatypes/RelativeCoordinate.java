package baritone.api.command.datatypes;

import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public enum RelativeCoordinate implements IDatatypePost<Double, Double> {
   INSTANCE;

   private static String ScalesAliasRegex = "[kKmM]";
   private static Pattern PATTERN = Pattern.compile("^(~?)([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(" + ScalesAliasRegex + "?)|)$");

   public Double apply(IDatatypeContext ctx, Double origin) throws CommandException {
      if (origin == null) {
         origin = 0.0;
      }

      Matcher matcher = PATTERN.matcher(ctx.getConsumer().getString());
      if (!matcher.matches()) {
         throw new IllegalArgumentException("pattern doesn't match");
      } else {
         boolean isRelative = !matcher.group(1).isEmpty();
         double offset = matcher.group(2).isEmpty() ? 0.0 : Double.parseDouble(matcher.group(2).replaceAll(ScalesAliasRegex, ""));
         if (matcher.group(2).toLowerCase().contains("k")) {
            offset *= 1000.0;
         }

         if (matcher.group(2).toLowerCase().contains("m")) {
            offset *= 1000000.0;
         }

         return isRelative ? origin + offset : offset;
      }
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
      IArgConsumer consumer = ctx.getConsumer();
      return !consumer.has(2) && consumer.getString().matches("^(~|$)") ? Stream.of("~") : Stream.empty();
   }
}

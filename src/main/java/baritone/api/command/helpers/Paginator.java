package baritone.api.command.helpers;

import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.utils.Helper;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;

public class Paginator<E> implements Helper {
   public final List<E> entries;
   public int pageSize = 8;
   public int page = 1;

   public Paginator(List<E> entries) {
      this.entries = entries;
   }

   public Paginator(E... entries) {
      this.entries = Arrays.asList(entries);
   }

   public Paginator<E> setPageSize(int pageSize) {
      this.pageSize = pageSize;
      return this;
   }

   public int getMaxPage() {
      return (this.entries.size() - 1) / this.pageSize + 1;
   }

   public boolean validPage(int page) {
      return page > 0 && page <= this.getMaxPage();
   }

   public Paginator<E> skipPages(int pages) {
      this.page += pages;
      return this;
   }

   public void display(Function<E, Component> transform, String commandPrefix) {
      int offset = (this.page - 1) * this.pageSize;

      for (int i = offset; i < offset + this.pageSize; i++) {
         if (i < this.entries.size()) {
            this.logDirect(new Component[]{transform.apply(this.entries.get(i))});
         } else {
            this.logDirect("--", ChatFormatting.DARK_GRAY);
         }
      }

      boolean hasPrevPage = commandPrefix != null && this.validPage(this.page - 1);
      boolean hasNextPage = commandPrefix != null && this.validPage(this.page + 1);
      MutableComponent prevPageComponent = Component.literal("<<");
      if (hasPrevPage) {
         prevPageComponent.setStyle(
            prevPageComponent.getStyle()
               .withClickEvent(new ClickEvent(Action.RUN_COMMAND, String.format("%s %d", commandPrefix, this.page - 1)))
               .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to view previous page")))
         );
      } else {
         prevPageComponent.setStyle(prevPageComponent.getStyle().withColor(ChatFormatting.DARK_GRAY));
      }

      MutableComponent nextPageComponent = Component.literal(">>");
      if (hasNextPage) {
         nextPageComponent.setStyle(
            nextPageComponent.getStyle()
               .withClickEvent(new ClickEvent(Action.RUN_COMMAND, String.format("%s %d", commandPrefix, this.page + 1)))
               .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to view next page")))
         );
      } else {
         nextPageComponent.setStyle(nextPageComponent.getStyle().withColor(ChatFormatting.DARK_GRAY));
      }

      MutableComponent pagerComponent = Component.literal("");
      pagerComponent.setStyle(pagerComponent.getStyle().withColor(ChatFormatting.GRAY));
      pagerComponent.append(prevPageComponent);
      pagerComponent.append(" | ");
      pagerComponent.append(nextPageComponent);
      pagerComponent.append(String.format(" %d/%d", this.page, this.getMaxPage()));
      this.logDirect(new Component[]{pagerComponent});
   }

   public void display(Function<E, Component> transform) {
      this.display(transform, null);
   }

   public static <T> void paginate(IArgConsumer consumer, Paginator<T> pagi, Runnable pre, Function<T, Component> transform, String commandPrefix) throws CommandException {
      int page = 1;
      consumer.requireMax(1);
      if (consumer.hasAny()) {
         page = consumer.getAs(Integer.class);
         if (!pagi.validPage(page)) {
            throw new CommandInvalidTypeException(consumer.consumed(), String.format("a valid page (1-%d)", pagi.getMaxPage()), consumer.consumed().getValue());
         }
      }

      pagi.skipPages(page - pagi.page);
      if (pre != null) {
         pre.run();
      }

      pagi.display(transform, commandPrefix);
   }

   public static <T> void paginate(IArgConsumer consumer, List<T> elems, Runnable pre, Function<T, Component> transform, String commandPrefix) throws CommandException {
      paginate(consumer, new Paginator<>(elems), pre, transform, commandPrefix);
   }

   public static <T> void paginate(IArgConsumer consumer, T[] elems, Runnable pre, Function<T, Component> transform, String commandPrefix) throws CommandException {
      paginate(consumer, Arrays.asList(elems), pre, transform, commandPrefix);
   }

   public static <T> void paginate(IArgConsumer consumer, Paginator<T> pagi, Function<T, Component> transform, String commandPrefix) throws CommandException {
      paginate(consumer, pagi, null, transform, commandPrefix);
   }

   public static <T> void paginate(IArgConsumer consumer, List<T> elems, Function<T, Component> transform, String commandPrefix) throws CommandException {
      paginate(consumer, new Paginator<>(elems), null, transform, commandPrefix);
   }

   public static <T> void paginate(IArgConsumer consumer, T[] elems, Function<T, Component> transform, String commandPrefix) throws CommandException {
      paginate(consumer, Arrays.asList(elems), null, transform, commandPrefix);
   }

   public static <T> void paginate(IArgConsumer consumer, Paginator<T> pagi, Runnable pre, Function<T, Component> transform) throws CommandException {
      paginate(consumer, pagi, pre, transform, null);
   }

   public static <T> void paginate(IArgConsumer consumer, List<T> elems, Runnable pre, Function<T, Component> transform) throws CommandException {
      paginate(consumer, new Paginator<>(elems), pre, transform, null);
   }

   public static <T> void paginate(IArgConsumer consumer, T[] elems, Runnable pre, Function<T, Component> transform) throws CommandException {
      paginate(consumer, Arrays.asList(elems), pre, transform, null);
   }

   public static <T> void paginate(IArgConsumer consumer, Paginator<T> pagi, Function<T, Component> transform) throws CommandException {
      paginate(consumer, pagi, null, transform, null);
   }

   public static <T> void paginate(IArgConsumer consumer, List<T> elems, Function<T, Component> transform) throws CommandException {
      paginate(consumer, new Paginator<>(elems), null, transform, null);
   }

   public static <T> void paginate(IArgConsumer consumer, T[] elems, Function<T, Component> transform) throws CommandException {
      paginate(consumer, Arrays.asList(elems), null, transform, null);
   }
}

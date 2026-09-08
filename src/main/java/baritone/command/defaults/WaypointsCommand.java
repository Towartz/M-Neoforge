package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.cache.IWaypoint;
import baritone.api.cache.IWorldData;
import baritone.api.cache.Waypoint;
import baritone.api.command.Command;
import baritone.api.command.IBaritoneChatControl;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.ForWaypoints;
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.helpers.Paginator;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class WaypointsCommand extends Command {
   private Map<IWorldData, List<IWaypoint>> deletedWaypoints = new HashMap<>();

   public WaypointsCommand(IBaritone baritone) {
      super(baritone, "waypoints", "waypoint", "wp");
   }

   @Override
   public void execute(String label, IArgConsumer args) throws CommandException {
      WaypointsCommand.Action action = args.hasAny() ? WaypointsCommand.Action.getByName(args.getString()) : WaypointsCommand.Action.LIST;
      if (action == null) {
         throw new CommandInvalidTypeException(args.consumed(), "an action");
      } else {
         BiFunction<IWaypoint, WaypointsCommand.Action, Component> toComponent = (waypointx, _action) -> {
            MutableComponent component = Component.literal("");
            MutableComponent tagComponent = Component.literal(waypointx.getTag().name() + " ");
            tagComponent.setStyle(tagComponent.getStyle().withColor(ChatFormatting.GRAY));
            String name = waypointx.getName();
            MutableComponent nameComponent = Component.literal(!name.isEmpty() ? name : "<empty>");
            nameComponent.setStyle(nameComponent.getStyle().withColor(!name.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
            MutableComponent timestamp = Component.literal(" @ " + new Date(waypointx.getCreationTimestamp()));
            timestamp.setStyle(timestamp.getStyle().withColor(ChatFormatting.DARK_GRAY));
            component.append(tagComponent);
            component.append(nameComponent);
            component.append(timestamp);
            component.setStyle(
               component.getStyle()
                  .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to select")))
                  .withClickEvent(
                     new ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                        String.format(
                           "%s%s %s %s @ %d",
                           IBaritoneChatControl.FORCE_COMMAND_PREFIX,
                           label,
                           _action.names[0],
                           waypointx.getTag().getName(),
                           waypointx.getCreationTimestamp()
                        )
                     )
                  )
            );
            return component;
         };
         Function<IWaypoint, Component> transform = waypointx -> toComponent.apply(
               waypointx, action == WaypointsCommand.Action.LIST ? WaypointsCommand.Action.INFO : action
            );
         if (action == WaypointsCommand.Action.LIST) {
            IWaypoint.Tag tag = args.hasAny() ? IWaypoint.Tag.getByName(args.peekString()) : null;
            if (tag != null) {
               args.get();
            }

            IWaypoint[] waypoints = tag != null ? ForWaypoints.getWaypointsByTag(this.baritone, tag) : ForWaypoints.getWaypoints(this.baritone);
            if (waypoints.length <= 0) {
               args.requireMax(0);
               throw new CommandInvalidStateException(tag != null ? "No waypoints found by that tag" : "No waypoints found");
            }

            args.requireMax(1);
            Paginator.paginate(
               args,
               waypoints,
               () -> this.logDirect(tag != null ? String.format("All waypoints by tag %s:", tag.name()) : "All waypoints:"),
               transform,
               String.format("%s%s %s%s", IBaritoneChatControl.FORCE_COMMAND_PREFIX, label, action.names[0], tag != null ? " " + tag.getName() : "")
            );
         } else if (action == WaypointsCommand.Action.SAVE) {
            IWaypoint.Tag tagx = args.hasAny() ? IWaypoint.Tag.getByName(args.peekString()) : null;
            if (tagx == null) {
               tagx = IWaypoint.Tag.USER;
            } else {
               args.get();
            }

            String name = !args.hasExactlyOne() && !args.hasExactly(4) ? "" : args.getString();
            BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, this.ctx.playerFeet()) : this.ctx.playerFeet();
            args.requireMax(0);
            IWaypoint waypoint = new Waypoint(name, tagx, pos);
            ForWaypoints.waypoints(this.baritone).addWaypoint(waypoint);
            MutableComponent component = Component.literal("Waypoint added: ");
            component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));
            component.append(toComponent.apply(waypoint, WaypointsCommand.Action.INFO));
            this.logDirect(new Component[]{component});
         } else if (action == WaypointsCommand.Action.CLEAR) {
            args.requireMax(1);
            String name = args.getString();
            IWaypoint.Tag tagx = IWaypoint.Tag.getByName(name);
            if (tagx == null) {
               throw new CommandInvalidStateException("Invalid tag, \"" + name + "\"");
            }

            IWaypoint[] waypoints = ForWaypoints.getWaypointsByTag(this.baritone, tagx);

            for (IWaypoint waypoint : waypoints) {
               ForWaypoints.waypoints(this.baritone).removeWaypoint(waypoint);
            }

            this.deletedWaypoints.computeIfAbsent(this.baritone.getWorldProvider().getCurrentWorld(), k -> new ArrayList<>()).addAll(Arrays.asList(waypoints));
            MutableComponent textComponent = Component.literal(String.format("Cleared %d waypoints, click to restore them", waypoints.length));
            textComponent.setStyle(
               textComponent.getStyle()
                  .withClickEvent(
                     new ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                        String.format(
                           "%s%s restore @ %s",
                           IBaritoneChatControl.FORCE_COMMAND_PREFIX,
                           label,
                           Stream.of(waypoints).map(wp -> Long.toString(wp.getCreationTimestamp())).collect(Collectors.joining(" "))
                        )
                     )
                  )
            );
            this.logDirect(new Component[]{textComponent});
         } else if (action == WaypointsCommand.Action.RESTORE) {
            List<IWaypoint> waypoints = new ArrayList<>();
            List<IWaypoint> deletedWaypoints = this.deletedWaypoints.getOrDefault(this.baritone.getWorldProvider().getCurrentWorld(), Collections.emptyList());
            if (!args.peekString().equals("@")) {
               args.requireExactly(1);
               int size = deletedWaypoints.size();
               int amount = Math.min(size, args.getAs(Integer.class));
               waypoints = new ArrayList<>(deletedWaypoints.subList(size - amount, size));
            } else {
               args.get();

               while (args.hasAny()) {
                  long timestamp = args.getAs(Long.class);

                  for (IWaypoint waypoint : deletedWaypoints) {
                     if (waypoint.getCreationTimestamp() == timestamp) {
                        waypoints.add(waypoint);
                        break;
                     }
                  }
               }
            }

            waypoints.forEach(ForWaypoints.waypoints(this.baritone)::addWaypoint);
            deletedWaypoints.removeIf(waypoints::contains);
            this.logDirect(String.format("Restored %d waypoints", waypoints.size()));
         } else {
            IWaypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            IWaypoint waypointx = null;
            if (args.hasAny() && args.peekString().equals("@")) {
               args.requireExactly(2);
               args.get();
               long timestamp = args.getAs(Long.class);

               for (IWaypoint iWaypoint : waypoints) {
                  if (iWaypoint.getCreationTimestamp() == timestamp) {
                     waypointx = iWaypoint;
                     break;
                  }
               }

               if (waypointx == null) {
                  throw new CommandInvalidStateException("Timestamp was specified but no waypoint was found");
               }
            } else {
               switch (waypoints.length) {
                  case 0:
                     throw new CommandInvalidStateException("No waypoints found");
                  case 1:
                     waypointx = waypoints[0];
               }
            }

            if (waypointx == null) {
               args.requireMax(1);
               Paginator.paginate(
                  args,
                  waypoints,
                  () -> this.logDirect("Multiple waypoints were found:"),
                  transform,
                  String.format("%s%s %s %s", IBaritoneChatControl.FORCE_COMMAND_PREFIX, label, action.names[0], args.consumedString())
               );
            } else if (action == WaypointsCommand.Action.INFO) {
               this.logDirect(new Component[]{transform.apply(waypointx)});
               this.logDirect(String.format("Position: %s", waypointx.getLocation()));
               MutableComponent deleteComponent = Component.literal("Click to delete this waypoint");
               deleteComponent.setStyle(
                  deleteComponent.getStyle()
                     .withClickEvent(
                        new ClickEvent(
                           net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                           String.format(
                              "%s%s delete %s @ %d",
                              IBaritoneChatControl.FORCE_COMMAND_PREFIX,
                              label,
                              waypointx.getTag().getName(),
                              waypointx.getCreationTimestamp()
                           )
                        )
                     )
               );
               MutableComponent goalComponent = Component.literal("Click to set goal to this waypoint");
               goalComponent.setStyle(
                  goalComponent.getStyle()
                     .withClickEvent(
                        new ClickEvent(
                           net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                           String.format(
                              "%s%s goal %s @ %d",
                              IBaritoneChatControl.FORCE_COMMAND_PREFIX,
                              label,
                              waypointx.getTag().getName(),
                              waypointx.getCreationTimestamp()
                           )
                        )
                     )
               );
               MutableComponent recreateComponent = Component.literal("Click to show a command to recreate this waypoint");
               recreateComponent.setStyle(
                  recreateComponent.getStyle()
                     .withClickEvent(
                        new ClickEvent(
                           net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
                           String.format(
                              "%s%s save %s %s %s %s %s",
                              Baritone.settings().prefix.value,
                              label,
                              waypointx.getTag().getName(),
                              waypointx.getName(),
                              waypointx.getLocation().x,
                              waypointx.getLocation().y,
                              waypointx.getLocation().z
                           )
                        )
                     )
               );
               MutableComponent backComponent = Component.literal("Click to return to the waypoints list");
               backComponent.setStyle(
                  backComponent.getStyle()
                     .withClickEvent(
                        new ClickEvent(
                           net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                           String.format("%s%s list", IBaritoneChatControl.FORCE_COMMAND_PREFIX, label)
                        )
                     )
               );
               this.logDirect(new Component[]{deleteComponent});
               this.logDirect(new Component[]{goalComponent});
               this.logDirect(new Component[]{recreateComponent});
               this.logDirect(new Component[]{backComponent});
            } else if (action == WaypointsCommand.Action.DELETE) {
               ForWaypoints.waypoints(this.baritone).removeWaypoint(waypointx);
               this.deletedWaypoints.computeIfAbsent(this.baritone.getWorldProvider().getCurrentWorld(), k -> new ArrayList<>()).add(waypointx);
               MutableComponent textComponent = Component.literal("That waypoint has successfully been deleted, click to restore it");
               textComponent.setStyle(
                  textComponent.getStyle()
                     .withClickEvent(
                        new ClickEvent(
                           net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                           String.format("%s%s restore @ %s", IBaritoneChatControl.FORCE_COMMAND_PREFIX, label, waypointx.getCreationTimestamp())
                        )
                     )
               );
               this.logDirect(new Component[]{textComponent});
            } else if (action == WaypointsCommand.Action.GOAL) {
               Goal goal = new GoalBlock(waypointx.getLocation());
               this.baritone.getCustomGoalProcess().setGoal(goal);
               this.logDirect(String.format("Goal: %s", goal));
            } else if (action == WaypointsCommand.Action.GOTO) {
               Goal goal = new GoalBlock(waypointx.getLocation());
               this.baritone.getCustomGoalProcess().setGoalAndPath(goal);
               this.logDirect(String.format("Going to: %s", goal));
            }
         }
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      if (args.hasAny()) {
         if (args.hasExactlyOne()) {
            return new TabCompleteHelper().append(WaypointsCommand.Action.getAllNames()).sortAlphabetically().filterPrefix(args.getString()).stream();
         }

         WaypointsCommand.Action action = WaypointsCommand.Action.getByName(args.getString());
         if (args.hasExactlyOne()) {
            if (action != WaypointsCommand.Action.LIST && action != WaypointsCommand.Action.SAVE && action != WaypointsCommand.Action.CLEAR) {
               if (action == WaypointsCommand.Action.RESTORE) {
                  return Stream.empty();
               }

               return args.tabCompleteDatatype(ForWaypoints.INSTANCE);
            }

            return new TabCompleteHelper().append(IWaypoint.Tag.getAllNames()).sortAlphabetically().filterPrefix(args.getString()).stream();
         }

         if (args.has(3) && action == WaypointsCommand.Action.SAVE) {
            args.get();
            args.get();
            return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
         }
      }

      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Manage waypoints";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The waypoint command allows you to manage Baritone's waypoints.",
         "",
         "Waypoints can be used to mark positions for later. Waypoints are each given a tag and an optional name.",
         "",
         "Note that the info, delete, and goal commands let you specify a waypoint by tag. If there is more than one waypoint with a certain tag, then they will let you select which waypoint you mean.",
         "",
         "Missing arguments for the save command use the USER tag, creating an unnamed waypoint and your current position as defaults.",
         "",
         "Usage:",
         "> wp [l/list] - List all waypoints.",
         "> wp <l/list> <tag> - List all waypoints by tag.",
         "> wp <s/save> - Save an unnamed USER waypoint at your current position",
         "> wp <s/save> [tag] [name] [pos] - Save a waypoint with the specified tag, name and position.",
         "> wp <i/info/show> <tag/name> - Show info on a waypoint by tag or name.",
         "> wp <d/delete> <tag/name> - Delete a waypoint by tag or name.",
         "> wp <restore> <n> - Restore the last n deleted waypoints.",
         "> wp <c/clear> <tag> - Delete all waypoints with the specified tag.",
         "> wp <g/goal> <tag/name> - Set a goal to a waypoint by tag or name.",
         "> wp <goto> <tag/name> - Set a goal to a waypoint by tag or name and start pathing."
      );
   }

   private static enum Action {
      LIST("list", "get", "l"),
      CLEAR("clear", "c"),
      SAVE("save", "s"),
      INFO("info", "show", "i"),
      DELETE("delete", "d"),
      RESTORE("restore"),
      GOAL("goal", "g"),
      GOTO("goto");

      private final String[] names;

      private Action(String... names) {
         this.names = names;
      }

      public static WaypointsCommand.Action getByName(String name) {
         for (WaypointsCommand.Action action : values()) {
            for (String alias : action.names) {
               if (alias.equalsIgnoreCase(name)) {
                  return action;
               }
            }
         }

         return null;
      }

      public static String[] getAllNames() {
         Set<String> names = new HashSet<>();

         for (WaypointsCommand.Action action : values()) {
            names.addAll(Arrays.asList(action.names));
         }

         return names.toArray(new String[0]);
      }
   }
}

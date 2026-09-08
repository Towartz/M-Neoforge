package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.ForAxis;
import baritone.api.command.datatypes.ForBlockOptionalMeta;
import baritone.api.command.datatypes.ForDirection;
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.schematic.CompositeSchematic;
import baritone.api.schematic.FillSchematic;
import baritone.api.schematic.ISchematic;
import baritone.api.schematic.MaskSchematic;
import baritone.api.schematic.ReplaceSchematic;
import baritone.api.schematic.ShellSchematic;
import baritone.api.schematic.WallsSchematic;
import baritone.api.schematic.mask.shape.CylinderMask;
import baritone.api.schematic.mask.shape.SphereMask;
import baritone.api.selection.ISelection;
import baritone.api.selection.ISelectionManager;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.utils.BlockStateInterface;
import baritone.utils.IRenderer;
import baritone.utils.schematic.StaticSchematic;
import com.mojang.blaze3d.vertex.BufferBuilder;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class SelCommand extends Command {
   private ISelectionManager manager = this.baritone.getSelectionManager();
   private BetterBlockPos pos1 = null;
   private ISchematic clipboard = null;
   private Vec3i clipboardOffset = null;

   public SelCommand(IBaritone baritone) {
      super(baritone, "sel", "selection", "s");
      baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
         @Override
         public void onRenderPass(RenderEvent event) {
            if (Baritone.settings().renderSelectionCorners.value && SelCommand.this.pos1 != null) {
               Color color = Baritone.settings().colorSelectionPos1.value;
               float opacity = Baritone.settings().selectionOpacity.value;
               float lineWidth = Baritone.settings().selectionLineWidth.value;
               boolean ignoreDepth = Baritone.settings().renderSelectionIgnoreDepth.value;
               BufferBuilder bufferBuilder = IRenderer.startLines(color, opacity, lineWidth, ignoreDepth);
               IRenderer.emitAABB(bufferBuilder, event.getModelViewStack(), new AABB(SelCommand.this.pos1));
               IRenderer.endLines(bufferBuilder, ignoreDepth);
            }
         }
      });
   }

   @Override
   public void execute(String label, IArgConsumer args) throws CommandException {
      SelCommand.Action action = SelCommand.Action.getByName(args.getString());
      if (action == null) {
         throw new CommandInvalidTypeException(args.consumed(), "an action");
      } else {
         if (action != SelCommand.Action.POS1 && action != SelCommand.Action.POS2) {
            if (action == SelCommand.Action.CLEAR) {
               args.requireMax(0);
               this.pos1 = null;
               this.logDirect(String.format("Removed %d selections", this.manager.removeAllSelections().length));
            } else if (action == SelCommand.Action.UNDO) {
               args.requireMax(0);
               if (this.pos1 != null) {
                  this.pos1 = null;
                  this.logDirect("Undid pos1");
               } else {
                  ISelection[] selections = this.manager.getSelections();
                  if (selections.length < 1) {
                     throw new CommandInvalidStateException("Nothing to undo!");
                  }

                  this.pos1 = this.manager.removeSelection(selections[selections.length - 1]).pos1();
                  this.logDirect("Undid pos2");
               }
            } else if (action.isFillAction()) {
               BlockOptionalMeta type = action == SelCommand.Action.CLEARAREA
                  ? new BlockOptionalMeta(Blocks.AIR)
                  : args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
               Axis alignment;
               BlockOptionalMetaLookup replaces;
               if (action != SelCommand.Action.REPLACE) {
                  if (action != SelCommand.Action.CYLINDER && action != SelCommand.Action.HCYLINDER) {
                     args.requireMax(0);
                     replaces = null;
                     alignment = null;
                  } else {
                     args.requireMax(1);
                     alignment = args.hasAny() ? args.getDatatypeFor(ForAxis.INSTANCE) : Axis.Y;
                     replaces = null;
                  }
               } else {
                  args.requireMin(1);
                  List<BlockOptionalMeta> replacesList = new ArrayList<>();
                  replacesList.add(type);

                  while (args.has(2)) {
                     replacesList.add(args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE));
                  }

                  type = args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
                  replaces = new BlockOptionalMetaLookup(replacesList.toArray(new BlockOptionalMeta[0]));
                  alignment = null;
               }

               ISelection[] selections = this.manager.getSelections();
               if (selections.length == 0) {
                  throw new CommandInvalidStateException("No selections");
               }

               BetterBlockPos origin = selections[0].min();
               CompositeSchematic composite = new CompositeSchematic(0, 0, 0);

               for (ISelection selection : selections) {
                  BetterBlockPos min = selection.min();
                  origin = new BetterBlockPos(Math.min(origin.x, min.x), Math.min(origin.y, min.y), Math.min(origin.z, min.z));
               }

               for (ISelection selection : selections) {
                  Vec3i size = selection.size();
                  BetterBlockPos min = selection.min();
                  UnaryOperator<ISchematic> create = fill -> {
                     int w = fill.widthX();
                     int h = fill.heightY();
                     int l = fill.lengthZ();
                     switch (action) {
                        case WALLS:
                           return new WallsSchematic(fill);
                        case SHELL:
                           return new ShellSchematic(fill);
                        case SPHERE:
                           return MaskSchematic.create(fill, new SphereMask(w, h, l, true).compute());
                        case HSPHERE:
                           return MaskSchematic.create(fill, new SphereMask(w, h, l, false).compute());
                        case CYLINDER:
                           return MaskSchematic.create(fill, new CylinderMask(w, h, l, true, alignment).compute());
                        case HCYLINDER:
                           return MaskSchematic.create(fill, new CylinderMask(w, h, l, false, alignment).compute());
                        case CLEARAREA:
                        default:
                           return fill;
                        case REPLACE:
                           return new ReplaceSchematic(fill, replaces);
                     }
                  };
                  ISchematic schematic = create.apply(new FillSchematic(size.getX(), size.getY(), size.getZ(), type));
                  composite.put(schematic, min.x - origin.x, min.y - origin.y, min.z - origin.z);
               }

               this.baritone.getBuilderProcess().build("Fill", composite, origin);
               this.logDirect("Filling now");
            } else if (action == SelCommand.Action.COPY) {
               BetterBlockPos playerPos = this.ctx.viewerPos();
               BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
               args.requireMax(0);
               ISelection[] selections = this.manager.getSelections();
               if (selections.length < 1) {
                  throw new CommandInvalidStateException("No selections");
               }

               BlockStateInterface bsi = new BlockStateInterface(this.ctx);
               BetterBlockPos origin = selections[0].min();
               CompositeSchematic composite = new CompositeSchematic(0, 0, 0);

               for (ISelection selection : selections) {
                  BetterBlockPos min = selection.min();
                  origin = new BetterBlockPos(Math.min(origin.x, min.x), Math.min(origin.y, min.y), Math.min(origin.z, min.z));
               }

               for (ISelection selection : selections) {
                  Vec3i size = selection.size();
                  BetterBlockPos min = selection.min();
                  BlockState[][][] blockstates = new BlockState[size.getX()][size.getZ()][size.getY()];

                  for (int x = 0; x < size.getX(); x++) {
                     for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                           blockstates[x][z][y] = bsi.get0(min.x + x, min.y + y, min.z + z);
                        }
                     }
                  }

                  ISchematic schematic = new StaticSchematic(blockstates);
                  composite.put(schematic, min.x - origin.x, min.y - origin.y, min.z - origin.z);
               }

               this.clipboard = composite;
               this.clipboardOffset = origin.subtract(pos);
               this.logDirect("Selection copied");
            } else if (action == SelCommand.Action.PASTE) {
               BetterBlockPos playerPos = this.ctx.viewerPos();
               BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
               args.requireMax(0);
               if (this.clipboard == null) {
                  throw new CommandInvalidStateException("You need to copy a selection first");
               }

               this.baritone.getBuilderProcess().build("Fill", this.clipboard, pos.offset(this.clipboardOffset));
               this.logDirect("Building now");
            } else if (action == SelCommand.Action.EXPAND || action == SelCommand.Action.CONTRACT || action == SelCommand.Action.SHIFT) {
               args.requireExactly(3);
               SelCommand.TransformTarget transformTarget = SelCommand.TransformTarget.getByName(args.getString());
               if (transformTarget == null) {
                  throw new CommandInvalidStateException("Invalid transform type");
               }

               Direction direction = args.getDatatypeFor(ForDirection.INSTANCE);
               int blocks = args.getAs(Integer.class);
               ISelection[] selections = this.manager.getSelections();
               if (selections.length < 1) {
                  throw new CommandInvalidStateException("No selections found");
               }

               selections = transformTarget.transform(selections);

               for (ISelection selection : selections) {
                  if (action == SelCommand.Action.EXPAND) {
                     this.manager.expand(selection, direction, blocks);
                  } else if (action == SelCommand.Action.CONTRACT) {
                     this.manager.contract(selection, direction, blocks);
                  } else {
                     this.manager.shift(selection, direction, blocks);
                  }
               }

               this.logDirect(String.format("Transformed %d selections", selections.length));
            }
         } else {
            if (action == SelCommand.Action.POS2 && this.pos1 == null) {
               throw new CommandInvalidStateException("Set pos1 first before using pos2");
            }

            BetterBlockPos playerPos = this.ctx.viewerPos();
            BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
            args.requireMax(0);
            if (action == SelCommand.Action.POS1) {
               this.pos1 = pos;
               this.logDirect("Position 1 has been set");
            } else {
               this.manager.addSelection(this.pos1, pos);
               this.pos1 = null;
               this.logDirect("Selection added");
            }
         }
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      if (args.hasExactlyOne()) {
         return new TabCompleteHelper().append(SelCommand.Action.getAllNames()).filterPrefix(args.getString()).sortAlphabetically().stream();
      } else {
         SelCommand.Action action = SelCommand.Action.getByName(args.getString());
         if (action != null) {
            if (action != SelCommand.Action.POS1 && action != SelCommand.Action.POS2) {
               if (action.isFillAction()) {
                  if (args.hasExactlyOne() || action == SelCommand.Action.REPLACE) {
                     while (args.has(2)) {
                        args.get();
                     }

                     return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
                  }

                  if (args.hasExactly(2) && (action == SelCommand.Action.CYLINDER || action == SelCommand.Action.HCYLINDER)) {
                     args.get();
                     return args.tabCompleteDatatype(ForAxis.INSTANCE);
                  }
               } else if (action == SelCommand.Action.EXPAND || action == SelCommand.Action.CONTRACT || action == SelCommand.Action.SHIFT) {
                  if (args.hasExactlyOne()) {
                     return new TabCompleteHelper()
                        .append(SelCommand.TransformTarget.getAllNames())
                        .filterPrefix(args.getString())
                        .sortAlphabetically()
                        .stream();
                  }

                  SelCommand.TransformTarget target = SelCommand.TransformTarget.getByName(args.getString());
                  if (target != null && args.hasExactlyOne()) {
                     return args.tabCompleteDatatype(ForDirection.INSTANCE);
                  }
               }
            } else if (args.hasAtMost(3)) {
               return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
            }
         }

         return Stream.empty();
      }
   }

   @Override
   public String getShortDesc() {
      return "WorldEdit-like commands";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The sel command allows you to manipulate Baritone's selections, similarly to WorldEdit.",
         "",
         "Using these selections, you can clear areas, fill them with blocks, or something else.",
         "",
         "The expand/contract/shift commands use a kind of selector to choose which selections to target. Supported ones are a/all, n/newest, and o/oldest.",
         "",
         "Usage:",
         "> sel pos1/p1/1 - Set position 1 to your current position.",
         "> sel pos1/p1/1 <x> <y> <z> - Set position 1 to a relative position.",
         "> sel pos2/p2/2 - Set position 2 to your current position.",
         "> sel pos2/p2/2 <x> <y> <z> - Set position 2 to a relative position.",
         "",
         "> sel clear/c - Clear the selection.",
         "> sel undo/u - Undo the last action (setting positions, creating selections, etc.)",
         "> sel set/fill/s/f [block] - Completely fill all selections with a block.",
         "> sel walls/w [block] - Fill in the walls of the selection with a specified block.",
         "> sel shell/shl [block] - The same as walls, but fills in a ceiling and floor too.",
         "> sel sphere/sph [block] - Fills the selection with a sphere bounded by the sides.",
         "> sel hsphere/hsph [block] - The same as sphere, but hollow.",
         "> sel cylinder/cyl [block] <axis> - Fills the selection with a cylinder bounded by the sides, oriented about the given axis. (default=y)",
         "> sel hcylinder/hcyl [block] <axis> - The same as cylinder, but hollow.",
         "> sel cleararea/ca - Basically 'set air'.",
         "> sel replace/r <blocks...> <with> - Replaces blocks with another block.",
         "> sel copy/cp <x> <y> <z> - Copy the selected area relative to the specified or your position.",
         "> sel paste/p <x> <y> <z> - Build the copied area relative to the specified or your position.",
         "",
         "> sel expand <target> <direction> <blocks> - Expand the targets.",
         "> sel contract <target> <direction> <blocks> - Contract the targets.",
         "> sel shift <target> <direction> <blocks> - Shift the targets (does not resize)."
      );
   }

   static enum Action {
      POS1("pos1", "p1", "1"),
      POS2("pos2", "p2", "2"),
      CLEAR("clear", "c"),
      UNDO("undo", "u"),
      SET("set", "fill", "s", "f"),
      WALLS("walls", "w"),
      SHELL("shell", "shl"),
      SPHERE("sphere", "sph"),
      HSPHERE("hsphere", "hsph"),
      CYLINDER("cylinder", "cyl"),
      HCYLINDER("hcylinder", "hcyl"),
      CLEARAREA("cleararea", "ca"),
      REPLACE("replace", "r"),
      EXPAND("expand", "ex"),
      COPY("copy", "cp"),
      PASTE("paste", "p"),
      CONTRACT("contract", "ct"),
      SHIFT("shift", "sh");

      private final String[] names;

      private Action(String... names) {
         this.names = names;
      }

      public static SelCommand.Action getByName(String name) {
         for (SelCommand.Action action : values()) {
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

         for (SelCommand.Action action : values()) {
            names.addAll(Arrays.asList(action.names));
         }

         return names.toArray(new String[0]);
      }

      public final boolean isFillAction() {
         return this == SET
            || this == WALLS
            || this == SHELL
            || this == SPHERE
            || this == HSPHERE
            || this == CYLINDER
            || this == HCYLINDER
            || this == CLEARAREA
            || this == REPLACE;
      }
   }

   static enum TransformTarget {
      ALL(sels -> sels, "all", "a"),
      NEWEST(sels -> new ISelection[]{sels[sels.length - 1]}, "newest", "n"),
      OLDEST(sels -> new ISelection[]{sels[0]}, "oldest", "o");

      private final Function<ISelection[], ISelection[]> transform;
      private final String[] names;

      private TransformTarget(Function<ISelection[], ISelection[]> transform, String... names) {
         this.transform = transform;
         this.names = names;
      }

      public ISelection[] transform(ISelection[] selections) {
         return this.transform.apply(selections);
      }

      public static SelCommand.TransformTarget getByName(String name) {
         for (SelCommand.TransformTarget target : values()) {
            for (String alias : target.names) {
               if (alias.equalsIgnoreCase(name)) {
                  return target;
               }
            }
         }

         return null;
      }

      public static String[] getAllNames() {
         Set<String> names = new HashSet<>();

         for (SelCommand.TransformTarget target : values()) {
            names.addAll(Arrays.asList(target.names));
         }

         return names.toArray(new String[0]);
      }
   }
}

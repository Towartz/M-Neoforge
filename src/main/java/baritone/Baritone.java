package baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.behavior.IBehavior;
import baritone.api.event.listener.IEventBus;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.IElytraProcess;
import baritone.api.utils.IPlayerContext;
import baritone.behavior.InventoryBehavior;
import baritone.behavior.LookBehavior;
import baritone.behavior.PathingBehavior;
import baritone.behavior.WaypointBehavior;
import baritone.cache.WorldProvider;
import baritone.command.manager.CommandManager;
import baritone.event.GameEventHandler;
import baritone.process.BackfillProcess;
import baritone.process.BuilderProcess;
import baritone.process.CustomGoalProcess;
import baritone.process.ElytraProcess;
import baritone.process.ExploreProcess;
import baritone.process.FarmProcess;
import baritone.process.FollowProcess;
import baritone.process.GetToBlockProcess;
import baritone.process.InventoryPauserProcess;
import baritone.process.MineProcess;
import baritone.selection.SelectionManager;
import baritone.utils.BlockStateInterface;
import baritone.utils.GuiClick;
import baritone.utils.InputOverrideHandler;
import baritone.utils.PathingControlManager;
import baritone.utils.player.BaritonePlayerContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.minecraft.client.Minecraft;

public class Baritone implements IBaritone {
   private static final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(4, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
   private final Minecraft mc;
   private final Path directory;
   private final GameEventHandler gameEventHandler;
   private final PathingBehavior pathingBehavior;
   private final LookBehavior lookBehavior;
   private final InventoryBehavior inventoryBehavior;
   private final InputOverrideHandler inputOverrideHandler;
   private final FollowProcess followProcess;
   private final MineProcess mineProcess;
   private final GetToBlockProcess getToBlockProcess;
   private final CustomGoalProcess customGoalProcess;
   private final BuilderProcess builderProcess;
   private final ExploreProcess exploreProcess;
   private final FarmProcess farmProcess;
   private final InventoryPauserProcess inventoryPauserProcess;
   private final IElytraProcess elytraProcess;
   private final PathingControlManager pathingControlManager;
   private final SelectionManager selectionManager;
   private final CommandManager commandManager;
   private final IPlayerContext playerContext;
   private final WorldProvider worldProvider;
   public BlockStateInterface bsi;

   Baritone(Minecraft mc) {
      this.mc = mc;
      this.gameEventHandler = new GameEventHandler(this);
      this.directory = mc.gameDirectory.toPath().resolve("baritone");
      if (!Files.exists(this.directory)) {
         try {
            Files.createDirectories(this.directory);
         } catch (IOException var3) {
         }
      }

      this.playerContext = new BaritonePlayerContext(this, mc);
      this.lookBehavior = this.registerBehavior(LookBehavior::new);
      this.pathingBehavior = this.registerBehavior(PathingBehavior::new);
      this.inventoryBehavior = this.registerBehavior(InventoryBehavior::new);
      this.inputOverrideHandler = this.registerBehavior(InputOverrideHandler::new);
      this.registerBehavior(WaypointBehavior::new);
      this.pathingControlManager = new PathingControlManager(this);
      this.followProcess = this.registerProcess(FollowProcess::new);
      this.mineProcess = this.registerProcess(MineProcess::new);
      this.customGoalProcess = this.registerProcess(CustomGoalProcess::new);
      this.getToBlockProcess = this.registerProcess(GetToBlockProcess::new);
      this.builderProcess = this.registerProcess(BuilderProcess::new);
      this.exploreProcess = this.registerProcess(ExploreProcess::new);
      this.farmProcess = this.registerProcess(FarmProcess::new);
      this.inventoryPauserProcess = this.registerProcess(InventoryPauserProcess::new);
      this.elytraProcess = this.registerProcess(ElytraProcess::create);
      this.registerProcess(BackfillProcess::new);
      this.worldProvider = new WorldProvider(this);
      this.selectionManager = new SelectionManager(this);
      this.commandManager = new CommandManager(this);
   }

   public void registerBehavior(IBehavior behavior) {
      this.gameEventHandler.registerEventListener(behavior);
   }

   public <T extends IBehavior> T registerBehavior(Function<Baritone, T> constructor) {
      T behavior = (T)constructor.apply(this);
      this.registerBehavior(behavior);
      return behavior;
   }

   public <T extends IBaritoneProcess> T registerProcess(Function<Baritone, T> constructor) {
      T behavior = (T)constructor.apply(this);
      this.pathingControlManager.registerProcess(behavior);
      return behavior;
   }

   public PathingControlManager getPathingControlManager() {
      return this.pathingControlManager;
   }

   public InputOverrideHandler getInputOverrideHandler() {
      return this.inputOverrideHandler;
   }

   public CustomGoalProcess getCustomGoalProcess() {
      return this.customGoalProcess;
   }

   public GetToBlockProcess getGetToBlockProcess() {
      return this.getToBlockProcess;
   }

   @Override
   public IPlayerContext getPlayerContext() {
      return this.playerContext;
   }

   public FollowProcess getFollowProcess() {
      return this.followProcess;
   }

   public BuilderProcess getBuilderProcess() {
      return this.builderProcess;
   }

   public InventoryBehavior getInventoryBehavior() {
      return this.inventoryBehavior;
   }

   public LookBehavior getLookBehavior() {
      return this.lookBehavior;
   }

   public ExploreProcess getExploreProcess() {
      return this.exploreProcess;
   }

   public MineProcess getMineProcess() {
      return this.mineProcess;
   }

   public FarmProcess getFarmProcess() {
      return this.farmProcess;
   }

   public InventoryPauserProcess getInventoryPauserProcess() {
      return this.inventoryPauserProcess;
   }

   public PathingBehavior getPathingBehavior() {
      return this.pathingBehavior;
   }

   public SelectionManager getSelectionManager() {
      return this.selectionManager;
   }

   public WorldProvider getWorldProvider() {
      return this.worldProvider;
   }

   @Override
   public IEventBus getGameEventHandler() {
      return this.gameEventHandler;
   }

   public CommandManager getCommandManager() {
      return this.commandManager;
   }

   @Override
   public IElytraProcess getElytraProcess() {
      return this.elytraProcess;
   }

   @Override
   public void openClick() {
      new Thread(() -> {
         try {
            Thread.sleep(100L);
            this.mc.execute(() -> this.mc.setScreen(new GuiClick()));
         } catch (Exception var2) {
         }
      }).start();
   }

   public Path getDirectory() {
      return this.directory;
   }

   public static Settings settings() {
      return BaritoneAPI.getSettings();
   }

   public static Executor getExecutor() {
      return threadPool;
   }
}

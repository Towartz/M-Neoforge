package baritone.api;

import baritone.api.utils.Helper;
import baritone.api.utils.NotificationHelper;
import baritone.api.utils.SettingsUtil;
import baritone.api.utils.TypeUtils;
import baritone.api.utils.gui.BaritoneToast;
import java.awt.Color;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Settings {
   private static final Logger LOGGER = LoggerFactory.getLogger("Baritone");
   public final Settings.Setting<Boolean> allowBreak = new Settings.Setting<>(true);
   public final Settings.Setting<List<Block>> allowBreakAnyway = new Settings.Setting<>(new ArrayList<>());
   public final Settings.Setting<Boolean> allowSprint = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowPlace = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowPlaceInFluidsSource = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowPlaceInFluidsFlow = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowInventory = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> ticksBetweenInventoryMoves = new Settings.Setting<>(1);
   public final Settings.Setting<Boolean> inventoryMoveOnlyIfStationary = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> assumeExternalAutoTool = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> autoTool = new Settings.Setting<>(true);
   public final Settings.Setting<Double> blockPlacementPenalty = new Settings.Setting<>(20.0);
   public final Settings.Setting<Double> blockBreakAdditionalPenalty = new Settings.Setting<>(2.0);
   public final Settings.Setting<Double> jumpPenalty = new Settings.Setting<>(2.0);
   public final Settings.Setting<Double> walkOnWaterOnePenalty = new Settings.Setting<>(3.0);
   public final Settings.Setting<Boolean> strictLiquidCheck = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> allowWaterBucketFall = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> assumeWalkOnWater = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> assumeWalkOnLava = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> assumeStep = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> assumeSafeWalk = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> allowJumpAtBuildLimit = new Settings.Setting<>(false);
   @Deprecated
   @Settings.JavaOnly
   public final Settings.Setting<Boolean> allowJumpAt256 = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> allowParkourAscend = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowDiagonalDescend = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> allowDiagonalAscend = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> allowDownward = new Settings.Setting<>(true);
   public final Settings.Setting<List<Item>> acceptableThrowawayItems = new Settings.Setting<>(
      new ArrayList<>(Arrays.asList(Blocks.DIRT.asItem(), Blocks.COBBLESTONE.asItem(), Blocks.NETHERRACK.asItem(), Blocks.STONE.asItem()))
   );
   public final Settings.Setting<List<Block>> blocksToAvoid = new Settings.Setting<>(new ArrayList<>(List.of(Blocks.TRIPWIRE)));
   public final Settings.Setting<List<Block>> blocksToDisallowBreaking = new Settings.Setting<>(new ArrayList<>());
   public final Settings.Setting<List<Block>> blocksToAvoidBreaking = new Settings.Setting<>(
      new ArrayList<>(Arrays.asList(Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.CHEST, Blocks.TRAPPED_CHEST))
   );
   public final Settings.Setting<Double> avoidBreakingMultiplier = new Settings.Setting<>(0.1);
   public final Settings.Setting<List<Block>> buildIgnoreBlocks = new Settings.Setting<>(new ArrayList<>(Arrays.asList()));
   public final Settings.Setting<List<Block>> buildSkipBlocks = new Settings.Setting<>(new ArrayList<>(Arrays.asList()));
   public final Settings.Setting<Map<Block, List<Block>>> buildValidSubstitutes = new Settings.Setting<>(new HashMap<>());
   public final Settings.Setting<Map<Block, List<Block>>> buildSubstitutes = new Settings.Setting<>(new HashMap<>());
   public final Settings.Setting<List<Block>> okIfAir = new Settings.Setting<>(new ArrayList<>(Arrays.asList()));
   public final Settings.Setting<Boolean> buildIgnoreExisting = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> buildIgnoreDirection = new Settings.Setting<>(false);
   public final Settings.Setting<List<String>> buildIgnoreProperties = new Settings.Setting<>(new ArrayList<>(Arrays.asList()));
   public final Settings.Setting<Boolean> avoidUpdatingFallingBlocks = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowVines = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> allowWalkOnBottomSlab = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowParkour = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> allowParkourPlace = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> considerPotionEffects = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> sprintAscends = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> overshootTraverse = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> pauseMiningForFallingBlocks = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> rightClickSpeed = new Settings.Setting<>(4);
   public final Settings.Setting<Double> randomLooking113 = new Settings.Setting<>(2.0);
   public final Settings.Setting<Float> blockReachDistance = new Settings.Setting<>(4.5F);
   public final Settings.Setting<Integer> blockBreakSpeed = new Settings.Setting<>(6);
   public final Settings.Setting<Double> randomLooking = new Settings.Setting<>(0.01);
   public final Settings.Setting<Double> costHeuristic = new Settings.Setting<>(3.563);
   public final Settings.Setting<Integer> pathingMaxChunkBorderFetch = new Settings.Setting<>(50);
   public final Settings.Setting<Double> backtrackCostFavoringCoefficient = new Settings.Setting<>(0.5);
   public final Settings.Setting<Boolean> avoidance = new Settings.Setting<>(false);
   public final Settings.Setting<Double> mobSpawnerAvoidanceCoefficient = new Settings.Setting<>(2.0);
   public final Settings.Setting<Integer> mobSpawnerAvoidanceRadius = new Settings.Setting<>(16);
   public final Settings.Setting<Double> mobAvoidanceCoefficient = new Settings.Setting<>(1.5);
   public final Settings.Setting<Integer> mobAvoidanceRadius = new Settings.Setting<>(8);
   public final Settings.Setting<Boolean> rightClickContainerOnArrival = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> enterPortal = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> minimumImprovementRepropagation = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> cutoffAtLoadBoundary = new Settings.Setting<>(false);
   public final Settings.Setting<Double> maxCostIncrease = new Settings.Setting<>(10.0);
   public final Settings.Setting<Integer> costVerificationLookahead = new Settings.Setting<>(5);
   public final Settings.Setting<Double> pathCutoffFactor = new Settings.Setting<>(0.9);
   public final Settings.Setting<Integer> pathCutoffMinimumLength = new Settings.Setting<>(30);
   public final Settings.Setting<Integer> planningTickLookahead = new Settings.Setting<>(150);
   public final Settings.Setting<Integer> pathingMapDefaultSize = new Settings.Setting<>(1024);
   public final Settings.Setting<Float> pathingMapLoadFactor = new Settings.Setting<>(0.75F);
   public final Settings.Setting<Integer> maxFallHeightNoWater = new Settings.Setting<>(3);
   public final Settings.Setting<Integer> maxFallHeightBucket = new Settings.Setting<>(20);
   public final Settings.Setting<Boolean> allowOvershootDiagonalDescend = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> simplifyUnloadedYCoord = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> repackOnAnyBlockChange = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> movementTimeoutTicks = new Settings.Setting<>(100);
   public final Settings.Setting<Long> primaryTimeoutMS = new Settings.Setting<>(1500L);
   public final Settings.Setting<Long> failureTimeoutMS = new Settings.Setting<>(4000L);
   public final Settings.Setting<Long> planAheadPrimaryTimeoutMS = new Settings.Setting<>(4000L);
   public final Settings.Setting<Long> planAheadFailureTimeoutMS = new Settings.Setting<>(5000L);
   public final Settings.Setting<Boolean> slowPath = new Settings.Setting<>(false);
   public final Settings.Setting<Long> slowPathTimeDelayMS = new Settings.Setting<>(100L);
   public final Settings.Setting<Long> slowPathTimeoutMS = new Settings.Setting<>(40000L);
   public final Settings.Setting<Boolean> doBedWaypoints = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> doDeathWaypoints = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> chunkCaching = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> pruneRegionsFromRAM = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> chunkPackerQueueMaxSize = new Settings.Setting<>(2000);
   public final Settings.Setting<Boolean> backfill = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> logAsToast = new Settings.Setting<>(false);
   public final Settings.Setting<Long> toastTimer = new Settings.Setting<>(5000L);
   public final Settings.Setting<Boolean> chatDebug = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> chatControl = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> chatControlAnyway = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> renderPath = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderPathAsLine = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> renderGoal = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderGoalAnimated = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderSelectionBoxes = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderGoalIgnoreDepth = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderGoalXZBeacon = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> renderSelectionBoxesIgnoreDepth = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderPathIgnoreDepth = new Settings.Setting<>(true);
   public final Settings.Setting<Float> pathRenderLineWidthPixels = new Settings.Setting<>(5.0F);
   public final Settings.Setting<Float> goalRenderLineWidthPixels = new Settings.Setting<>(3.0F);
   public final Settings.Setting<Boolean> fadePath = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> freeLook = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> blockFreeLook = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> elytraFreeLook = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> smoothLook = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> elytraSmoothLook = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> smoothLookTicks = new Settings.Setting<>(5);
   public final Settings.Setting<Boolean> remainWithExistingLookDirection = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> antiCheatCompatibility = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> pathThroughCachedOnly = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> sprintInWater = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> blacklistClosestOnFailure = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderCachedChunks = new Settings.Setting<>(false);
   public final Settings.Setting<Float> cachedChunksOpacity = new Settings.Setting<>(0.5F);
   public final Settings.Setting<Boolean> prefixControl = new Settings.Setting<>(true);
   public final Settings.Setting<String> prefix = new Settings.Setting<>("#");
   public final Settings.Setting<Boolean> shortBaritonePrefix = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> useMessageTag = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> echoCommands = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> censorCoordinates = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> censorRanCommands = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> itemSaver = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> itemSaverThreshold = new Settings.Setting<>(10);
   public final Settings.Setting<Boolean> preferSilkTouch = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> walkWhileBreaking = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> splicePath = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> maxPathHistoryLength = new Settings.Setting<>(300);
   public final Settings.Setting<Integer> pathHistoryCutoffAmount = new Settings.Setting<>(50);
   public final Settings.Setting<Integer> mineGoalUpdateInterval = new Settings.Setting<>(5);
   public final Settings.Setting<Integer> maxCachedWorldScanCount = new Settings.Setting<>(10);
   public final Settings.Setting<Integer> mineMaxOreLocationsCount = new Settings.Setting<>(64);
   public final Settings.Setting<Boolean> mineClingyTarget = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> mineDirectReachable = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> mineVeinCluster = new Settings.Setting<>(true);
   public final Settings.Setting<Double> mineHysteresisDistance = new Settings.Setting<>(8.0);
   public final Settings.Setting<Integer> mineFailureRetryCount = new Settings.Setting<>(3);
   public final Settings.Setting<Integer> minYLevelWhileMining = new Settings.Setting<>(0);
   public final Settings.Setting<Integer> maxYLevelWhileMining = new Settings.Setting<>(2031);
   public final Settings.Setting<Boolean> allowOnlyExposedOres = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> allowOnlyExposedOresDistance = new Settings.Setting<>(1);
   public final Settings.Setting<Boolean> mineOnlyLoadedChunks = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> mineMaxChunkRadius = new Settings.Setting<>(8);
   public final Settings.Setting<Boolean> exploreForBlocks = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> worldExploringChunkOffset = new Settings.Setting<>(0);
   public final Settings.Setting<Integer> exploreChunkSetMinimumSize = new Settings.Setting<>(10);
   public final Settings.Setting<Integer> exploreMaintainY = new Settings.Setting<>(64);
   public final Settings.Setting<Boolean> replantCrops = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> replantNetherWart = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> farmMaxScanSize = new Settings.Setting<>(256);
   public final Settings.Setting<Boolean> farmWaitForGrowth = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> extendCacheOnThreshold = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> buildInLayers = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> layerOrder = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> layerHeight = new Settings.Setting<>(1);
   public final Settings.Setting<Integer> startAtLayer = new Settings.Setting<>(0);
   public final Settings.Setting<Boolean> skipFailedLayers = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> buildOnlySelection = new Settings.Setting<>(false);
   public final Settings.Setting<Vec3i> buildRepeat = new Settings.Setting<>(new Vec3i(0, 0, 0));
   public final Settings.Setting<Integer> buildRepeatCount = new Settings.Setting<>(-1);
   public final Settings.Setting<Boolean> buildRepeatSneaky = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> breakFromAbove = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> goalBreakFromAbove = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> mapArtMode = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> okIfWater = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> incorrectSize = new Settings.Setting<>(100);
   public final Settings.Setting<Double> breakCorrectBlockPenaltyMultiplier = new Settings.Setting<>(10.0);
   public final Settings.Setting<Double> placeIncorrectBlockPenaltyMultiplier = new Settings.Setting<>(2.0);
   public final Settings.Setting<Boolean> schematicOrientationX = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> schematicOrientationY = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> schematicOrientationZ = new Settings.Setting<>(false);
   public final Settings.Setting<Rotation> buildSchematicRotation = new Settings.Setting<>(Rotation.NONE);
   public final Settings.Setting<Mirror> buildSchematicMirror = new Settings.Setting<>(Mirror.NONE);
   public final Settings.Setting<String> schematicFallbackExtension = new Settings.Setting<>("schematic");
   public final Settings.Setting<Integer> builderTickScanRadius = new Settings.Setting<>(5);
   public final Settings.Setting<Boolean> mineScanDroppedItems = new Settings.Setting<>(true);
   public final Settings.Setting<Long> mineDropLoiterDurationMSThanksLouca = new Settings.Setting<>(250L);
   public final Settings.Setting<Boolean> distanceTrim = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> cancelOnGoalInvalidation = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> axisHeight = new Settings.Setting<>(120);
   public final Settings.Setting<Boolean> disconnectOnArrival = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> legitMine = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> legitMineYLevel = new Settings.Setting<>(-59);
   public final Settings.Setting<Boolean> legitMineIncludeDiagonals = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> forceInternalMining = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> internalMiningAirException = new Settings.Setting<>(true);
   public final Settings.Setting<Double> followOffsetDistance = new Settings.Setting<>(0.0);
   public final Settings.Setting<Float> followOffsetDirection = new Settings.Setting<>(0.0F);
   public final Settings.Setting<Integer> followRadius = new Settings.Setting<>(3);
   public final Settings.Setting<Integer> followTargetMaxDistance = new Settings.Setting<>(0);
   public final Settings.Setting<Boolean> disableCompletionCheck = new Settings.Setting<>(false);
   public final Settings.Setting<Long> cachedChunksExpirySeconds = new Settings.Setting<>(-1L);
   @Settings.JavaOnly
   public final Settings.Setting<Consumer<Component>> logger = new Settings.Setting<>(msg -> {
      try {
         GuiMessageTag tag = this.useMessageTag.value ? Helper.MESSAGE_TAG : null;
         Minecraft.getInstance().gui.getChat().addMessage(msg, null, tag);
      } catch (Throwable var3x) {
         LOGGER.warn("Failed to log message to chat: " + msg.getString(), var3x);
      }
   });
   @Settings.JavaOnly
   public final Settings.Setting<BiConsumer<String, Boolean>> notifier = new Settings.Setting<>(NotificationHelper::notify);
   @Settings.JavaOnly
   public final Settings.Setting<BiConsumer<Component, Component>> toaster = new Settings.Setting<>(BaritoneToast::addOrUpdate);
   public final Settings.Setting<Boolean> verboseCommandExceptions = new Settings.Setting<>(false);
   public final Settings.Setting<Double> yLevelBoxSize = new Settings.Setting<>(15.0);
   public final Settings.Setting<Color> colorCurrentPath = new Settings.Setting<>(Color.RED);
   public final Settings.Setting<Color> colorNextPath = new Settings.Setting<>(Color.MAGENTA);
   public final Settings.Setting<Color> colorBlocksToBreak = new Settings.Setting<>(Color.RED);
   public final Settings.Setting<Color> colorBlocksToPlace = new Settings.Setting<>(Color.GREEN);
   public final Settings.Setting<Color> colorBlocksToWalkInto = new Settings.Setting<>(Color.MAGENTA);
   public final Settings.Setting<Color> colorBestPathSoFar = new Settings.Setting<>(Color.BLUE);
   public final Settings.Setting<Color> colorMostRecentConsidered = new Settings.Setting<>(Color.CYAN);
   public final Settings.Setting<Boolean> renderMostRecentConsidered = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> renderScannedNodes = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> scannedNodesLimit = new Settings.Setting<>(600);
   public final Settings.Setting<Integer> scannedNodesFadeTimeMS = new Settings.Setting<>(1500);
   public final Settings.Setting<Color> colorScannedNodes = new Settings.Setting<>(new Color(0, 220, 255, 120));
   public final Settings.Setting<Boolean> renderPathWaypoints = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> smoothPathLookahead = new Settings.Setting<>(true);
   public final Settings.Setting<Color> colorGoalBox = new Settings.Setting<>(Color.GREEN);
   public final Settings.Setting<Color> colorInvertedGoalBox = new Settings.Setting<>(Color.RED);
   public final Settings.Setting<Color> colorSelection = new Settings.Setting<>(Color.CYAN);
   public final Settings.Setting<Color> colorSelectionPos1 = new Settings.Setting<>(Color.BLACK);
   public final Settings.Setting<Color> colorSelectionPos2 = new Settings.Setting<>(Color.ORANGE);
   public final Settings.Setting<Float> selectionOpacity = new Settings.Setting<>(0.5F);
   public final Settings.Setting<Float> selectionLineWidth = new Settings.Setting<>(2.0F);
   public final Settings.Setting<Boolean> renderSelection = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderSelectionIgnoreDepth = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderSelectionCorners = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> useSwordToMine = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> desktopNotifications = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> notificationOnPathComplete = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> notificationOnFarmFail = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> notificationOnBuildFinished = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> notificationOnExploreFinished = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> notificationOnMineFail = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> elytraSimulationTicks = new Settings.Setting<>(20);
   public final Settings.Setting<Integer> elytraPitchRange = new Settings.Setting<>(25);
   public final Settings.Setting<Double> elytraFireworkSpeed = new Settings.Setting<>(1.2);
   public final Settings.Setting<Integer> elytraFireworkSetbackUseDelay = new Settings.Setting<>(15);
   public final Settings.Setting<Double> elytraMinimumAvoidance = new Settings.Setting<>(0.2);
   public final Settings.Setting<Boolean> elytraConserveFireworks = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> elytraRenderRaytraces = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> elytraRenderHitboxRaytraces = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> elytraRenderSimulation = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> elytraAutoJump = new Settings.Setting<>(false);
   public final Settings.Setting<Long> elytraNetherSeed = new Settings.Setting<>(146008555100680L);
   public final Settings.Setting<Boolean> elytraPredictTerrain = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> elytraAutoSwap = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> elytraMinimumDurability = new Settings.Setting<>(5);
   public final Settings.Setting<Integer> elytraMinFireworksBeforeLanding = new Settings.Setting<>(5);
   public final Settings.Setting<Boolean> elytraAllowEmergencyLand = new Settings.Setting<>(true);
   public final Settings.Setting<Long> elytraTimeBetweenCacheCullSecs = new Settings.Setting<>(TimeUnit.MINUTES.toSeconds(3L));
   public final Settings.Setting<Integer> elytraCacheCullDistance = new Settings.Setting<>(5000);
   public final Settings.Setting<Boolean> elytraAllowLandOnNetherFortress = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> elytraTermsAccepted = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> elytraChatSpam = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> elytraCustomAllocator = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> elytraAllowTightSpaces = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> elytraAllowAboveRoof = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> elytraUseCache = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> elytraAllowAboveBuildLimit = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> elytraLongDistanceThreshold = new Settings.Setting<>(500);
   public final Settings.Setting<Boolean> allowWalkOnMagmaBlocks = new Settings.Setting<>(false);
   public final Map<String, Settings.Setting<?>> byLowerName;
   public final List<Settings.Setting<?>> allSettings;
   public final Map<Settings.Setting<?>, Type> settingTypes;

   Settings() {
      Field[] temp = this.getClass().getFields();
      Map<String, Settings.Setting<?>> tmpByName = new HashMap<>();
      List<Settings.Setting<?>> tmpAll = new ArrayList<>();
      Map<Settings.Setting<?>, Type> tmpSettingTypes = new HashMap<>();

      try {
         for (Field field : temp) {
            if (field.getType().equals(Settings.Setting.class)) {
               Settings.Setting<?> setting = (Settings.Setting<?>)field.get(this);
               String name = field.getName();
               setting.name = name;
               setting.javaOnly = field.isAnnotationPresent(Settings.JavaOnly.class);
               name = name.toLowerCase();
               if (tmpByName.containsKey(name)) {
                  throw new IllegalStateException("Duplicate setting name");
               }

               tmpByName.put(name, setting);
               tmpAll.add(setting);
               tmpSettingTypes.put(setting, ((ParameterizedType)field.getGenericType()).getActualTypeArguments()[0]);
            }
         }
      } catch (IllegalAccessException var11) {
         throw new IllegalStateException(var11);
      }

      this.byLowerName = Collections.unmodifiableMap(tmpByName);
      this.allSettings = Collections.unmodifiableList(tmpAll);
      this.settingTypes = Collections.unmodifiableMap(tmpSettingTypes);
   }

   public <T> List<Settings.Setting<T>> getAllValuesByType(Class<T> cla$$) {
      List<Settings.Setting<T>> result = new ArrayList<>();

      for (Settings.Setting<?> setting : this.allSettings) {
         if (setting.getValueClass().equals(cla$$)) {
            result.add((Settings.Setting<T>)setting);
         }
      }

      return result;
   }

   @Retention(RetentionPolicy.RUNTIME)
   @Target({ElementType.FIELD})
   private @interface JavaOnly {
   }

   public final class Setting<T> {
      public T value;
      public final T defaultValue;
      private String name;
      private boolean javaOnly;

      private Setting(T value) {
         if (value == null) {
            throw new IllegalArgumentException("Cannot determine value type class from null");
         } else {
            this.value = value;
            this.defaultValue = value;
            this.javaOnly = false;
         }
      }

      @Deprecated
      public final T get() {
         return this.value;
      }

      public final String getName() {
         return this.name;
      }

      public Class<T> getValueClass() {
         return (Class<T>)TypeUtils.resolveBaseClass(this.getType());
      }

      @Override
      public String toString() {
         return SettingsUtil.settingToString(this);
      }

      public void reset() {
         this.value = this.defaultValue;
      }

      public final Type getType() {
         return Settings.this.settingTypes.get(this);
      }

      public boolean isJavaOnly() {
         return this.javaOnly;
      }
   }
}

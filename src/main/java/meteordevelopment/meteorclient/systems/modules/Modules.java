package meteordevelopment.meteorclient.systems.modules;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.ModuleBindChangedEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.combat.AnchorAura;
import meteordevelopment.meteorclient.systems.modules.combat.AntiAnvil;
import meteordevelopment.meteorclient.systems.modules.combat.AntiBed;
import meteordevelopment.meteorclient.systems.modules.combat.ArrowDodge;
import meteordevelopment.meteorclient.systems.modules.combat.AutoAnvil;
import meteordevelopment.meteorclient.systems.modules.combat.AutoArmor;
import meteordevelopment.meteorclient.systems.modules.combat.AutoCity;
import meteordevelopment.meteorclient.systems.modules.combat.AutoEXP;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTrap;
import meteordevelopment.meteorclient.systems.modules.combat.AutoWeapon;
import meteordevelopment.meteorclient.systems.modules.combat.AutoWeb;
import meteordevelopment.meteorclient.systems.modules.combat.BedAura;
import meteordevelopment.meteorclient.systems.modules.combat.BowAimbot;
import meteordevelopment.meteorclient.systems.modules.combat.BowSpam;
import meteordevelopment.meteorclient.systems.modules.combat.Burrow;
import meteordevelopment.meteorclient.systems.modules.combat.Criticals;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.systems.modules.combat.Hitboxes;
import meteordevelopment.meteorclient.systems.modules.combat.HoleFiller;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.combat.Offhand;
import meteordevelopment.meteorclient.systems.modules.combat.Quiver;
import meteordevelopment.meteorclient.systems.modules.combat.SelfAnvil;
import meteordevelopment.meteorclient.systems.modules.combat.SelfTrap;
import meteordevelopment.meteorclient.systems.modules.combat.SelfWeb;
import meteordevelopment.meteorclient.systems.modules.combat.Surround;
import meteordevelopment.meteorclient.systems.modules.misc.AntiPacketKick;
import meteordevelopment.meteorclient.systems.modules.misc.AutoLog;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.systems.modules.misc.AutoRespawn;
import meteordevelopment.meteorclient.systems.modules.misc.BetterBeacons;
import meteordevelopment.meteorclient.systems.modules.misc.BetterChat;
import meteordevelopment.meteorclient.systems.modules.misc.BookBot;
import meteordevelopment.meteorclient.systems.modules.misc.DiscordPresence;
import meteordevelopment.meteorclient.systems.modules.misc.InventoryTweaks;
import meteordevelopment.meteorclient.systems.modules.misc.MessageAura;
import meteordevelopment.meteorclient.systems.modules.misc.NameProtect;
import meteordevelopment.meteorclient.systems.modules.misc.Notebot;
import meteordevelopment.meteorclient.systems.modules.misc.Notifier;
import meteordevelopment.meteorclient.systems.modules.misc.PacketCanceller;
import meteordevelopment.meteorclient.systems.modules.misc.ServerSpoof;
import meteordevelopment.meteorclient.systems.modules.misc.SoundBlocker;
import meteordevelopment.meteorclient.systems.modules.misc.Spam;
import meteordevelopment.meteorclient.systems.modules.misc.swarm.Swarm;
import meteordevelopment.meteorclient.systems.modules.movement.AirJump;
import meteordevelopment.meteorclient.systems.modules.movement.Anchor;
import meteordevelopment.meteorclient.systems.modules.movement.AntiAFK;
import meteordevelopment.meteorclient.systems.modules.movement.AntiVoid;
import meteordevelopment.meteorclient.systems.modules.movement.AutoJump;
import meteordevelopment.meteorclient.systems.modules.movement.AutoWalk;
import meteordevelopment.meteorclient.systems.modules.movement.AutoWasp;
import meteordevelopment.meteorclient.systems.modules.movement.Blink;
import meteordevelopment.meteorclient.systems.modules.movement.BoatFly;
import meteordevelopment.meteorclient.systems.modules.movement.ClickTP;
import meteordevelopment.meteorclient.systems.modules.movement.ElytraBoost;
import meteordevelopment.meteorclient.systems.modules.movement.EntityControl;
import meteordevelopment.meteorclient.systems.modules.movement.EntitySpeed;
import meteordevelopment.meteorclient.systems.modules.movement.FastClimb;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import meteordevelopment.meteorclient.systems.modules.movement.GUIMove;
import meteordevelopment.meteorclient.systems.modules.movement.HighJump;
import meteordevelopment.meteorclient.systems.modules.movement.Jesus;
import meteordevelopment.meteorclient.systems.modules.movement.LongJump;
import meteordevelopment.meteorclient.systems.modules.movement.NoFall;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import meteordevelopment.meteorclient.systems.modules.movement.Parkour;
import meteordevelopment.meteorclient.systems.modules.movement.ReverseStep;
import meteordevelopment.meteorclient.systems.modules.movement.SafeWalk;
import meteordevelopment.meteorclient.systems.modules.movement.Scaffold;
import meteordevelopment.meteorclient.systems.modules.movement.Slippy;
import meteordevelopment.meteorclient.systems.modules.movement.Sneak;
import meteordevelopment.meteorclient.systems.modules.movement.Spider;
import meteordevelopment.meteorclient.systems.modules.movement.Sprint;
import meteordevelopment.meteorclient.systems.modules.movement.Step;
import meteordevelopment.meteorclient.systems.modules.movement.TridentBoost;
import meteordevelopment.meteorclient.systems.modules.movement.Velocity;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.systems.modules.movement.speed.Speed;
import meteordevelopment.meteorclient.systems.modules.player.AntiHunger;
import meteordevelopment.meteorclient.systems.modules.player.AutoClicker;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoFish;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.systems.modules.player.AutoMend;
import meteordevelopment.meteorclient.systems.modules.player.AutoReplenish;
import meteordevelopment.meteorclient.systems.modules.player.AutoTool;
import meteordevelopment.meteorclient.systems.modules.player.BreakDelay;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.systems.modules.player.EXPThrower;
import meteordevelopment.meteorclient.systems.modules.player.FakePlayer;
import meteordevelopment.meteorclient.systems.modules.player.FastUse;
import meteordevelopment.meteorclient.systems.modules.player.GhostHand;
import meteordevelopment.meteorclient.systems.modules.player.InstantRebreak;
import meteordevelopment.meteorclient.systems.modules.player.LiquidInteract;
import meteordevelopment.meteorclient.systems.modules.player.MiddleClickExtra;
import meteordevelopment.meteorclient.systems.modules.player.Multitask;
import meteordevelopment.meteorclient.systems.modules.player.NoInteract;
import meteordevelopment.meteorclient.systems.modules.player.NoMiningTrace;
import meteordevelopment.meteorclient.systems.modules.player.NoRotate;
import meteordevelopment.meteorclient.systems.modules.player.OffhandCrash;
import meteordevelopment.meteorclient.systems.modules.player.Portals;
import meteordevelopment.meteorclient.systems.modules.player.PotionSaver;
import meteordevelopment.meteorclient.systems.modules.player.PotionSpoof;
import meteordevelopment.meteorclient.systems.modules.player.Reach;
import meteordevelopment.meteorclient.systems.modules.player.Rotation;
import meteordevelopment.meteorclient.systems.modules.player.SpeedMine;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import meteordevelopment.meteorclient.systems.modules.render.BetterTooltips;
import meteordevelopment.meteorclient.systems.modules.render.BlockSelection;
import meteordevelopment.meteorclient.systems.modules.render.Blur;
import meteordevelopment.meteorclient.systems.modules.render.BossStack;
import meteordevelopment.meteorclient.systems.modules.render.Breadcrumbs;
import meteordevelopment.meteorclient.systems.modules.render.BreakIndicators;
import meteordevelopment.meteorclient.systems.modules.render.CameraTweaks;
import meteordevelopment.meteorclient.systems.modules.render.Chams;
import meteordevelopment.meteorclient.systems.modules.render.CityESP;
import meteordevelopment.meteorclient.systems.modules.render.ESP;
import meteordevelopment.meteorclient.systems.modules.render.EntityOwner;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.systems.modules.render.Fullbright;
import meteordevelopment.meteorclient.systems.modules.render.HandView;
import meteordevelopment.meteorclient.systems.modules.render.HoleESP;
import meteordevelopment.meteorclient.systems.modules.render.ItemHighlight;
import meteordevelopment.meteorclient.systems.modules.render.ItemPhysics;
import meteordevelopment.meteorclient.systems.modules.render.LightOverlay;
import meteordevelopment.meteorclient.systems.modules.render.LogoutSpots;
import meteordevelopment.meteorclient.systems.modules.render.Nametags;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.systems.modules.render.PopChams;
import meteordevelopment.meteorclient.systems.modules.render.StorageESP;
import meteordevelopment.meteorclient.systems.modules.render.TimeChanger;
import meteordevelopment.meteorclient.systems.modules.render.Tracers;
import meteordevelopment.meteorclient.systems.modules.render.Trail;
import meteordevelopment.meteorclient.systems.modules.render.Trajectories;
import meteordevelopment.meteorclient.systems.modules.render.TunnelESP;
import meteordevelopment.meteorclient.systems.modules.render.UnfocusedCPU;
import meteordevelopment.meteorclient.systems.modules.render.VoidESP;
import meteordevelopment.meteorclient.systems.modules.render.WallHack;
import meteordevelopment.meteorclient.systems.modules.render.WaypointsModule;
import meteordevelopment.meteorclient.systems.modules.render.Xray;
import meteordevelopment.meteorclient.systems.modules.render.Zoom;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.BlockESP;
import meteordevelopment.meteorclient.systems.modules.render.marker.Marker;
import meteordevelopment.meteorclient.systems.modules.world.AirPlace;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.systems.modules.world.AutoBreed;
import meteordevelopment.meteorclient.systems.modules.world.AutoBrewer;
import meteordevelopment.meteorclient.systems.modules.world.AutoMount;
import meteordevelopment.meteorclient.systems.modules.world.AutoNametag;
import meteordevelopment.meteorclient.systems.modules.world.AutoShearer;
import meteordevelopment.meteorclient.systems.modules.world.AutoSign;
import meteordevelopment.meteorclient.systems.modules.world.AutoSmelter;
import meteordevelopment.meteorclient.systems.modules.world.BuildHeight;
import meteordevelopment.meteorclient.systems.modules.world.ChunkScanner;
import meteordevelopment.meteorclient.systems.modules.world.Collisions;
import meteordevelopment.meteorclient.systems.modules.world.EChestFarmer;
import meteordevelopment.meteorclient.systems.modules.world.EndermanLook;
import meteordevelopment.meteorclient.systems.modules.world.Excavator;
import meteordevelopment.meteorclient.systems.modules.world.Flamethrower;
import meteordevelopment.meteorclient.systems.modules.world.GotoSurface;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder;
import meteordevelopment.meteorclient.systems.modules.world.InfinityMiner;
import meteordevelopment.meteorclient.systems.modules.world.LiquidFiller;
import meteordevelopment.meteorclient.systems.modules.world.MountBypass;
import meteordevelopment.meteorclient.systems.modules.world.NoGhostBlocks;
import meteordevelopment.meteorclient.systems.modules.world.Nuker;
import meteordevelopment.meteorclient.systems.modules.world.PacketMine;
import meteordevelopment.meteorclient.systems.modules.world.SpawnProofer;
import meteordevelopment.meteorclient.systems.modules.world.StashFinder;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.systems.modules.world.VeinMiner;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.ValueComparableMap;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Modules extends System<Modules> {
   public static final Modules.ModuleRegistry REGISTRY = new Modules.ModuleRegistry();
   private static final List<Category> CATEGORIES = new ArrayList<>();
   private final List<Module> modules = new ArrayList<>();
   private final Map<Class<? extends Module>, Module> moduleInstances = new Reference2ReferenceOpenHashMap();
   private final Map<Category, List<Module>> groups = new Reference2ReferenceOpenHashMap();
   private final List<Module> active = new ArrayList<>();
   private Module moduleToBind;
   private boolean awaitingKeyRelease = false;

   public Modules() {
      super("modules");
   }

   public static Modules get() {
      return Systems.get(Modules.class);
   }

   @Override
   public void init() {
      this.initCombat();
      this.initPlayer();
      this.initMovement();
      this.initRender();
      this.initWorld();
      this.initMisc();
   }

   @Override
   public void load(File folder) {
      for (Module module : this.modules) {
         for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
               setting.reset();
            }
         }
      }

      super.load(folder);
   }

   public void sortModules() {
      for (List<Module> modules : this.groups.values()) {
         modules.sort(Comparator.comparing(o -> o.title));
      }

      this.modules.sort(Comparator.comparing(o -> o.title));
   }

   public static void registerCategory(Category category) {
      if (!Categories.REGISTERING) {
         throw new RuntimeException("Modules.registerCategory - Cannot register category outside of onRegisterCategories callback.");
      } else {
         CATEGORIES.add(category);
      }
   }

   public static Iterable<Category> loopCategories() {
      return CATEGORIES;
   }

   @Deprecated(
      forRemoval = true
   )
   public static Category getCategoryByHash(int hash) {
      for (Category category : CATEGORIES) {
         if (category.hashCode() == hash) {
            return category;
         }
      }

      return null;
   }

   public <T extends Module> T get(Class<T> klass) {
      return (T)this.moduleInstances.get(klass);
   }

   public Module get(String name) {
      for (Module module : this.moduleInstances.values()) {
         if (module.name.equalsIgnoreCase(name)) {
            return module;
         }
      }

      return null;
   }

   public boolean isActive(Class<? extends Module> klass) {
      Module module = this.get(klass);
      return module != null && module.isActive();
   }

   public List<Module> getGroup(Category category) {
      return this.groups.computeIfAbsent(category, category1 -> new ArrayList<>());
   }

   public Collection<Module> getAll() {
      return this.moduleInstances.values();
   }

   public List<Module> getList() {
      return this.modules;
   }

   public int getCount() {
      return this.moduleInstances.values().size();
   }

   public List<Module> getActive() {
      synchronized (this.active) {
         return this.active;
      }
   }

   public Set<Module> searchTitles(String text) {
      Map<Module, Integer> modules = new ValueComparableMap<>(Comparator.naturalOrder());

      for (Module module : this.moduleInstances.values()) {
         int score = Utils.searchLevenshteinDefault(module.title, text, false);
         if (Config.get().moduleAliases.get()) {
            for (String alias : module.aliases) {
               int aliasScore = Utils.searchLevenshteinDefault(alias, text, false);
               if (aliasScore < score) {
                  score = aliasScore;
               }
            }
         }

         modules.put(module, modules.getOrDefault(module, 0) + score);
      }

      return modules.keySet();
   }

   public Set<Module> searchSettingTitles(String text) {
      Map<Module, Integer> modules = new ValueComparableMap<>(Comparator.naturalOrder());

      for (Module module : this.moduleInstances.values()) {
         int lowest = Integer.MAX_VALUE;

         for (SettingGroup sg : module.settings) {
            for (Setting<?> setting : sg) {
               int score = Utils.searchLevenshteinDefault(setting.title, text, false);
               if (score < lowest) {
                  lowest = score;
               }
            }
         }

         modules.put(module, modules.getOrDefault(module, 0) + lowest);
      }

      return modules.keySet();
   }

   void addActive(Module module) {
      synchronized (this.active) {
         if (!this.active.contains(module)) {
            this.active.add(module);
            MeteorClient.EVENT_BUS.post(ActiveModulesChangedEvent.get());
         }
      }
   }

   void removeActive(Module module) {
      synchronized (this.active) {
         if (this.active.remove(module)) {
            MeteorClient.EVENT_BUS.post(ActiveModulesChangedEvent.get());
         }
      }
   }

   public void setModuleToBind(Module moduleToBind) {
      this.moduleToBind = moduleToBind;
   }

   public void awaitKeyRelease() {
      this.awaitingKeyRelease = true;
   }

   public boolean isBinding() {
      return this.moduleToBind != null;
   }

   @EventHandler(
      priority = 200
   )
   private void onKeyBinding(KeyEvent event) {
      if (event.action == KeyAction.Release && this.onBinding(true, event.key, event.modifiers)) {
         event.cancel();
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onButtonBinding(MouseButtonEvent event) {
      if (event.action == KeyAction.Release && this.onBinding(false, event.button, 0)) {
         event.cancel();
      }
   }

   private boolean onBinding(boolean isKey, int value, int modifiers) {
      if (!this.isBinding()) {
         return false;
      } else if (!this.awaitingKeyRelease) {
         if (this.moduleToBind.keybind.canBindTo(isKey, value, modifiers)) {
            this.moduleToBind.keybind.set(isKey, value, modifiers);
            this.moduleToBind.info("Bound to (highlight)%s(default).", this.moduleToBind.keybind);
         } else {
            if (value != 256) {
               return false;
            }

            this.moduleToBind.keybind.set(Keybind.none());
            this.moduleToBind.info("Removed bind.");
         }

         MeteorClient.EVENT_BUS.post(ModuleBindChangedEvent.get(this.moduleToBind));
         this.moduleToBind = null;
         return true;
      } else if (isKey && (value == 257 || value == 335)) {
         this.awaitingKeyRelease = false;
         return false;
      } else {
         return false;
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onKey(KeyEvent event) {
      if (event.action != KeyAction.Repeat) {
         this.onAction(true, event.key, event.modifiers, event.action == KeyAction.Press);
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onMouseButton(MouseButtonEvent event) {
      if (event.action != KeyAction.Repeat) {
         this.onAction(false, event.button, 0, event.action == KeyAction.Press);
      }
   }

   private void onAction(boolean isKey, int value, int modifiers, boolean isPress) {
      if (MeteorClient.mc.screen == null && !Input.isKeyPressed(292)) {
         for (Module module : this.moduleInstances.values()) {
            if (module.keybind.matches(isKey, value, modifiers) && (isPress || module.toggleOnBindRelease && module.isActive())) {
               module.toggle();
               module.sendToggledMsg();
            }
         }
      }
   }

   @EventHandler(
      priority = 201
   )
   private void onOpenScreen(OpenScreenEvent event) {
      if (Utils.canUpdate()) {
         for (Module module : this.moduleInstances.values()) {
            if (module.toggleOnBindRelease && module.isActive()) {
               module.toggle();
               module.sendToggledMsg();
            }
         }
      }
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      synchronized (this.active) {
         for (Module module : this.modules) {
            if (module.isActive() && !module.runInMainMenu) {
               MeteorClient.EVENT_BUS.subscribe(module);
               module.onActivate();
            }
         }
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      synchronized (this.active) {
         for (Module module : this.modules) {
            if (module.isActive() && !module.runInMainMenu) {
               MeteorClient.EVENT_BUS.unsubscribe(module);
               module.onDeactivate();
            }
         }
      }
   }

   public void disableAll() {
      synchronized (this.active) {
         for (Module module : this.modules) {
            if (module.isActive()) {
               module.toggle();
            }
         }
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      ListTag modulesTag = new ListTag();

      for (Module module : this.getAll()) {
         CompoundTag moduleTag = module.toTag();
         if (moduleTag != null) {
            modulesTag.add(moduleTag);
         }
      }

      tag.put("modules", modulesTag);
      return tag;
   }

   public Modules fromTag(CompoundTag tag) {
      this.disableAll();

      for (Tag moduleTagI : tag.getList("modules", 10)) {
         CompoundTag moduleTag = (CompoundTag)moduleTagI;
         Module module = this.get(moduleTag.getString("name"));
         if (module != null) {
            module.fromTag(moduleTag);
         }
      }

      return this;
   }

   public void add(Module module) {
      if (!CATEGORIES.contains(module.category)) {
         throw new RuntimeException("Modules.addModule - Module's category was not registered.");
      } else {
         AtomicReference<Module> removedModule = new AtomicReference<>();
         if (this.moduleInstances.values().removeIf(module1 -> {
            if (module1.name.equals(module.name)) {
               removedModule.set(module1);
               module1.settings.unregisterColorSettings();
               return true;
            } else {
               return false;
            }
         })) {
            this.getGroup(removedModule.get().category).remove(removedModule.get());
         }

         this.moduleInstances.put((Class<? extends Module>)module.getClass(), module);
         this.modules.add(module);
         this.getGroup(module.category).add(module);
         module.settings.registerColorSettings(module);
      }
   }

   private void initCombat() {
      this.add(new AnchorAura());
      this.add(new AntiAnvil());
      this.add(new AntiBed());
      this.add(new ArrowDodge());
      this.add(new AutoAnvil());
      this.add(new AutoArmor());
      this.add(new AutoCity());
      this.add(new AutoEXP());
      this.add(new AutoTotem());
      this.add(new AutoTrap());
      this.add(new AutoWeapon());
      this.add(new AutoWeb());
      this.add(new BedAura());
      this.add(new BowAimbot());
      this.add(new BowSpam());
      this.add(new Burrow());
      this.add(new Criticals());
      this.add(new CrystalAura());
      this.add(new Hitboxes());
      this.add(new HoleFiller());
      this.add(new KillAura());
      this.add(new Offhand());
      this.add(new Quiver());
      this.add(new SelfAnvil());
      this.add(new SelfTrap());
      this.add(new SelfWeb());
      this.add(new Surround());
   }

   private void initPlayer() {
      this.add(new AntiHunger());
      this.add(new AutoEat());
      this.add(new AutoClicker());
      this.add(new AutoFish());
      this.add(new AutoGap());
      this.add(new AutoMend());
      this.add(new AutoReplenish());
      this.add(new AutoTool());
      this.add(new BreakDelay());
      this.add(new ChestSwap());
      this.add(new EXPThrower());
      this.add(new FakePlayer());
      this.add(new FastUse());
      this.add(new GhostHand());
      this.add(new InstantRebreak());
      this.add(new LiquidInteract());
      this.add(new MiddleClickExtra());
      this.add(new Multitask());
      this.add(new NoInteract());
      this.add(new NoMiningTrace());
      this.add(new NoRotate());
      this.add(new OffhandCrash());
      this.add(new Portals());
      this.add(new PotionSaver());
      this.add(new PotionSpoof());
      this.add(new Reach());
      this.add(new Rotation());
      this.add(new SpeedMine());
   }

   private void initMovement() {
      this.add(new AirJump());
      this.add(new Anchor());
      this.add(new AntiAFK());
      this.add(new AntiVoid());
      this.add(new AutoJump());
      this.add(new AutoWalk());
      this.add(new AutoWasp());
      this.add(new Blink());
      this.add(new BoatFly());
      this.add(new ClickTP());
      this.add(new ElytraBoost());
      this.add(new ElytraFly());
      this.add(new EntityControl());
      this.add(new EntitySpeed());
      this.add(new FastClimb());
      this.add(new Flight());
      this.add(new GUIMove());
      this.add(new HighJump());
      this.add(new Jesus());
      this.add(new LongJump());
      this.add(new NoFall());
      this.add(new NoSlow());
      this.add(new Parkour());
      this.add(new ReverseStep());
      this.add(new SafeWalk());
      this.add(new Scaffold());
      this.add(new Slippy());
      this.add(new Sneak());
      this.add(new Speed());
      this.add(new Spider());
      this.add(new Sprint());
      this.add(new Step());
      this.add(new TridentBoost());
      this.add(new Velocity());
   }

   private void initRender() {
      this.add(new BetterTooltips());
      this.add(new BlockSelection());
      this.add(new BossStack());
      this.add(new Breadcrumbs());
      this.add(new BreakIndicators());
      this.add(new CameraTweaks());
      this.add(new Chams());
      this.add(new CityESP());
      this.add(new EntityOwner());
      this.add(new ESP());
      this.add(new Freecam());
      this.add(new FreeLook());
      this.add(new Fullbright());
      this.add(new HandView());
      this.add(new HoleESP());
      this.add(new ItemPhysics());
      this.add(new ItemHighlight());
      this.add(new LightOverlay());
      this.add(new LogoutSpots());
      this.add(new Marker());
      this.add(new Nametags());
      this.add(new NoRender());
      this.add(new BlockESP());
      this.add(new StorageESP());
      this.add(new TimeChanger());
      this.add(new Tracers());
      this.add(new Trail());
      this.add(new Trajectories());
      this.add(new UnfocusedCPU());
      this.add(new VoidESP());
      this.add(new WallHack());
      this.add(new WaypointsModule());
      this.add(new Xray());
      this.add(new Zoom());
      this.add(new Blur());
      this.add(new PopChams());
      this.add(new TunnelESP());
      this.add(new BetterTab());
   }

   private void initWorld() {
      this.add(new AirPlace());
      this.add(new Ambience());
      this.add(new AutoBreed());
      this.add(new AutoBrewer());
      this.add(new AutoMount());
      this.add(new AutoNametag());
      this.add(new AutoShearer());
      this.add(new AutoSign());
      this.add(new AutoSmelter());
      this.add(new BuildHeight());
      this.add(new Collisions());
      this.add(new EChestFarmer());
      this.add(new EndermanLook());
      this.add(new Flamethrower());
      this.add(new HighwayBuilder());
      this.add(new LiquidFiller());
      this.add(new MountBypass());
      this.add(new NoGhostBlocks());
      this.add(new Nuker());
      this.add(new PacketMine());
      this.add(new StashFinder());
      this.add(new SpawnProofer());
      this.add(new Timer());
      this.add(new VeinMiner());
      this.add(new ChunkScanner());
      if (BaritoneUtils.IS_AVAILABLE) {
         this.add(new Excavator());
         this.add(new InfinityMiner());
         this.add(new GotoSurface());
      }
   }

   private void initMisc() {
      this.add(new Swarm());
      this.add(new AntiPacketKick());
      this.add(new AutoLog());
      this.add(new AutoReconnect());
      this.add(new AutoRespawn());
      this.add(new BetterBeacons());
      this.add(new BetterChat());
      this.add(new BookBot());
      this.add(new DiscordPresence());
      this.add(new InventoryTweaks());
      this.add(new MessageAura());
      this.add(new NameProtect());
      this.add(new Notebot());
      this.add(new Notifier());
      this.add(new PacketCanceller());
      this.add(new ServerSpoof());
      this.add(new SoundBlocker());
      this.add(new Spam());
   }

   public static class ModuleRegistry extends MappedRegistry<Module> {
      public ModuleRegistry() {
         super(ResourceKey.createRegistryKey(MeteorClient.identifier("modules")), Lifecycle.stable());
      }

      public int size() {
         return Modules.get().getAll().size();
      }

      public ResourceLocation getKey(Module entry) {
         return null;
      }

      public Optional<ResourceKey<Module>> getResourceKey(Module entry) {
         return Optional.empty();
      }

      public int getId(Module entry) {
         return 0;
      }

      public Module get(ResourceKey<Module> key) {
         return null;
      }

      public Module get(ResourceLocation id) {
         return null;
      }

      public Lifecycle registryLifecycle() {
         return null;
      }

      public Set<ResourceLocation> keySet() {
         return null;
      }

      public boolean containsKey(ResourceLocation id) {
         return false;
      }

      @Nullable
      public Module get(int index) {
         return null;
      }

      @NotNull
      public Iterator<Module> iterator() {
         return new Modules.ModuleRegistry.ModuleIterator();
      }

      public boolean containsKey(ResourceKey<Module> key) {
         return false;
      }

      public Set<Entry<ResourceKey<Module>, Module>> entrySet() {
         return null;
      }

      public Set<ResourceKey<Module>> registryKeySet() {
         return null;
      }

      public Optional<Reference<Module>> getRandom(RandomSource random) {
         return Optional.empty();
      }

      public Registry<Module> freeze() {
         return null;
      }

      public Reference<Module> createEntry(Module value) {
         return null;
      }

      public Optional<Reference<Module>> getHolder(int rawId) {
         return Optional.empty();
      }

      public Optional<Reference<Module>> getHolder(ResourceKey<Module> key) {
         return Optional.empty();
      }

      public Stream<Reference<Module>> holders() {
         return null;
      }

      public Optional<Named<Module>> getTag(TagKey<Module> tag) {
         return Optional.empty();
      }

      public Named<Module> getOrCreateTag(TagKey<Module> tag) {
         return null;
      }

      public Stream<Pair<TagKey<Module>, Named<Module>>> getTags() {
         return null;
      }

      public Stream<TagKey<Module>> getTagNames() {
         return null;
      }

      public void resetTags() {
      }

      public void bindTags(Map<TagKey<Module>, List<Holder<Module>>> tagEntries) {
      }

      private static class ModuleIterator implements Iterator<Module> {
         private final Iterator<Module> iterator = Modules.get().getAll().iterator();

         @Override
         public boolean hasNext() {
            return this.iterator.hasNext();
         }

         public Module next() {
            return this.iterator.next();
         }
      }
   }
}

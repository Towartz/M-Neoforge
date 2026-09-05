package meteordevelopment.meteorclient.systems.waypoints;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

public class Waypoints extends System<Waypoints> implements Iterable<Waypoint> {
   public static final String[] BUILTIN_ICONS = new String[]{"square", "circle", "triangle", "star", "diamond", "skull"};
   public final Map<String, AbstractTexture> icons = new ConcurrentHashMap<>();
   private final List<Waypoint> waypoints = Collections.synchronizedList(new ArrayList<>());

   public Waypoints() {
      super(null);
   }

   public static Waypoints get() {
      return Systems.get(Waypoints.class);
   }

   @Override
   public void init() {
      File iconsFolder = new File(new File(MeteorClient.FOLDER, "waypoints"), "icons");
      iconsFolder.mkdirs();

      for (String builtinIcon : BUILTIN_ICONS) {
         File iconFile = new File(iconsFolder, builtinIcon + ".png");
         if (!iconFile.exists()) {
            this.copyIcon(iconFile);
         }
      }

      File[] files = iconsFolder.listFiles();
      if (files != null) {
         for (File file : files) {
            if (file.getName().endsWith(".png")) {
               try {
                  String name = file.getName().replace(".png", "");
                  AbstractTexture texture = new DynamicTexture(NativeImage.read(new FileInputStream(file)));
                  this.icons.put(name, texture);
               } catch (IOException var9) {
                  MeteorClient.LOG.error("Failed to read a waypoint icon", var9);
               }
            }
         }
      }
   }

   public boolean add(Waypoint waypoint) {
      if (this.waypoints.contains(waypoint)) {
         this.save();
         return true;
      } else {
         this.waypoints.add(waypoint);
         this.save();
         return false;
      }
   }

   public boolean remove(Waypoint waypoint) {
      boolean removed = this.waypoints.remove(waypoint);
      if (removed) {
         this.save();
      }

      return removed;
   }

   public Waypoint get(String name) {
      for (Waypoint waypoint : this.waypoints) {
         if (waypoint.name.get().equalsIgnoreCase(name)) {
            return waypoint;
         }
      }

      return null;
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.load();
   }

   @EventHandler(
      priority = -200
   )
   private void onGameDisconnected(GameLeftEvent event) {
      this.waypoints.clear();
   }

   public static boolean checkDimension(Waypoint waypoint) {
      Dimension playerDim = PlayerUtils.getDimension();
      Dimension waypointDim = waypoint.dimension.get();
      if (playerDim == waypointDim) {
         return true;
      } else if (!waypoint.opposite.get()) {
         return false;
      } else {
         boolean playerOpp = playerDim == Dimension.Overworld || playerDim == Dimension.Nether;
         boolean waypointOpp = waypointDim == Dimension.Overworld || waypointDim == Dimension.Nether;
         return playerOpp && waypointOpp;
      }
   }

   @Override
   public File getFile() {
      return !Utils.canUpdate() ? null : new File(new File(MeteorClient.FOLDER, "waypoints"), Utils.getFileWorldName() + ".nbt");
   }

   public boolean isEmpty() {
      return this.waypoints.isEmpty();
   }

   @NotNull
   @Override
   public Iterator<Waypoint> iterator() {
      return new Waypoints.WaypointIterator();
   }

   private void copyIcon(File file) {
      String path = "/assets/meteor_client/textures/icons/waypoints/" + file.getName();
      InputStream in = Waypoints.class.getResourceAsStream(path);
      if (in == null) {
         path = "/assets/meteor-client/textures/icons/waypoints/" + file.getName();
         in = Waypoints.class.getResourceAsStream(path);
      }
      if (in == null) {
         MeteorClient.LOG.error("Failed to read a resource: {}", path);
      } else {
         try (InputStream input = in) {
            Files.copy(input, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException e) {
            MeteorClient.LOG.error("Failed to copy waypoint icon: {}", file.getName(), e);
         }
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.put("waypoints", NbtUtils.listToTag(this.waypoints));
      return tag;
   }

   public Waypoints fromTag(CompoundTag tag) {
      this.waypoints.clear();

      for (Tag waypointTag : tag.getList("waypoints", 10)) {
         this.waypoints.add(new Waypoint(waypointTag));
      }

      return this;
   }

   private final class WaypointIterator implements Iterator<Waypoint> {
      private final Iterator<Waypoint> it = Waypoints.this.waypoints.iterator();

      @Override
      public boolean hasNext() {
         return this.it.hasNext();
      }

      public Waypoint next() {
         return this.it.next();
      }

      @Override
      public void remove() {
         this.it.remove();
         Waypoints.this.save();
      }
   }
}

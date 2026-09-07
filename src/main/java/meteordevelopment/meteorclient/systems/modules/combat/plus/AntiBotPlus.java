package meteordevelopment.meteorclient.systems.modules.combat.plus;

import java.util.*;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.plus.ColorRemover;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class AntiBotPlus extends Module {
    private final SettingGroup sgFilters = this.settings.createGroup("Filters");
    private final Setting<Boolean> tab = this.sgFilters.add(new BoolSetting.Builder().name("tab").description("Check tab.").defaultValue(true).build());
    private final Setting<TabMode> tabMode = this.sgFilters.add(new EnumSetting.Builder<TabMode>().name("tab-mode").description("Check tab mode.").defaultValue(TabMode.Contains).visible(this.tab::get).build());
    private final Setting<Boolean> entityID = this.sgFilters.add(new BoolSetting.Builder().name("EntityID").description("Check entity id.").defaultValue(true).build());
    private final Setting<Boolean> color = this.sgFilters.add(new BoolSetting.Builder().name("Color").description("Check color.").defaultValue(false).build());
    private final Setting<Boolean> ground = this.sgFilters.add(new BoolSetting.Builder().name("ground").description("Check ground.").defaultValue(true).build());
    private final Setting<Boolean> air = this.sgFilters.add(new BoolSetting.Builder().name("air").description("Check air.").defaultValue(false).build());
    private final Setting<Boolean> invalidGround = this.sgFilters.add(new BoolSetting.Builder().name("Invalid-Ground").description("Check invalid ground.").defaultValue(true).build());
    private final Setting<Boolean> swing = this.sgFilters.add(new BoolSetting.Builder().name("Swing").description("Check swing.").defaultValue(false).build());
    private final Setting<Boolean> derp = this.sgFilters.add(new BoolSetting.Builder().name("derp").description("Check derp pitch.").defaultValue(true).build());
    private final Setting<Boolean> useHash = this.sgFilters.add(new BoolSetting.Builder().name("Memorize bots").description("Prevent attacking and blinking esp.").defaultValue(true).build());

    private final List<UUID> hash = new ArrayList<>();
    private final List<Integer> swings = new ArrayList<>();
    private final List<Integer> grounds = new ArrayList<>();
    private final List<Integer> airs = new ArrayList<>();
    private final Map<Integer, Integer> invalidGrounds = new HashMap<>();

    public AntiBotPlus() {
        super(Categories.PlusCombat, "Anti Bot", "Detects and filters anticheat combat bots.");
    }

    @Override
    public void onDeactivate() {
        this.hash.clear();
        this.clearAll();
    }

    public boolean isBot(Entity entity) {
        return entity instanceof LivingEntity living ? this.isBot(living) : false;
    }

    public boolean isBot(LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;
        if (!this.isActive()) return false;
        if (this.useHash.get() && this.hash.contains(player.getUUID())) return true;

        if (this.color.get() && player.getName().getString().replace("§r", "").contains("§")) {
            if (this.useHash.get()) this.hash.add(player.getUUID());
            return true;
        }

        if (this.ground.get() && !this.grounds.contains(player.getId())) {
            if (this.useHash.get()) this.hash.add(player.getUUID());
            return true;
        }

        if (this.invalidGround.get() && this.invalidGrounds.getOrDefault(player.getId(), 0) >= 10) {
            if (this.useHash.get()) this.hash.add(player.getUUID());
            return true;
        }

        if (this.entityID.get() && (player.getId() >= 1000000000 || player.getId() <= -1)) {
            if (this.useHash.get()) this.hash.add(player.getUUID());
            return true;
        }

        if (this.derp.get() && (player.getXRot() > 90.0F || player.getXRot() < -90.0F)) {
            if (this.useHash.get()) this.hash.add(player.getUUID());
            return true;
        }

        if (this.swing.get() && !this.swings.contains(player.getId())) {
            if (this.useHash.get()) this.hash.add(player.getUUID());
            return true;
        }

        if (this.tab.get() && this.mc.getConnection() != null) {
            String targetname = ColorRemover.getVerbatim(player.getName().getString());
            for (PlayerInfo info : this.mc.getConnection().getOnlinePlayers()) {
                if (info.getProfile() != null && info.getProfile().getName() != null) {
                    String networkName = ColorRemover.getVerbatim(info.getProfile().getName());
                    if (this.tabMode.get() == TabMode.Equals) {
                        if (targetname.equals(networkName)) return false;
                    } else if (this.tabMode.get() == TabMode.Contains_LowerCase) {
                        if (targetname.toLowerCase().contains(networkName.toLowerCase())) return false;
                    } else if (targetname.contains(networkName)) {
                        return false;
                    }
                }
            }
            if (this.useHash.get()) this.hash.add(player.getUUID());
            return true;
        }

        return false;
    }

    @EventHandler
    private void onEntityAdd(EntityAddedEvent event) {
        this.isBot(event.entity);
    }

    @EventHandler
    private void onEntityRemove(EntityRemovedEvent event) {
        this.hash.remove(event.entity.getUUID());
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (this.mc.level == null) return;
        if (event.packet instanceof ClientboundSetEntityDataPacket packet) {
            Entity entity = this.mc.level.getEntity(packet.id());
            if (entity != null) {
                if (entity.onGround()) {
                    this.grounds.add(entity.getId());
                    if (entity.yo != entity.getY()) {
                        this.invalidGrounds.put(entity.getId(), this.invalidGrounds.getOrDefault(entity.getId(), 0) + 1);
                    }
                } else {
                    if (!this.airs.contains(entity.getId())) {
                        this.airs.add(entity.getId());
                    }
                    int currentVL = this.invalidGrounds.getOrDefault(entity.getId(), 0) / 2;
                    if (currentVL <= 0) {
                        this.invalidGrounds.remove(entity.getId());
                    } else {
                        this.invalidGrounds.put(entity.getId(), currentVL);
                    }
                }
            }
        } else if (event.packet instanceof ClientboundAnimatePacket packet) {
            Entity entity = this.mc.level.getEntity(packet.getId());
            if (entity instanceof LivingEntity && packet.getAction() == 0 && !this.swings.contains(entity.getId())) {
                this.swings.add(entity.getId());
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.clearAll();
    }

    private void clearAll() {
        this.swings.clear();
        this.grounds.clear();
        this.airs.clear();
        this.invalidGrounds.clear();
    }

    public enum TabMode {
        Equals,
        Contains,
        Contains_LowerCase;

        @Override
        public String toString() {
            return super.toString().replace('_', ' ');
        }
    }
}

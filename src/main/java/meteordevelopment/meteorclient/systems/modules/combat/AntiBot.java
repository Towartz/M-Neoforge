package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class AntiBot extends Module {
    public enum Mode {
        Conservative,
        Aggressive
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Mode> mode = this.sgGeneral.add(
        new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Detection strictness preset.")
            .defaultValue(Mode.Conservative)
            .build()
    );

    private final Setting<Boolean> tabCheck = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("tab-check")
            .description("Flags players missing from the server tablist.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> duplicateCheck = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("duplicate-check")
            .description("Flags duplicate entities sharing identical names.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> derpPitch = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("derp-pitch")
            .description("Flags players with impossible head pitch (> 90 or < -90).")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> entityIdCheck = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("entity-id-check")
            .description("Flags entities with abnormal IDs (>= 1,000,000,000 or < 0).")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> groundCheck = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("ground-check")
            .description("Flags players spoofing on-ground status while in mid-air (Aggressive).")
            .defaultValue(false)
            .visible(() -> mode.get() == Mode.Aggressive)
            .build()
    );

    private final Set<UUID> confirmedBots = new HashSet<>();
    private final Set<Integer> duplicateNameIds = new HashSet<>();
    private final Map<Integer, Integer> groundViolations = new HashMap<>();

    public AntiBot() {
        super(Categories.Combat, "anti-bot", "Detects and ignores fake anti-cheat bots in combat.");
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    public void reset() {
        confirmedBots.clear();
        duplicateNameIds.clear();
        groundViolations.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }

        if (duplicateCheck.get()) {
            rebuildDuplicateNames();
        }

        if (mode.get() == Mode.Aggressive && groundCheck.get()) {
            updateGroundViolations();
        } else {
            groundViolations.clear();
        }
    }

    public static boolean isBot(Entity entity) {
        AntiBot antiBot = meteordevelopment.meteorclient.systems.modules.Modules.get().get(AntiBot.class);
        return antiBot != null && antiBot.checkBot(entity);
    }

    public boolean checkBot(Entity entity) {
        if (!isActive()) return false;
        if (!(entity instanceof Player player)) return false;
        if (player == mc.player) return false;

        UUID uuid = player.getUUID();
        if (confirmedBots.contains(uuid)) return true;

        if (entityIdCheck.get()) {
            int id = player.getId();
            if (id >= 1000000000 || id < 0) {
                confirmedBots.add(uuid);
                return true;
            }
        }

        if (derpPitch.get()) {
            float pitch = player.getXRot();
            if (pitch > 90.0F || pitch < -90.0F) {
                confirmedBots.add(uuid);
                return true;
            }
        }

        if (duplicateCheck.get() && duplicateNameIds.contains(player.getId())) {
            confirmedBots.add(uuid);
            return true;
        }

        if (tabCheck.get() && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info == null) {
                return true;
            }
        }

        if (mode.get() == Mode.Aggressive && groundCheck.get()) {
            if (groundViolations.getOrDefault(player.getId(), 0) >= 10) {
                confirmedBots.add(uuid);
                return true;
            }
        }

        return false;
    }

    private void rebuildDuplicateNames() {
        if (mc.level == null) return;
        duplicateNameIds.clear();
        Map<String, List<Integer>> nameMap = new HashMap<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player player && player != mc.player) {
                String name = player.getScoreboardName().toLowerCase(Locale.ROOT);
                nameMap.computeIfAbsent(name, k -> new ArrayList<>()).add(player.getId());
            }
        }

        for (List<Integer> ids : nameMap.values()) {
            if (ids.size() > 1) {
                duplicateNameIds.addAll(ids);
            }
        }
    }

    private void updateGroundViolations() {
        if (mc.level == null) return;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player player && player != mc.player) {
                boolean onGround = player.onGround();
                boolean airBelow = mc.level.getBlockState(player.blockPosition().below()).isAir();
                if (onGround && airBelow && player.getY() > player.blockPosition().getY() + 0.1) {
                    groundViolations.merge(player.getId(), 1, Integer::sum);
                }
            }
        }
    }

    @Override
    public String getInfoString() {
        return confirmedBots.isEmpty() ? null : String.valueOf(confirmedBots.size());
    }
}

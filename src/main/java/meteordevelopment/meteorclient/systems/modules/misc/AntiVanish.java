package meteordevelopment.meteorclient.systems.modules.misc;

import com.mojang.brigadier.suggestion.Suggestion;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiVanish extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgSensors = this.settings.createGroup("Sensors");

    private final Setting<Boolean> tabTracker = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("tab-tracker")
            .description("Alerts when a player disappears from tab without a leave message.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> tabProbe = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("tab-probe")
            .description("Actively probes server command autocomplete to detect vanished staff.")
            .defaultValue(true)
            .build()
    );

    private final Setting<String> probeCommand = this.sgGeneral.add(
        new StringSetting.Builder()
            .name("probe-command")
            .description("Command prefix used for autocomplete probe.")
            .defaultValue("/msg ")
            .visible(tabProbe::get)
            .build()
    );

    private final Setting<Integer> probeInterval = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("probe-interval")
            .description("Tick interval between autocomplete probes.")
            .defaultValue(100)
            .min(20)
            .max(600)
            .visible(tabProbe::get)
            .build()
    );

    private final Setting<Boolean> soundSensor = this.sgSensors.add(
        new BoolSetting.Builder()
            .name("sound-sensor")
            .description("Detects unexplained block/interaction sounds with no visible player nearby.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> particleSensor = this.sgSensors.add(
        new BoolSetting.Builder()
            .name("particle-sensor")
            .description("Detects suspicious potion swirls with no visible entities nearby (Experimental).")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> chatAlerts = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("chat-alerts")
            .description("Displays vanish detections in client chat.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> alertSound = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("alert-sound")
            .description("Plays a warning chime upon detecting vanished players.")
            .defaultValue(true)
            .build()
    );

    private final Map<UUID, String> knownPlayers = new ConcurrentHashMap<>();
    private final Map<String, Long> regularDepartures = new ConcurrentHashMap<>();
    private final Set<String> detectedVanished = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Long> alertCooldowns = new ConcurrentHashMap<>();
    private int ticksPassed = 0;
    private int activeProbeId = -1;

    public AntiVanish() {
        super(Categories.Misc, "anti-vanish", "Detects vanished staff and players using tablist, packet probes, and sensors.");
    }

    @Override
    public void onActivate() {
        resetState();
        refreshKnownPlayers();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    private void resetState() {
        knownPlayers.clear();
        regularDepartures.clear();
        detectedVanished.clear();
        alertCooldowns.clear();
        ticksPassed = 0;
        activeProbeId = -1;
    }

    private void refreshKnownPlayers() {
        if (mc.getConnection() == null) return;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info.getProfile() != null && info.getProfile().getName() != null) {
                knownPlayers.put(info.getProfile().getId(), info.getProfile().getName());
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.getConnection() == null) {
            resetState();
            return;
        }

        ticksPassed++;

        // Clean up expired departure timestamps and alert cooldowns (older than 10 seconds)
        long now = System.currentTimeMillis();
        regularDepartures.entrySet().removeIf(entry -> now - entry.getValue() > 10000L);
        alertCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 10000L);

        // Update known players periodic snapshot
        if (ticksPassed % 20 == 0) {
            refreshKnownPlayers();
        }

        // Active tab autocomplete probe
        if (tabProbe.get() && ticksPassed % probeInterval.get() == 0) {
            sendProbe();
        }
    }

    private void sendProbe() {
        if (mc.getConnection() == null) return;
        activeProbeId = (int) (System.currentTimeMillis() % 100000);
        String cmd = probeCommand.get().trim() + " ";
        mc.getConnection().send(new ServerboundCommandSuggestionPacket(activeProbeId, cmd));
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString().toLowerCase(Locale.ROOT);
        if (msg.contains("left the game") || msg.contains("quit") || msg.contains("disconnected")) {
            for (String name : knownPlayers.values()) {
                if (msg.contains(name.toLowerCase(Locale.ROOT))) {
                    regularDepartures.put(name.toLowerCase(Locale.ROOT), System.currentTimeMillis());
                }
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive()) return;

        // 1. Tablist Removal Detection
        if (tabTracker.get() && event.packet instanceof ClientboundPlayerInfoRemovePacket remove) {
            for (UUID uuid : remove.profileIds()) {
                String name = knownPlayers.remove(uuid);
                if (name != null && !name.equalsIgnoreCase(mc.player.getScoreboardName())) {
                    long now = System.currentTimeMillis();
                    Long departedAt = regularDepartures.get(name.toLowerCase(Locale.ROOT));
                    if (departedAt == null || (now - departedAt > 8000L)) {
                        flagVanish(name, "Tablist removal without disconnect message");
                    }
                }
            }
        }

        // 2. Tablist Update Listed Detection
        if (tabTracker.get() && event.packet instanceof ClientboundPlayerInfoUpdatePacket info) {
            if (info.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED)) {
                for (ClientboundPlayerInfoUpdatePacket.Entry entry : info.entries()) {
                    if (entry != null && !entry.listed()) {
                        String name = knownPlayers.get(entry.profileId());
                        if (name != null && !name.equalsIgnoreCase(mc.player.getScoreboardName())) {
                            flagVanish(name, "Unlisted from tablist while remaining connected");
                        }
                    }
                }
            }
        }

        // 3. Command Autocomplete Suggestions Probe
        if (tabProbe.get() && event.packet instanceof ClientboundCommandSuggestionsPacket suggestions) {
            if (activeProbeId != -1 && suggestions.id() == activeProbeId) {
                activeProbeId = -1;
                Set<String> onlineNames = new HashSet<>();
                if (mc.getConnection() != null) {
                    for (PlayerInfo p : mc.getConnection().getOnlinePlayers()) {
                        if (p.getProfile() != null && p.getProfile().getName() != null) {
                            onlineNames.add(p.getProfile().getName().toLowerCase(Locale.ROOT));
                        }
                    }
                }

                for (Suggestion s : suggestions.toSuggestions().getList()) {
                    String candidate = s.getText().trim();
                    if (!candidate.isEmpty() && !candidate.startsWith("/") && candidate.length() >= 3) {
                        String lower = candidate.toLowerCase(Locale.ROOT);
                        if (!onlineNames.contains(lower) && !lower.equalsIgnoreCase(mc.player.getScoreboardName().toLowerCase(Locale.ROOT))) {
                            flagVanish(candidate, "Exposed by command autocomplete while hidden from tablist");
                        }
                    }
                }
            }
        }

        // 4. Sound Sensor
        if (soundSensor.get() && event.packet instanceof ClientboundSoundPacket sound) {
            checkSuspiciousSound(sound);
        }

        // 5. Particle Sensor
        if (particleSensor.get() && event.packet instanceof ClientboundLevelParticlesPacket particles) {
            checkSuspiciousParticles(particles);
        }
    }

    private static boolean isSuspiciousBlockSound(String soundPath) {
        if (soundPath.contains("chest.open") || soundPath.contains("chest.close")) return true;
        if (soundPath.contains("shulker_box.open") || soundPath.contains("shulker_box.close")) return true;
        if (soundPath.contains("barrel.open") || soundPath.contains("barrel.close")) return true;
        if (soundPath.contains("door.open") || soundPath.contains("door.close")) return true;
        if (soundPath.contains("trapdoor.open") || soundPath.contains("trapdoor.close")) return true;
        if (soundPath.contains("fence_gate.open") || soundPath.contains("fence_gate.close")) return true;
        return false;
    }

    private void checkSuspiciousSound(ClientboundSoundPacket sound) {
        if (mc.level == null || mc.player == null) return;

        SoundSource source = sound.getSource();
        // Ignore ambient, hostile mobs, neutral animals, weather, music, etc.
        if (source != SoundSource.PLAYERS && source != SoundSource.BLOCKS) return;

        Vec3 pos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        if (mc.player.position().distanceTo(pos) < 3.0 || mc.player.position().distanceTo(pos) > 48.0) return;

        // Check if any visible entity is near the sound emission
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity != mc.player && entity.position().distanceTo(pos) < 6.0 && !entity.isInvisible()) {
                return; // Normal visible entity or player nearby
            }
        }

        String soundPath = ((SoundEvent) sound.getSound().value()).getLocation().getPath().toLowerCase(Locale.ROOT);

        if (source == SoundSource.BLOCKS) {
            if (!isSuspiciousBlockSound(soundPath)) return;
            flagSuspiciousActivity("Unseen container interaction", soundPath, pos);
        } else {
            flagSuspiciousActivity("Unseen player sound", soundPath, pos);
        }
    }

    private void checkSuspiciousParticles(ClientboundLevelParticlesPacket particles) {
        if (mc.level == null || mc.player == null) return;

        ParticleType<?> type = particles.getParticle().getType();
        if (type != ParticleTypes.ENTITY_EFFECT && type != ParticleTypes.INSTANT_EFFECT) return;

        Vec3 pos = new Vec3(particles.getX(), particles.getY(), particles.getZ());
        if (mc.player.position().distanceTo(pos) < 3.0 || mc.player.position().distanceTo(pos) > 48.0) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity != mc.player && entity.position().distanceTo(pos) < 6.0 && !entity.isInvisible()) {
                return;
            }
        }

        flagSuspiciousActivity("Unseen potion effect", "potion swirls", pos);
    }

    private void flagSuspiciousActivity(String alertType, String details, Vec3 pos) {
        long now = System.currentTimeMillis();
        String chunkKey = alertType + ":" + ((int) Math.floor(pos.x / 16.0)) + ":" + ((int) Math.floor(pos.z / 16.0));
        Long last = alertCooldowns.get(chunkKey);
        if (last != null && (now - last) < 10000L) return;
        alertCooldowns.put(chunkKey, now);

        String coord = String.format("(%.0f, %.0f, %.0f)", pos.x, pos.y, pos.z);
        if (chatAlerts.get()) {
            warning("[AntiVanish] " + alertType + ": " + details + " at " + coord);
        }

        if (alertSound.get() && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, 1.0F));
        }
    }

    private void flagVanish(String name, String reason) {
        if (!detectedVanished.add(name + ":" + reason)) return;

        if (chatAlerts.get()) {
            warning("[AntiVanish] Detected vanished player: " + name + " (" + reason + ")");
        }

        if (alertSound.get() && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, 1.0F));
        }
    }

    @Override
    public String getInfoString() {
        return detectedVanished.isEmpty() ? null : String.valueOf(detectedVanished.size());
    }
}

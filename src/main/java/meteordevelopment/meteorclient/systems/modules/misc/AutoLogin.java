package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.Arrays;
import java.util.Locale;

public class AutoLogin extends Module {
    public enum CommandMode {
        Trigger,
        Standard,
        Custom
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgTiming = this.settings.createGroup("Timing & Triggers");
    private final SettingGroup sgCustom = this.settings.createGroup("Custom Format");

    private final Setting<CommandMode> mode = this.sgGeneral.add(
        new EnumSetting.Builder<CommandMode>()
            .name("mode")
            .description("Command format preset.")
            .defaultValue(CommandMode.Trigger)
            .build()
    );

    private final Setting<String> password = this.sgGeneral.add(
        new StringSetting.Builder()
            .name("password-or-pin")
            .description("Password or numeric PIN used for automatic authentication.")
            .defaultValue("")
            .build()
    );

    private final Setting<Boolean> autoRegister = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-register")
            .description("Automatically detects and sends registration commands.")
            .defaultValue(true)
            .build()
    );

    // Timing & Triggers
    private final Setting<Boolean> sendOnJoin = this.sgTiming.add(
        new BoolSetting.Builder()
            .name("send-on-join")
            .description("Automatically sends authentication command after joining a world or server.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> joinDelay = this.sgTiming.add(
        new IntSetting.Builder()
            .name("join-delay-ticks")
            .description("Tick delay after joining world before sending command (20 ticks = 1s).")
            .defaultValue(20)
            .min(0)
            .max(120)
            .visible(sendOnJoin::get)
            .build()
    );

    private final Setting<Boolean> sendOnPrompt = this.sgTiming.add(
        new BoolSetting.Builder()
            .name("send-on-chat-prompt")
            .description("Sends command when an authentication chat prompt is detected.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> chatDelay = this.sgTiming.add(
        new IntSetting.Builder()
            .name("chat-delay-ticks")
            .description("Tick delay after chat prompt before replying.")
            .defaultValue(5)
            .min(0)
            .max(60)
            .visible(sendOnPrompt::get)
            .build()
    );

    private final Setting<String> promptKeywords = this.sgTiming.add(
        new StringSetting.Builder()
            .name("prompt-keywords")
            .description("Comma-separated keywords to detect authentication requests in chat.")
            .defaultValue("/login, /register, login:, pin:, password:, masuk:, daftar:")
            .visible(sendOnPrompt::get)
            .build()
    );

    // Custom Commands
    private final Setting<String> customLoginCommand = this.sgCustom.add(
        new StringSetting.Builder()
            .name("custom-login-command")
            .description("Custom login command. Use {pass} or {pin} as placeholder (supports ';' chaining).")
            .defaultValue("/trigger login set {pass}")
            .visible(() -> mode.get() == CommandMode.Custom)
            .build()
    );

    private final Setting<String> customRegisterCommand = this.sgCustom.add(
        new StringSetting.Builder()
            .name("custom-register-command")
            .description("Custom register command. Use {pass} or {pin} as placeholder.")
            .defaultValue("/trigger register set {pass}")
            .visible(() -> mode.get() == CommandMode.Custom && autoRegister.get())
            .build()
    );

    private String pendingCommand = null;
    private int pendingTimer = -1;
    private long lastSentTime = 0L;
    private boolean authenticated = false;

    public AutoLogin() {
        super(Categories.Misc, "auto-login", "Automatically executes login, PIN trigger, or custom authentication commands.");
    }

    @Override
    public void onActivate() {
        resetState();
        if (sendOnJoin.get() && mc.player != null) {
            queueCommand(getLoginCommand(), joinDelay.get());
        }
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetState();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (!isActive() || !sendOnJoin.get()) return;
        resetState();
        queueCommand(getLoginCommand(), joinDelay.get());
    }

    private void resetState() {
        pendingCommand = null;
        pendingTimer = -1;
        authenticated = false;
    }

    private void queueCommand(String command, int delayTicks) {
        if (command == null || command.trim().isEmpty()) return;
        this.pendingCommand = command;
        this.pendingTimer = Math.max(0, delayTicks);
    }

    private String formatCommand(String template) {
        String pass = password.get().trim();
        return template.replace("{pass}", pass)
                       .replace("{password}", pass)
                       .replace("{pin}", pass);
    }

    private String getLoginCommand() {
        String pass = password.get().trim();
        return switch (mode.get()) {
            case Trigger -> pass.isEmpty() ? null : "/trigger login set " + pass;
            case Standard -> pass.isEmpty() ? null : "/login " + pass;
            case Custom -> {
                String cmd = customLoginCommand.get();
                if ((cmd.contains("{pass}") || cmd.contains("{password}") || cmd.contains("{pin}")) && pass.isEmpty()) {
                    yield null;
                }
                yield formatCommand(cmd);
            }
        };
    }

    private String getRegisterCommand() {
        String pass = password.get().trim();
        return switch (mode.get()) {
            case Trigger -> pass.isEmpty() ? null : "/trigger register set " + pass;
            case Standard -> pass.isEmpty() ? null : "/register " + pass + " " + pass;
            case Custom -> {
                String cmd = customRegisterCommand.get();
                if ((cmd.contains("{pass}") || cmd.contains("{password}") || cmd.contains("{pin}")) && pass.isEmpty()) {
                    yield null;
                }
                yield formatCommand(cmd);
            }
        };
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!isActive() || !sendOnPrompt.get() || mc.player == null) return;
        if (authenticated) return; // Already authenticated for this session!

        long now = System.currentTimeMillis();
        if (now - lastSentTime < 3000L) return; // Prevent repeated replies within 3 seconds

        String rawMsg = event.getMessage().getString();
        // Ignore client-generated messages to prevent self-triggering
        if (rawMsg.startsWith("[Utility+]") || rawMsg.startsWith("[Auto Login]") || rawMsg.contains("Sent authentication:")) return;

        String msg = rawMsg.toLowerCase(Locale.ROOT);

        // Ignore server rejections, errors, and already-logged-in notices
        if (msg.contains("you cannot trigger") || msg.contains("already logged in") || msg.contains("sudah login") || msg.contains("incorrect password") || msg.contains("unknown or incomplete command")) {
            return;
        }

        // Check for register prompt
        if (autoRegister.get() && (msg.contains("/register") || msg.contains("register") || msg.contains("daftar"))) {
            queueCommand(getRegisterCommand(), chatDelay.get());
            return;
        }

        // Check configured keywords
        String[] keywords = promptKeywords.get().toLowerCase(Locale.ROOT).split(",");
        boolean matched = Arrays.stream(keywords)
            .map(String::trim)
            .filter(k -> !k.isEmpty())
            .anyMatch(msg::contains);

        if (matched) {
            queueCommand(getLoginCommand(), chatDelay.get());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.player == null) return;

        if (pendingTimer > 0) {
            pendingTimer--;
            return;
        }

        if (pendingTimer == 0 && pendingCommand != null) {
            String[] commands = pendingCommand.split(";");
            for (String cmd : commands) {
                String trimmed = cmd.trim();
                if (!trimmed.isEmpty()) {
                    ChatUtils.sendPlayerMsg(trimmed);
                }
            }
            info("Sent authentication: " + pendingCommand);
            lastSentTime = System.currentTimeMillis();
            authenticated = true;
            pendingCommand = null;
            pendingTimer = -1;
        }
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }
}

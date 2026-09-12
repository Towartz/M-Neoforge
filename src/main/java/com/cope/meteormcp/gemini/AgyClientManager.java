package com.cope.meteormcp.gemini;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.systems.GeminiConfig;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class AgyClientManager {
   private static final AgyClientManager INSTANCE = new AgyClientManager();

   public static final List<String> SUPPORTED_AGY_MODELS = List.of(
      "gemini-3.8-flash-high",
      "gemini-3.8-flash-medium",
      "gemini-3.7-flash-high",
      "gemini-3.7-flash-medium",
      "gemini-3.6-flash-high",
      "gemini-3.1-pro-high",
      "claude-sonnet-4-6"
   );

   private AgyClientManager() {
   }

   public static AgyClientManager getInstance() {
      return INSTANCE;
   }

   public String getResolvedAgyPath(GeminiConfig config) {
      if (config != null && config.getAgyPath() != null && !config.getAgyPath().isBlank()) {
         File customFile = new File(config.getAgyPath().trim());
         if (customFile.exists()) {
            return customFile.getAbsolutePath();
         }
      }

      String localAppData = System.getenv("LOCALAPPDATA");
      if (localAppData != null && !localAppData.isBlank()) {
         File agyBin = new File(localAppData, "agy" + File.separator + "bin" + File.separator + "agy.exe");
         if (agyBin.exists()) {
            return agyBin.getAbsolutePath();
         }
      }

      String userProfile = System.getenv("USERPROFILE");
      if (userProfile != null && !userProfile.isBlank()) {
         File agyBin = new File(userProfile, "AppData" + File.separator + "Local" + File.separator + "agy" + File.separator + "bin" + File.separator + "agy.exe");
         if (agyBin.exists()) {
            return agyBin.getAbsolutePath();
         }
      }

      return "agy";
   }

   public boolean isAgyInstalled() {
      String resolved = this.getResolvedAgyPath(null);
      if (!"agy".equalsIgnoreCase(resolved)) {
         File file = new File(resolved);
         if (file.exists()) {
            return true;
         }
      }

      try {
         Process process = new ProcessBuilder(resolved, "--help").start();
         boolean finished = process.waitFor(3, TimeUnit.SECONDS);
         if (finished && process.exitValue() == 0) {
            return true;
         }
         process.destroyForcibly();
      } catch (Exception ignored) {
      }

      return false;
   }

   public TestResult testConfiguration(GeminiConfig config) {
      String agyPath = this.getResolvedAgyPath(config);
      String model = (config != null && config.getAgyModel() != null && !config.getAgyModel().isBlank())
         ? config.getAgyModel()
         : "gemini-3.8-flash-high";

      try {
         ProcessBuilder pb = new ProcessBuilder(
            agyPath,
            "-p", "Respond with: test successful",
            "--model", model,
            "--disable-slash-commands"
         );
         pb.redirectErrorStream(true);
         Process process = pb.start();

         StringBuilder sb = new StringBuilder();
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
               sb.append(line).append("\n");
            }
         }

         boolean finished = process.waitFor(25, TimeUnit.SECONDS);
         if (!finished) {
            process.destroyForcibly();
            return new TestResult(false, "Antigravity CLI timed out after 25 seconds.");
         }

         if (process.exitValue() != 0) {
            String output = sb.toString().trim();
            return new TestResult(false, "AGY CLI exited with code " + process.exitValue() + (output.isEmpty() ? "" : ": " + output));
         }

         String output = sb.toString().trim();
         return new TestResult(true, output.isEmpty() ? "AGY CLI connection successful." : output);
      } catch (Exception e) {
         MeteorMCPAddon.LOG.error("Failed to test AGY CLI configuration: {}", e.getMessage());
         return new TestResult(false, "Failed to run AGY CLI: " + e.getMessage());
      }
   }

   public static final class TestResult {
      private final boolean success;
      private final String message;

      public TestResult(boolean success, String message) {
         this.success = success;
         this.message = message;
      }

      public boolean success() {
         return this.success;
      }

      public String message() {
         return this.message;
      }
   }
}

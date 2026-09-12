package com.cope.meteormcp.commands;

import com.cope.meteormcp.MeteorMCPAddon;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.modelcontextprotocol.spec.McpSchema.AudioContent;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.commands.Command;

public final class CommandUtils {
   private static final ObjectMapper MAPPER = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
   private static final Set<String> BOOLEAN_TRUE = Set.of("true", "1", "yes", "on");
   private static final Set<String> BOOLEAN_FALSE = Set.of("false", "0", "no", "off");

   private CommandUtils() {
   }

   public static Map<String, Object> parseArguments(String argsString, Tool tool) {
      JsonSchema schema = tool != null ? tool.inputSchema() : null;
      return parseArguments(argsString, schema);
   }

   public static Map<String, Object> parseArguments(String argsString, JsonSchema schema) {
      String raw = argsString == null ? "" : argsString.trim();
      if (raw.isEmpty()) {
         return new LinkedHashMap<>();
      } else {
         Map<String, Object> schemaProperties = schema != null ? schema.properties() : Collections.emptyMap();
         if (looksLikeJson(raw)) {
            try {
               Object parsed = MAPPER.readValue(raw, Object.class);
               if (parsed instanceof Map<?, ?> map) {
                  return normalizeJsonMap(map);
               }

               Map<String, Object> payload = new LinkedHashMap<>();
               payload.put("value", parsed);
               return payload;
            } catch (JsonProcessingException var6) {
               MeteorMCPAddon.LOG.debug("Argument JSON parsing failed, falling back to token parsing: {}", var6.getMessage());
            }
         }

         List<String> tokens = tokenizeArguments(raw);
         if (tokens.isEmpty()) {
            return new LinkedHashMap<>();
         } else {
            boolean hasNamed = tokens.stream().anyMatch(token -> findAssignment(token) >= 0);
            return hasNamed ? parseNamedArguments(tokens, schemaProperties) : parsePositionalArguments(tokens, schemaProperties);
         }
      }
   }

   public static boolean validateRequiredParams(Map<String, Object> arguments, Tool toolSchema) {
      if (toolSchema == null) {
         return true;
      } else {
         JsonSchema schema = toolSchema.inputSchema();
         List<String> required = schema != null ? schema.required() : null;
         if (required != null && !required.isEmpty()) {
            for (String name : required) {
               if (!arguments.containsKey(name)) {
                  return false;
               }
            }

            return true;
         } else {
            return true;
         }
      }
   }

   public static String generateUsage(Tool toolSchema) {
      if (toolSchema == null) {
         return "<no arguments>";
      } else {
         JsonSchema schema = toolSchema.inputSchema();
         Map<String, Object> properties = schema != null ? schema.properties() : null;
         if (properties != null && !properties.isEmpty()) {
            Set<String> required = new LinkedHashSet<>(schema != null && schema.required() != null ? schema.required() : Collections.emptyList());
            StringBuilder usage = new StringBuilder();

            for (Entry<String, Object> entry : properties.entrySet()) {
               String name = entry.getKey();
               String type = extractType(entry.getValue());
               boolean requiredParam = required.contains(name);
               usage.append(requiredParam ? "<" : "[").append(name);
               if (!type.isBlank()) {
                  usage.append(":").append(type);
               }

               usage.append(requiredParam ? "> " : "] ");
            }

            return usage.toString().trim();
         } else {
            return "<no arguments>";
         }
      }
   }

   public static void displayToolResult(Command command, CallToolResult result) {
      if (command != null) {
         if (result == null) {
            command.error("Tool returned no result.", new Object[0]);
         } else if (Boolean.TRUE.equals(result.isError())) {
            command.error("Tool Error: {}", new Object[]{extractErrorMessage(result)});
            displayContentList(command, result.content());
            displayStructuredContent(command, result.structuredContent());
         } else {
            boolean hasContent = displayContentList(command, result.content());
            boolean hasStructured = displayStructuredContent(command, result.structuredContent());
            boolean hasMeta = displayMeta(command, result.meta());
            if (!hasContent && !hasStructured && !hasMeta) {
               command.info("Tool executed successfully (no output).", new Object[0]);
            }
         }
      }
   }

   private static boolean displayContentList(Command command, List<Content> contents) {
      if (contents != null && !contents.isEmpty()) {
         for (Content content : contents) {
            displayContent(command, content);
         }

         return true;
      } else {
         return false;
      }
   }

   private static void displayContent(Command command, Content content) {
      if (content != null) {
         if (content instanceof TextContent textContent) {
            String text = Objects.toString(textContent.text(), "").trim();
            if (!text.isEmpty()) {
               for (String line : text.split("\\R")) {
                  if (!line.isBlank()) {
                     command.info(line.trim(), new Object[0]);
                  }
               }
            }
         } else if (content instanceof ImageContent image) {
            String mime = image.mimeType() != null ? image.mimeType() : "image";
            int length = image.data() != null ? image.data().length() : 0;
            command.info("[Image] {} ({} chars)", new Object[]{mime, length});
         } else if (content instanceof AudioContent audio) {
            String mime = audio.mimeType() != null ? audio.mimeType() : "audio";
            command.info("[Audio] {}", new Object[]{mime});
            if (audio.data() != null) {
               command.info("Data length: {} chars", new Object[]{audio.data().length()});
            }
         } else {
            command.info(content.toString(), new Object[0]);
         }
      }
   }

   private static boolean displayStructuredContent(Command command, Object structured) {
      if (structured == null) {
         return false;
      } else {
         try {
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(structured);

            for (String line : json.split("\\R")) {
               command.info(line, new Object[0]);
            }

            return true;
         } catch (JsonProcessingException var7) {
            MeteorMCPAddon.LOG.warn("Failed to render structured content: {}", var7.getMessage());
            command.info(structured.toString(), new Object[0]);
            return true;
         }
      }
   }

   private static boolean displayMeta(Command command, Map<String, Object> meta) {
      if (meta != null && !meta.isEmpty()) {
         command.info("Meta:", new Object[0]);

         for (Entry<String, Object> entry : meta.entrySet()) {
            command.info("  {}: {}", new Object[]{entry.getKey(), entry.getValue()});
         }

         return true;
      } else {
         return false;
      }
   }

   private static String extractErrorMessage(CallToolResult result) {
      List<Content> contents = result.content();
      if (contents != null) {
         for (Content content : contents) {
            if (content instanceof TextContent text && text.text() != null) {
               return text.text();
            }
         }
      }

      Object structured = result.structuredContent();
      return structured != null ? structured.toString() : "No error message provided.";
   }

   private static List<String> tokenizeArguments(String raw) {
      List<String> tokens = new ArrayList<>();
      if (raw.isEmpty()) {
         return tokens;
      } else {
         StringBuilder current = new StringBuilder();
         boolean inQuotes = false;
         char quoteChar = 0;
         boolean escape = false;
         int braceDepth = 0;
         int bracketDepth = 0;
         int parenDepth = 0;

         for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escape) {
               current.append(c);
               escape = false;
            } else if (inQuotes) {
               if (c == '\\') {
                  escape = true;
               }

               current.append(c);
               if (c == quoteChar) {
                  inQuotes = false;
               }
            } else {
               switch (c) {
                  case '"':
                  case '\'':
                     inQuotes = true;
                     quoteChar = c;
                     current.append(c);
                     continue;
                  case '(':
                     parenDepth++;
                     break;
                  case ')':
                     parenDepth = Math.max(0, parenDepth - 1);
                     break;
                  case '[':
                     bracketDepth++;
                     break;
                  case '\\':
                     escape = true;
                     current.append(c);
                     continue;
                  case ']':
                     bracketDepth = Math.max(0, bracketDepth - 1);
                     break;
                  case '{':
                     braceDepth++;
                     break;
                  case '}':
                     braceDepth = Math.max(0, braceDepth - 1);
               }

               if (!Character.isWhitespace(c) || braceDepth != 0 || bracketDepth != 0 || parenDepth != 0) {
                  current.append(c);
               } else if (current.length() > 0) {
                  tokens.add(current.toString());
                  current.setLength(0);
               }
            }
         }

         if (current.length() > 0) {
            tokens.add(current.toString());
         }

         return tokens;
      }
   }

   private static Map<String, Object> parseNamedArguments(List<String> tokens, Map<String, Object> properties) {
      Map<String, Object> result = new LinkedHashMap<>();

      for (String token : tokens) {
         int assignment = findAssignment(token);
         if (assignment < 0) {
            throw new IllegalArgumentException("Invalid named argument: " + token);
         }

         String key = token.substring(0, assignment).trim();
         String valuePart = token.substring(assignment + 1).trim();
         if (key.isEmpty()) {
            throw new IllegalArgumentException("Argument name is required near: " + token);
         }

         Object schema = properties != null ? properties.get(key) : null;
         Object value = coerceValue(valuePart, schema);
         result.put(key, value);
      }

      return result;
   }

   private static Map<String, Object> parsePositionalArguments(List<String> tokens, Map<String, Object> properties) {
      Map<String, Object> result = new LinkedHashMap<>();
      if (properties != null && !properties.isEmpty()) {
         int index = 0;

         for (Entry<String, Object> entry : properties.entrySet()) {
            if (index >= tokens.size()) {
               break;
            }

            String token = tokens.get(index);
            Object schema = entry.getValue();
            Object value = coerceValue(token, schema);
            result.put(entry.getKey(), value);
            index++;
         }

         if (tokens.size() > properties.size()) {
            throw new IllegalArgumentException("Too many positional arguments (expected " + properties.size() + ").");
         } else {
            return result;
         }
      } else {
         for (int i = 0; i < tokens.size(); i++) {
            result.put("arg" + i, stripQuotes(tokens.get(i)));
         }

         return result;
      }
   }

   private static Object coerceValue(String valuePart, Object schema) {
      String raw = valuePart == null ? "" : valuePart.trim();
      if (raw.isEmpty()) {
         return "";
      } else {
         String type = extractType(schema);
         if (type.isBlank()) {
            return stripQuotes(raw);
         } else {
            try {
               return switch (type) {
                  case "integer" -> parseInteger(raw);
                  case "number" -> parseDecimal(raw);
                  case "boolean" -> parseBoolean(raw);
                  case "array" -> (List)MAPPER.readValue(normalizeJsonValue(raw), List.class);
                  case "object" -> (Map)MAPPER.readValue(normalizeJsonValue(raw), Map.class);
                  default -> stripQuotes(raw);
               };
            } catch (JsonProcessingException var6) {
               throw new IllegalArgumentException("Invalid JSON for " + type + " value: " + raw);
            } catch (NumberFormatException var7) {
               throw new IllegalArgumentException("Invalid " + type + " value: " + raw);
            }
         }
      }
   }

   private static Object parseInteger(String raw) {
      return !raw.startsWith("0x") && !raw.startsWith("0X") ? Long.parseLong(stripQuotes(raw)) : Long.parseLong(raw.substring(2), 16);
   }

   private static Object parseDecimal(String raw) {
      return Double.parseDouble(stripQuotes(raw));
   }

   private static Object parseBoolean(String raw) {
      String normalized = stripQuotes(raw).toLowerCase(Locale.ROOT);
      if (BOOLEAN_TRUE.contains(normalized)) {
         return Boolean.TRUE;
      } else if (BOOLEAN_FALSE.contains(normalized)) {
         return Boolean.FALSE;
      } else {
         throw new IllegalArgumentException("Invalid boolean value: " + raw);
      }
   }

   private static String normalizeJsonValue(String raw) {
      String trimmed = raw.trim();
      if ((!trimmed.startsWith("{") || !trimmed.endsWith("}")) && (!trimmed.startsWith("[") || !trimmed.endsWith("]"))) {
         if (trimmed.startsWith("\"") && trimmed.endsWith("\"") || trimmed.startsWith("'") && trimmed.endsWith("'")) {
            trimmed = stripQuotes(trimmed);
         }

         return trimmed;
      } else {
         return trimmed;
      }
   }

   private static String stripQuotes(String value) {
      if (value != null && value.length() >= 2) {
         char first = value.charAt(0);
         char last = value.charAt(value.length() - 1);
         if (first == '"' && last == '"') {
            try {
               return (String)MAPPER.readValue(value, String.class);
            } catch (JsonProcessingException var4) {
               return value.substring(1, value.length() - 1);
            }
         } else {
            return first == '\'' && last == '\'' ? value.substring(1, value.length() - 1).replace("\\'", "'") : value;
         }
      } else {
         return value;
      }
   }

   private static boolean looksLikeJson(String raw) {
      if (raw == null) {
         return false;
      } else {
         String trimmed = raw.trim();
         return trimmed.startsWith("{") && trimmed.endsWith("}") || trimmed.startsWith("[") && trimmed.endsWith("]");
      }
   }

   private static Map<String, Object> normalizeJsonMap(Map<?, ?> map) {
      Map<String, Object> normalized = new LinkedHashMap<>();

      for (Entry<?, ?> entry : map.entrySet()) {
         Object key = entry.getKey();
         if (key != null) {
            normalized.put(key.toString(), entry.getValue());
         }
      }

      return normalized;
   }

   private static String extractType(Object schema) {
      if (schema instanceof Map<?, ?> schemaMap) {
         Object type = schemaMap.get("type");
         if (type != null) {
            return type.toString();
         }
      }

      return "";
   }

   private static int findAssignment(String token) {
      boolean inQuotes = false;
      char quoteChar = 0;
      int brace = 0;
      int bracket = 0;
      int paren = 0;
      boolean escape = false;

      for (int i = 0; i < token.length(); i++) {
         char c = token.charAt(i);
         if (escape) {
            escape = false;
         } else if (inQuotes) {
            if (c == '\\') {
               escape = true;
            } else if (c == quoteChar) {
               inQuotes = false;
            }
         } else {
            switch (c) {
               case '"':
               case '\'':
                  inQuotes = true;
                  quoteChar = c;
                  break;
               case '(':
                  paren++;
                  break;
               case ')':
                  paren = Math.max(0, paren - 1);
                  break;
               case '=':
                  if (brace == 0 && bracket == 0 && paren == 0) {
                     return i;
                  }
                  break;
               case '[':
                  bracket++;
                  break;
               case ']':
                  bracket = Math.max(0, bracket - 1);
                  break;
               case '{':
                  brace++;
                  break;
               case '}':
                  brace = Math.max(0, brace - 1);
            }
         }
      }

      return -1;
   }

   static {
      MAPPER.findAndRegisterModules();
   }
}

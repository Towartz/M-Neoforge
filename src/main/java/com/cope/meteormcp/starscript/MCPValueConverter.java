package com.cope.meteormcp.starscript;

import com.cope.meteormcp.MeteorMCPAddon;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import meteordevelopment.starscript.Starscript;
import meteordevelopment.starscript.value.Value;
import meteordevelopment.starscript.value.ValueMap;

public class MCPValueConverter {
   public static Object toJson(Value value) {
      if (value == null || value.isNull()) {
         return null;
      } else if (value.isBool()) {
         return value.getBool();
      } else if (value.isNumber()) {
         double num = value.getNumber();
         return num == Math.floor(num) ? (long)num : num;
      } else if (value.isString()) {
         return value.getString();
      } else if (value.isMap()) {
         Map<String, Object> map = new HashMap<>();
         ValueMap valueMap = value.getMap();

         for (String key : valueMap.keys()) {
            Supplier<Value> supplier = valueMap.get(key);
            if (supplier != null) {
               Value val = supplier.get();
               if (val != null) {
                  map.put(key, toJson(val));
               }
            }
         }

         return map;
      } else {
         return value.toString();
      }
   }

   public static Value toValue(CallToolResult result) {
      if (result == null) {
         return Value.null_();
      } else {
         try {
            List<Content> contentList = result.content();
            if (contentList != null && !contentList.isEmpty()) {
               if (contentList.size() == 1) {
                  return contentToValue(contentList.get(0));
               } else {
                  StringBuilder combined = new StringBuilder();

                  for (Content content : contentList) {
                     Value val = contentToValue(content);
                     if (val.isString()) {
                        if (combined.length() > 0) {
                           combined.append("\n");
                        }

                        combined.append(val.getString());
                     }
                  }

                  return combined.length() > 0 ? Value.string(combined.toString()) : contentToValue(contentList.get(0));
               }
            } else {
               return Value.null_();
            }
         } catch (Exception var6) {
            MeteorMCPAddon.LOG.error("Error converting MCP result to StarScript value: {}", var6.getMessage());
            return Value.null_();
         }
      }
   }

   private static Value contentToValue(Content content) {
      if (content instanceof TextContent text) {
         return Value.string(text.text());
      } else {
         return content instanceof ImageContent image ? Value.string(image.data()) : Value.string(content.toString());
      }
   }

   public static ValueMap jsonToValueMap(Map<?, ?> map) {
      ValueMap valueMap = new ValueMap();

      for (Entry<?, ?> entry : map.entrySet()) {
         if (entry.getKey() != null) {
            valueMap.set(entry.getKey().toString(), objectToValue(entry.getValue()));
         }
      }

      return valueMap;
   }

   private static Value objectToValue(Object obj) {
      if (obj == null) {
         return Value.null_();
      } else if (obj instanceof Boolean) {
         return Value.bool((Boolean)obj);
      } else if (obj instanceof Number) {
         return Value.number(((Number)obj).doubleValue());
      } else if (obj instanceof String) {
         return Value.string((String)obj);
      } else if (obj instanceof Map<?, ?> map) {
         return Value.map(jsonToValueMap(map));
      } else if (obj instanceof List<?> list) {
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
               sb.append(", ");
            }

            sb.append(list.get(i).toString());
         }

         return Value.string(sb.toString());
      } else {
         return Value.string(obj.toString());
      }
   }

   public static List<Object> extractArgumentsAsList(Starscript ss, int argCount) {
      List<Object> args = new ArrayList<>();

      for (int i = argCount - 1; i >= 0; i--) {
         Value val = ss.pop();
         args.add(0, toJson(val));
      }

      return args;
   }

   public static Map<String, Object> extractArgumentsAsMap(Starscript ss, int argCount, List<String> paramNames) {
      Map<String, Object> args = new HashMap<>();

      for (int i = argCount - 1; i >= 0; i--) {
         Value val = ss.pop();
         String paramName = i < paramNames.size() ? paramNames.get(i) : "arg" + i;
         args.put(paramName, toJson(val));
      }

      return args;
   }
}

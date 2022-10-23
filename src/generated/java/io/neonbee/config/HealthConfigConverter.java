package io.neonbee.config;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.neonbee.config.HealthConfig}.
 * NOTE: This class has been automatically generated from the {@link io.neonbee.config.HealthConfig} original class using Vert.x codegen.
 */
public class HealthConfigConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, HealthConfig obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "enabled":
          if (member.getValue() instanceof Boolean) {
            obj.setEnabled((Boolean)member.getValue());
          }
          break;
        case "timeout":
          if (member.getValue() instanceof Number) {
            obj.setTimeout(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

   static void toJson(HealthConfig obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(HealthConfig obj, java.util.Map<String, Object> json) {
    json.put("enabled", obj.isEnabled());
    json.put("timeout", obj.getTimeout());
  }
}

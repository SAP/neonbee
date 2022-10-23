package io.neonbee.config;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.neonbee.config.MicrometerRegistryConfig}.
 * NOTE: This class has been automatically generated from the {@link io.neonbee.config.MicrometerRegistryConfig} original class using Vert.x codegen.
 */
public class MicrometerRegistryConfigConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, MicrometerRegistryConfig obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "className":
          if (member.getValue() instanceof String) {
            obj.setClassName((String)member.getValue());
          }
          break;
        case "config":
          if (member.getValue() instanceof JsonObject) {
            obj.setConfig(((JsonObject)member.getValue()).copy());
          }
          break;
      }
    }
  }

   static void toJson(MicrometerRegistryConfig obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(MicrometerRegistryConfig obj, java.util.Map<String, Object> json) {
    if (obj.getClassName() != null) {
      json.put("className", obj.getClassName());
    }
    if (obj.getConfig() != null) {
      json.put("config", obj.getConfig());
    }
  }
}

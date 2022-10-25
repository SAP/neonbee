package io.neonbee.config;

import java.util.Base64;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.impl.JsonUtil;

/**
 * Converter and mapper for {@link io.neonbee.config.MetricsConfig}. NOTE: This class has been automatically generated
 * from the {@link io.neonbee.config.MetricsConfig} original class using Vert.x codegen.
 */
public class MetricsConfigConverter {

    private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;

    private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

    static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, MetricsConfig obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
            case "enabled":
                if (member.getValue() instanceof Boolean) {
                    obj.setEnabled((Boolean) member.getValue());
                }
                break;
            }
        }
    }

    static void toJson(MetricsConfig obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    static void toJson(MetricsConfig obj, java.util.Map<String, Object> json) {
        json.put("enabled", obj.isEnabled());
    }
}

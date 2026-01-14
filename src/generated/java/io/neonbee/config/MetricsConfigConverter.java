package io.neonbee.config;

import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link io.neonbee.config.MetricsConfig}. NOTE: This class has been automatically generated
 * from the {@link io.neonbee.config.MetricsConfig} original class using Vert.x codegen.
 */
public class MetricsConfigConverter {

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

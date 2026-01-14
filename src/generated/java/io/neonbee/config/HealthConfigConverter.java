package io.neonbee.config;

import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link io.neonbee.config.HealthConfig}. NOTE: This class has been automatically generated
 * from the {@link io.neonbee.config.HealthConfig} original class using Vert.x codegen.
 */
public class HealthConfigConverter {

    static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, HealthConfig obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
            case "enabled":
                if (member.getValue() instanceof Boolean) {
                    obj.setEnabled((Boolean) member.getValue());
                }
                break;
            case "timeout":
                if (member.getValue() instanceof Number) {
                    obj.setTimeout(((Number) member.getValue()).intValue());
                }
                break;
            case "collectClusteredResults":
                if (member.getValue() instanceof Boolean) {
                    obj.setCollectClusteredResults((Boolean) member.getValue());
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

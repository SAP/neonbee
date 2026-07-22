package io.neonbee.config;

import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link io.neonbee.config.TracingConfig}. NOTE: This class has been automatically generated
 * from the {@link io.neonbee.config.TracingConfig} original class using Vert.x codegen.
 */
public class TracingConfigConverter {

    static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, TracingConfig obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
            case "enabled":
                if (member.getValue() instanceof Boolean) {
                    obj.setEnabled((Boolean) member.getValue());
                }
                break;
            case "otlpEndpoint":
                if (member.getValue() instanceof String) {
                    obj.setOtlpEndpoint((String) member.getValue());
                }
                break;
            case "otlpApiToken":
                if (member.getValue() instanceof String) {
                    obj.setOtlpApiToken((String) member.getValue());
                }
                break;
            case "exportIntervalSeconds":
                if (member.getValue() instanceof Number) {
                    obj.setExportIntervalSeconds(((Number) member.getValue()).intValue());
                }
                break;
            }
        }
    }

    static void toJson(TracingConfig obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    static void toJson(TracingConfig obj, java.util.Map<String, Object> json) {
        json.put("enabled", obj.isEnabled());
        if (obj.getOtlpEndpoint() != null) {
            json.put("otlpEndpoint", obj.getOtlpEndpoint());
        }
        if (obj.getOtlpApiToken() != null) {
            json.put("otlpApiToken", obj.getOtlpApiToken());
        }
        json.put("exportIntervalSeconds", obj.getExportIntervalSeconds());
    }
}

package io.neonbee.config;

import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link io.neonbee.config.AuthProviderConfig}. NOTE: This class has been automatically
 * generated from the {@link io.neonbee.config.AuthProviderConfig} original class using Vert.x codegen.
 */
public class AuthProviderConfigConverter {

    static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, AuthProviderConfig obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
            case "type":
                if (member.getValue() instanceof String) {
                    obj.setType(
                            io.neonbee.config.AuthProviderConfig.AuthProviderType.valueOf((String) member.getValue()));
                }
                break;
            }
        }
    }

    static void toJson(AuthProviderConfig obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    static void toJson(AuthProviderConfig obj, java.util.Map<String, Object> json) {
        if (obj.getType() != null) {
            json.put("type", obj.getType().name());
        }
    }
}

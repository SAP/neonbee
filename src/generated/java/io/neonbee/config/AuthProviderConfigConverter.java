package io.neonbee.config;

import java.util.Base64;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.impl.JsonUtil;

/**
 * Converter and mapper for {@link io.neonbee.config.AuthProviderConfig}. NOTE: This class has been automatically
 * generated from the {@link io.neonbee.config.AuthProviderConfig} original class using Vert.x codegen.
 */
public class AuthProviderConfigConverter {

    private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;

    private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

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

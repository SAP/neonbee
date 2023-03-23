package io.neonbee.config;

import java.util.Base64;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.impl.JsonUtil;

/**
 * Converter and mapper for {@link io.neonbee.config.CorsConfig}. NOTE: This class has been automatically generated from
 * the {@link io.neonbee.config.CorsConfig} original class using Vert.x codegen.
 */
public class CorsConfigConverter {

    private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;

    private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

    static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, CorsConfig obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
            case "allowCredentials":
                if (member.getValue() instanceof Boolean) {
                    obj.setAllowCredentials((Boolean) member.getValue());
                }
                break;
            case "allowedHeaders":
                if (member.getValue() instanceof JsonArray) {
                    java.util.LinkedHashSet<java.lang.String> list = new java.util.LinkedHashSet<>();
                    ((Iterable<Object>) member.getValue()).forEach(item -> {
                        if (item instanceof String)
                            list.add((String) item);
                    });
                    obj.setAllowedHeaders(list);
                }
                break;
            case "allowedMethods":
                if (member.getValue() instanceof JsonArray) {
                    java.util.LinkedHashSet<java.lang.String> list = new java.util.LinkedHashSet<>();
                    ((Iterable<Object>) member.getValue()).forEach(item -> {
                        if (item instanceof String)
                            list.add((String) item);
                    });
                    obj.setAllowedMethods(list);
                }
                break;
            case "enabled":
                if (member.getValue() instanceof Boolean) {
                    obj.setEnabled((Boolean) member.getValue());
                }
                break;
            case "exposedHeaders":
                if (member.getValue() instanceof JsonArray) {
                    java.util.LinkedHashSet<java.lang.String> list = new java.util.LinkedHashSet<>();
                    ((Iterable<Object>) member.getValue()).forEach(item -> {
                        if (item instanceof String)
                            list.add((String) item);
                    });
                    obj.setExposedHeaders(list);
                }
                break;
            case "maxAgeSeconds":
                if (member.getValue() instanceof Number) {
                    obj.setMaxAgeSeconds(((Number) member.getValue()).intValue());
                }
                break;
            case "origins":
                if (member.getValue() instanceof JsonArray) {
                    java.util.ArrayList<java.lang.String> list = new java.util.ArrayList<>();
                    ((Iterable<Object>) member.getValue()).forEach(item -> {
                        if (item instanceof String)
                            list.add((String) item);
                    });
                    obj.setOrigins(list);
                }
                break;
            case "relativeOrigins":
                if (member.getValue() instanceof JsonArray) {
                    java.util.ArrayList<java.lang.String> list = new java.util.ArrayList<>();
                    ((Iterable<Object>) member.getValue()).forEach(item -> {
                        if (item instanceof String)
                            list.add((String) item);
                    });
                    obj.setRelativeOrigins(list);
                }
                break;
            }
        }
    }

    static void toJson(CorsConfig obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    static void toJson(CorsConfig obj, java.util.Map<String, Object> json) {
        json.put("allowCredentials", obj.getAllowCredentials());
        if (obj.getAllowedHeaders() != null) {
            JsonArray array = new JsonArray();
            obj.getAllowedHeaders().forEach(item -> array.add(item));
            json.put("allowedHeaders", array);
        }
        if (obj.getAllowedMethods() != null) {
            JsonArray array = new JsonArray();
            obj.getAllowedMethods().forEach(item -> array.add(item));
            json.put("allowedMethods", array);
        }
        json.put("enabled", obj.isEnabled());
        if (obj.getExposedHeaders() != null) {
            JsonArray array = new JsonArray();
            obj.getExposedHeaders().forEach(item -> array.add(item));
            json.put("exposedHeaders", array);
        }
        json.put("maxAgeSeconds", obj.getMaxAgeSeconds());
        if (obj.getOrigins() != null) {
            JsonArray array = new JsonArray();
            obj.getOrigins().forEach(item -> array.add(item));
            json.put("origins", array);
        }
        if (obj.getRelativeOrigins() != null) {
            JsonArray array = new JsonArray();
            obj.getRelativeOrigins().forEach(item -> array.add(item));
            json.put("relativeOrigins", array);
        }
    }
}

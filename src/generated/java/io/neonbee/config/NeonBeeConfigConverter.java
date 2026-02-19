package io.neonbee.config;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link io.neonbee.config.NeonBeeConfig}. NOTE: This class has been automatically generated
 * from the {@link io.neonbee.config.NeonBeeConfig} original class using Vert.x codegen.
 */
public class NeonBeeConfigConverter {

    static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, NeonBeeConfig obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
            case "metricsConfig":
                if (member.getValue() instanceof JsonObject) {
                    obj.setMetricsConfig(
                            new io.neonbee.config.MetricsConfig((io.vertx.core.json.JsonObject) member.getValue()));
                }
                break;
            case "healthConfig":
                if (member.getValue() instanceof JsonObject) {
                    obj.setHealthConfig(
                            new io.neonbee.config.HealthConfig((io.vertx.core.json.JsonObject) member.getValue()));
                }
                break;
            case "eventBusTimeout":
                if (member.getValue() instanceof Number) {
                    obj.setEventBusTimeout(((Number) member.getValue()).intValue());
                }
                break;
            case "deploymentTimeout":
                if (member.getValue() instanceof Number) {
                    obj.setDeploymentTimeout(((Number) member.getValue()).intValue());
                }
                break;
            case "modelsDeploymentTimeout":
                if (member.getValue() instanceof Number) {
                    obj.setModelsDeploymentTimeout(((Number) member.getValue()).intValue());
                }
                break;
            case "moduleDeploymentTimeout":
                if (member.getValue() instanceof Number) {
                    obj.setModuleDeploymentTimeout(((Number) member.getValue()).intValue());
                }
                break;
            case "verticleDeploymentTimeout":
                if (member.getValue() instanceof Number) {
                    obj.setVerticleDeploymentTimeout(((Number) member.getValue()).intValue());
                }
                break;
            case "defaultThreadingModel":
                if (member.getValue() instanceof String) {
                    obj.setDefaultThreadingModel(io.vertx.core.ThreadingModel.valueOf((String) member.getValue()));
                }
                break;
            case "eventBusCodecs":
                if (member.getValue() instanceof JsonObject) {
                    java.util.Map<String, java.lang.String> map = new java.util.LinkedHashMap<>();
                    ((Iterable<java.util.Map.Entry<String, Object>>) member.getValue()).forEach(entry -> {
                        if (entry.getValue() instanceof String)
                            map.put(entry.getKey(), (String) entry.getValue());
                    });
                    obj.setEventBusCodecs(map);
                }
                break;
            case "trackingDataHandlingStrategy":
                if (member.getValue() instanceof String) {
                    obj.setTrackingDataHandlingStrategy((String) member.getValue());
                }
                break;
            case "platformClasses":
                if (member.getValue() instanceof JsonArray) {
                    java.util.ArrayList<java.lang.String> list = new java.util.ArrayList<>();
                    ((Iterable<Object>) member.getValue()).forEach(item -> {
                        if (item instanceof String)
                            list.add((String) item);
                    });
                    obj.setPlatformClasses(list);
                }
                break;
            case "timeZone":
                if (member.getValue() instanceof String) {
                    obj.setTimeZone((String) member.getValue());
                }
                break;
            case "micrometerRegistries":
                if (member.getValue() instanceof JsonArray) {
                    java.util.ArrayList<io.neonbee.config.MicrometerRegistryConfig> list = new java.util.ArrayList<>();
                    ((Iterable<Object>) member.getValue()).forEach(item -> {
                        if (item instanceof JsonObject)
                            list.add(new io.neonbee.config.MicrometerRegistryConfig(
                                    (io.vertx.core.json.JsonObject) item));
                    });
                    obj.setMicrometerRegistries(list);
                }
                break;
            case "jsonMaxStringSize":
                if (member.getValue() instanceof Number) {
                    obj.setJsonMaxStringSize(((Number) member.getValue()).intValue());
                }
                break;
            }
        }
    }

    static void toJson(NeonBeeConfig obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    static void toJson(NeonBeeConfig obj, java.util.Map<String, Object> json) {
        if (obj.getMetricsConfig() != null) {
            json.put("metricsConfig", obj.getMetricsConfig().toJson());
        }
        if (obj.getHealthConfig() != null) {
            json.put("healthConfig", obj.getHealthConfig().toJson());
        }
        json.put("eventBusTimeout", obj.getEventBusTimeout());
        json.put("deploymentTimeout", obj.getDeploymentTimeout());
        if (obj.getModelsDeploymentTimeout() != null) {
            json.put("modelsDeploymentTimeout", obj.getModelsDeploymentTimeout());
        }
        if (obj.getModuleDeploymentTimeout() != null) {
            json.put("moduleDeploymentTimeout", obj.getModuleDeploymentTimeout());
        }
        if (obj.getVerticleDeploymentTimeout() != null) {
            json.put("verticleDeploymentTimeout", obj.getVerticleDeploymentTimeout());
        }
        if (obj.getDefaultThreadingModel() != null) {
            json.put("defaultThreadingModel", obj.getDefaultThreadingModel().name());
        }
        if (obj.getEventBusCodecs() != null) {
            JsonObject map = new JsonObject();
            obj.getEventBusCodecs().forEach((key, value) -> map.put(key, value));
            json.put("eventBusCodecs", map);
        }
        if (obj.getTrackingDataHandlingStrategy() != null) {
            json.put("trackingDataHandlingStrategy", obj.getTrackingDataHandlingStrategy());
        }
        if (obj.getPlatformClasses() != null) {
            JsonArray array = new JsonArray();
            obj.getPlatformClasses().forEach(item -> array.add(item));
            json.put("platformClasses", array);
        }
        if (obj.getTimeZone() != null) {
            json.put("timeZone", obj.getTimeZone());
        }
        if (obj.getMicrometerRegistries() != null) {
            JsonArray array = new JsonArray();
            obj.getMicrometerRegistries().forEach(item -> array.add(item.toJson()));
            json.put("micrometerRegistries", array);
        }
        json.put("jsonMaxStringSize", obj.getJsonMaxStringSize());
    }
}

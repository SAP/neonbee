package io.neonbee.data.internal;

import java.time.LocalTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.neonbee.data.DataContext.DataVerticleCoordinate;

public class DataVerticleCoordinateImpl implements DataVerticleCoordinate {
    private final String qualifiedName;

    private final String requestTimestamp;

    private String deploymentId;

    private String ipAddress;

    private String responseTimestamp;

    @JsonCreator
    DataVerticleCoordinateImpl(@JsonProperty("qualifiedName") String qualifiedName) {
        this.qualifiedName = qualifiedName;
        this.requestTimestamp = LocalTime.now(ZoneId.systemDefault()).toString();
    }

    @Override
    public String getRequestTimestamp() {
        return requestTimestamp;
    }

    @Override
    public String getResponseTimestamp() {
        return responseTimestamp;
    }

    @Override
    public String getQualifiedName() {
        return qualifiedName;
    }

    @Override
    public String getIpAddress() {
        return ipAddress;
    }

    DataVerticleCoordinateImpl updateResponseTimestamp() {
        this.responseTimestamp = LocalTime.now(ZoneId.systemDefault()).toString();
        return this;
    }

    DataVerticleCoordinateImpl setDeploymentId(String instanceId) {
        this.deploymentId = instanceId;
        return this;
    }

    DataVerticleCoordinateImpl setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    @Override
    public String getDeploymentId() {
        return deploymentId;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (requestTimestamp != null) {
            builder.append(requestTimestamp).append(' ');
        }
        builder.append(qualifiedName);
        if (deploymentId != null) {
            builder.append('[').append(deploymentId).append(']');
        }
        if (ipAddress != null) {
            builder.append('@').append(ipAddress);
        }
        if (responseTimestamp != null) {
            builder.append(' ').append(responseTimestamp);
        }
        return builder.toString();
    }
}

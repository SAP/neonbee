package io.neonbee.internal.cluster.discovery;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.config.properties.PropertyTypeConverter;
import com.hazelcast.config.properties.SimplePropertyDefinition;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.DiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryStrategyFactory;

/**
 * DNS discovery strategy factory.
 */
public class DomainNameServiceDiscoveryStrategyFactory implements DiscoveryStrategyFactory {
    private static final PropertyDefinition DNS_NAME_PROPERTY =
            new SimplePropertyDefinition("DNS_NAME", true, PropertyTypeConverter.STRING);

    @Override
    public Class<? extends DiscoveryStrategy> getDiscoveryStrategyType() {
        return DomainNameServiceDiscoveryStrategy.class;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public DiscoveryStrategy newDiscoveryStrategy(DiscoveryNode discoveryNode, ILogger logger,
            Map<String, Comparable> properties) {
        return new DomainNameServiceDiscoveryStrategy(discoveryNode, logger, properties);
    }

    @Override
    public Collection<PropertyDefinition> getConfigurationProperties() {
        return Collections.unmodifiableCollection(Arrays.asList(DNS_NAME_PROPERTY));
    }
}

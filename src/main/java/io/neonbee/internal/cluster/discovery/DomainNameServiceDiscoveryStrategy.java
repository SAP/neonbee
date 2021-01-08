package io.neonbee.internal.cluster.discovery;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.cluster.Address;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.AbstractDiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;

/**
 * DNS-based discovery strategy for cloud foundry deployment.
 *
 * This strategy resolves a CF-internal DNS name and uses the IPs to build the cluster.
 *
 * The cloud foundry DNS-internal route must be created with
 *
 * cf map-route {appname} apps.internal --hostname {hostname}
 */
public class DomainNameServiceDiscoveryStrategy extends AbstractDiscoveryStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainNameServiceDiscoveryStrategy.class);

    private final DiscoveryNode discoveryNode;

    private final String dnsName;

    private int port;

    DomainNameServiceDiscoveryStrategy(DiscoveryNode discoveryNode, ILogger logger,
            @SuppressWarnings("rawtypes") Map<String, Comparable> properties) {
        super(logger, properties);
        this.discoveryNode = discoveryNode;
        this.dnsName = (String) properties.get("DNS_NAME");
    }

    @Override
    public void start() {
        this.port = discoveryNode.getPrivateAddress().getPort();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Start at " + discoveryNode.getPrivateAddress());
        }
    }

    @Override
    public Iterable<DiscoveryNode> discoverNodes() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Discover Nodes at {}.", discoveryNode.getPrivateAddress());
        }
        try {
            Set<String> ips = Arrays.stream(InetAddress.getAllByName(dnsName)).map(adr -> adr.getHostAddress())
                    .collect(Collectors.toSet());
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Resolved IPs: " + String.join(";", ips));
            }
            return ips.stream().map(ip -> {
                try {
                    return new SimpleDiscoveryNode(new Address(ip, port));
                } catch (UnknownHostException e) {
                    if (LOGGER.isWarnEnabled()) {
                        LOGGER.warn("Error while building address with IP " + ip);
                    }
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toSet());
        } catch (UnknownHostException e) {
            LOGGER.error("Error while resolving DNS name {}.", e, dnsName);
            return Collections.<DiscoveryNode>emptyList();
        }
    }

    @Override
    public void destroy() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Destroy at " + discoveryNode.getPrivateAddress());
        }
    }
}


package io.neonbee;

import static io.neonbee.internal.helper.StringHelper.EMPTY;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Flag annotation for NeonBee deployables
 *
 * NeonBee will scan for this interface during build and start-up, in order to deploy verticle flagged with this
 * annotation. Also the NeonBee default build (as provided with any sample project) will scan for this annotation during
 * build time and create a respective entry in the JAR manifest file.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NeonBeeDeployable {
    /**
     * The name space for NeonBee system verticles.
     */
    String NEONBEE_NAMESPACE = "neonbee";

    /**
     * The namespace of this deployable
     * <p>
     * Must be a forward slash separated lower-case string of unlimited length, using only latin letters and numbers.
     *
     * @return The namespace
     */
    String namespace() default EMPTY;

    /**
     * Profile filter for the deployable.
     *
     * @return the neonbee profile
     */
    NeonBeeProfile profile() default NeonBeeProfile.INCUBATOR;

    /**
     * Whether the verticle should be auto deployed. A system or an unstable verticle should set this flag to false.
     *
     * @return true if auto-deploy is active
     */
    boolean autoDeploy() default true;
}

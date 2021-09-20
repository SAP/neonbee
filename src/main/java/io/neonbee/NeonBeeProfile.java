package io.neonbee;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.base.Strings;

/**
 * NeonBee deployment profile.
 */
@SuppressWarnings("checkstyle:JavadocVariable")
public enum NeonBeeProfile {
    WEB, CORE, STABLE, INCUBATOR, NO_WEB, ALL;

    /**
     * Returns true if this profile is inside the passed active profiles.
     *
     * @param activeProfiles The active profiles
     * @return true if this profile is active
     */
    public boolean isActive(Collection<NeonBeeProfile> activeProfiles) {
        if (activeProfiles.isEmpty() || (activeProfiles.contains(NO_WEB) && this == WEB)) {
            return false;
        }

        return activeProfiles.contains(ALL) || activeProfiles.contains(this) || this == ALL;
    }

    /**
     * Parses a comma separated string of profile values.
     *
     * @param values string with profile values
     * @return a List with the parsed {@link NeonBeeProfile}s
     */
    public static Set<NeonBeeProfile> parseProfiles(String values) {
        return Optional.ofNullable(values).map(Strings::emptyToNull)
                .map(nonEmptyValues -> Arrays.stream(nonEmptyValues.split(",")).map(value -> {
                    try {
                        return NeonBeeProfile.valueOf(value);
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toSet())).filter(Predicate.not(Collection::isEmpty))
                .orElse(Set.of(ALL));
    }
}

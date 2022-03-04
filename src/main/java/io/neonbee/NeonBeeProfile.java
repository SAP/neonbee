package io.neonbee;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.base.Enums;

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
     * Similar to {@link #valueOf(String)}, but returning {@code null} instead of throwing an exception if the profile
     * is not found.
     *
     * @param profile the profile to get
     * @return the {@link NeonBeeProfile} or {@code null}
     */
    public static NeonBeeProfile getProfile(String profile) {
        return Enums.getIfPresent(NeonBeeProfile.class, profile).orNull();
    }

    /**
     * Parses a comma separated string of profiles.
     *
     * @param profiles a comma separated string of profiles
     * @return a List with the parsed {@link NeonBeeProfile}s
     */
    public static Set<NeonBeeProfile> parseProfiles(String profiles) {
        return Optional.ofNullable(profiles)
                .map(nonNullProfiles -> Arrays.stream(nonNullProfiles.split(",")).filter(Predicate.not(String::isBlank))
                        .map(NeonBeeProfile::getProfile).filter(Objects::nonNull).collect(Collectors.toSet()))
                .filter(Predicate.not(Collection::isEmpty)).orElse(Set.of(ALL));
    }
}

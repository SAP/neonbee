package io.neonbee;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NeonBee deployment profile.
 */
class NeonBeeProfileTest {

    @Test
    @DisplayName("is profile active")
    void isActive() {
        assertThat(NeonBeeProfile.CORE.isActive(List.<NeonBeeProfile>of(NeonBeeProfile.ALL))).isTrue();
        assertThat(NeonBeeProfile.STABLE.isActive(List.<NeonBeeProfile>of(NeonBeeProfile.ALL))).isTrue();
        assertThat(NeonBeeProfile.CORE.isActive(List.<NeonBeeProfile>of(NeonBeeProfile.CORE))).isTrue();
        assertThat(NeonBeeProfile.STABLE.isActive(List.<NeonBeeProfile>of(NeonBeeProfile.STABLE, NeonBeeProfile.CORE)))
                .isTrue();
        assertThat(
                NeonBeeProfile.INCUBATOR.isActive(List.<NeonBeeProfile>of(NeonBeeProfile.STABLE, NeonBeeProfile.ALL)))
                        .isTrue();
        assertThat(
                NeonBeeProfile.CORE.isActive(List.<NeonBeeProfile>of(NeonBeeProfile.STABLE, NeonBeeProfile.INCUBATOR)))
                        .isFalse();
        assertThat(NeonBeeProfile.ALL.isActive(List.<NeonBeeProfile>of(NeonBeeProfile.STABLE))).isTrue();
    }

    @Test
    void parseActiveProfile() {
        List<NeonBeeProfile> profiles = NeonBeeProfile.parseProfiles("");
        assertThat(profiles).contains(NeonBeeProfile.ALL);
        assertThat(profiles).hasSize(1);
        profiles = NeonBeeProfile.parseProfiles("CORE");
        assertThat(profiles).contains(NeonBeeProfile.CORE);
        assertThat(profiles).hasSize(1);
        profiles = NeonBeeProfile.parseProfiles("CORE,STABLE");
        assertThat(profiles).contains(NeonBeeProfile.CORE);
        assertThat(profiles).contains(NeonBeeProfile.STABLE);
        assertThat(profiles).hasSize(2);
        profiles = NeonBeeProfile.parseProfiles("CORE,anything");
        assertThat(profiles).contains(NeonBeeProfile.CORE);
        assertThat(profiles).hasSize(1);
        profiles = NeonBeeProfile.parseProfiles("anything");
        assertThat(profiles).contains(NeonBeeProfile.ALL);
        assertThat(profiles).hasSize(1);
        profiles = NeonBeeProfile.parseProfiles("");
        assertThat(profiles).contains(NeonBeeProfile.ALL);
        assertThat(profiles).hasSize(1);
    }
}

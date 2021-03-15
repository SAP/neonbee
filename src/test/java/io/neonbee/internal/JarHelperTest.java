package io.neonbee.internal;

import static com.google.common.truth.Truth.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

class JarHelperTest {

    @Test
    void extractFilePathTest() {
        String rawUriString = "jar:file:///path/to.jar!/my/package/name/MyClass.class";
        URI uri = URI.create(rawUriString);
        assertThat(JarHelper.extractFilePath(uri)).isEqualTo("my/package/name/MyClass.class");
    }
}

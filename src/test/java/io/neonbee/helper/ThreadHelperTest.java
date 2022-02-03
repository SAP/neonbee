package io.neonbee.helper;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import io.neonbee.helper.ThreadHelperTest.InnerClass.InnerInnerClass;
import io.neonbee.internal.helper.ThreadHelper;

@Isolated
class ThreadHelperTest {
    @Test
    void testGetClassLoader() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            assertThat(ThreadHelper.getClassLoader()).isEqualTo(contextClassLoader); // NOPMD

            Thread.currentThread().setContextClassLoader(null);

            assertThat(ThreadHelper.getClassLoader()).isEqualTo(getClass().getClassLoader()); // NOPMD
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    @Test
    void testGetOwnClass() {
        assertThat(ThreadHelper.getOwnClass()).isEqualTo(ThreadHelperTest.class);
        assertThat(getOwnClassStatic()).isEqualTo(ThreadHelperTest.class);
        assertThat(InnerClass.getOwnClassStatic()).isEqualTo(InnerClass.class);
        assertThat(new InnerClass().getOwnClass()).isEqualTo(InnerClass.class);
        assertThat(new InnerClass().getOwnClassFromInnerClass()).isEqualTo(InnerInnerClass.class);
        assertThat(new InnerClass().new InnerInnerClass().getOwnClass()).isEqualTo(InnerInnerClass.class);
        assertThat(new InnerClass().new InnerInnerClass().alsoGetOwnClass()).isEqualTo(InnerInnerClass.class);
    }

    @Test
    void testGetCallingClass() {
        assertThat(ThreadHelper.getCallingClass().getPackageName()).startsWith("org.junit");
        assertThat(getCallingClassStatic().getPackageName()).startsWith("org.junit");
        assertThat(InnerClass.getCallingClassStatic()).isEqualTo(ThreadHelperTest.class);
        assertThat(new InnerClass().getCallingClass()).isEqualTo(ThreadHelperTest.class);
        assertThat(new InnerClass().getCallingClassFromInnerClass()).isEqualTo(InnerClass.class);
        assertThat(new InnerClass().new InnerInnerClass().getCallingClass()).isEqualTo(ThreadHelperTest.class);
        assertThat(new InnerClass().new InnerInnerClass().alsoGetCallingClass()).isEqualTo(ThreadHelperTest.class);
    }

    static Class<?> getOwnClassStatic() {
        return ThreadHelper.getOwnClass();
    }

    static Class<?> getCallingClassStatic() {
        return ThreadHelper.getCallingClass();
    }

    static class InnerClass {
        Class<?> getOwnClass() {
            return ThreadHelper.getOwnClass();
        }

        Class<?> getOwnClassFromInnerClass() {
            return new InnerInnerClass().getOwnClass();
        }

        Class<?> getCallingClass() {
            return ThreadHelper.getCallingClass();
        }

        Class<?> getCallingClassFromInnerClass() {
            return new InnerInnerClass().getCallingClass();
        }

        static Class<?> getOwnClassStatic() {
            return ThreadHelper.getOwnClass();
        }

        static Class<?> getCallingClassStatic() {
            return ThreadHelper.getCallingClass();
        }

        @SuppressWarnings("ClassCanBeStatic")
        class InnerInnerClass {
            Class<?> getOwnClass() {
                return ThreadHelper.getOwnClass();
            }

            Class<?> alsoGetOwnClass() {
                return getOwnClass();
            }

            Class<?> getCallingClass() {
                return ThreadHelper.getCallingClass();
            }

            Class<?> alsoGetCallingClass() {
                return getCallingClass();
            }
        }
    }
}

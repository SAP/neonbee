package io.neonbee.hook.internal;

import org.junit.jupiter.api.Test;

import io.neonbee.internal.BasicJar;
import io.neonbee.logging.LoggingFacade;
import io.neonbee.test.helper.ResourceHelper;

class NonVertxTest {

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @Test
    void classCompile() throws Exception {

        BasicJar jarWithHookAnnotation =
                new HookClassTemplate(ResourceHelper.TEST_RESOURCES.resolveRelated("CompileClassTest.java.template"),
                        "HodorHook", "hook").setMethodAnnotation("@Hook(HookType.ONCE_PER_REQUEST)").asJar();
        LOGGER.info("compilation scceeded" + jarWithHookAnnotation.toString());
    }
}

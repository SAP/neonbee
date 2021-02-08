package io.neonbee.internal;

import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.io.IOException;

public class DummyVerticleTemplate implements ClassTemplate {
    private static final String PLACEHOLDER_CLASS_NAME = "<VerticleClassName>";

    private static final String PLACEHOLDER_ADDRESS = "<address>";

    private static final String PLACEHOLDER_PACKAGE = "<package>";

    private final String simpleClassName;

    private final String ebAddress;

    private final String expectedResponse;

    private final String template;

    private final String packageName;

    /**
     * Creates a dummy Verticle
     *
     * @param simpleClassName The simple class name of the new verticle
     * @param ebAddress       The address on which the verticle is listening
     * @throws IOException Template file could not be read
     */
    public DummyVerticleTemplate(String simpleClassName, String ebAddress) throws IOException {
        this(simpleClassName, ebAddress, null);
    }

    /**
     * Creates a dummy Verticle
     *
     * @param simpleClassName The simple class name of the new verticle
     * @param ebAddress       The address on which the verticle is listening
     * @param packageName     The package name of the verticle. Pass null for default package
     * @throws IOException Template file could not be read
     */
    public DummyVerticleTemplate(String simpleClassName, String ebAddress, String packageName) throws IOException {
        this.simpleClassName = simpleClassName;
        this.ebAddress = ebAddress;
        this.expectedResponse = "Hello from: " + ebAddress;
        this.template = TEST_RESOURCES.getRelated("DummyVerticle.java.template").toString();
        this.packageName = packageName;
    }

    public String getEbAddress() {
        return ebAddress;
    }

    public String getExpectedResponse() {
        return expectedResponse;
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public String reifyTemplate() {
        String packageNameReplacement = "";
        if (packageName != null) {
            packageNameReplacement = "package " + packageName + ";";
        }

        return template.replace(PLACEHOLDER_CLASS_NAME, simpleClassName).replace(PLACEHOLDER_ADDRESS, ebAddress)
                .replace(PLACEHOLDER_PACKAGE, packageNameReplacement);
    }

    @Override
    public String getSimpleName() {
        return simpleClassName;
    }
}

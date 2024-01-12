package io.neonbee.internal.deploy;

import static io.neonbee.internal.helper.StringHelper.EMPTY;

import io.neonbee.NeonBee;

public abstract class Deployable {
    /**
     * Create a new {@link Deployable}.
     */
    protected Deployable() {
        // no initialization needed, however:
        // checkstyle suggests to create an empty constructor to explain the use of the class and
        // sonarcube is complaining if the constructor is empty and suggests to add (this) comment ;)
    }

    /**
     * The identifier of a deployable. In case of a single verticle, this might be the is the full qualified class name
     * of the verticle to deploy, in case of a module it contains the module name / version, in case of models, this
     * might be the name of the models to deploy.
     *
     * @return The identifier of this deployable
     */
    public abstract String getIdentifier();

    /**
     * Returns the type of deployable. If it is a {@link Deployable} returns "Generic Deployable", otherwise it returns
     * the specific type, e.g. "Verticle", "Models", or "Module".
     *
     * @return the type represented as a string, e.g. "Verticle", "Models", or "Module"
     */
    public String getType() {
        return getClass().getSimpleName().replace(Deployable.class.getSimpleName(), EMPTY);
    }

    /**
     * This method deploys this {@link Deployable} on the passed Vert.x instance.
     *
     * @param neonBee the NeonBee instance on which to deploy the {@link Deployable} to
     * @return A {@link PendingDeployment} of the {@link Deployable}
     */
    public abstract PendingDeployment deploy(NeonBee neonBee);

    @Override
    public String toString() {
        return getType() + "(" + getIdentifier() + ")";
    }
}

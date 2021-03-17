package io.neonbee.internal.processor.odata.expression;

import java.util.Comparator;
import java.util.List;

import org.apache.olingo.commons.api.data.Entity;

@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "SE_COMPARATOR_SHOULD_BE_SERIALIZABLE",
        justification = "Comparator is not serialized by Olingo and provided entityComparators list is not serializable anyways")
public class EntityChainedComparator implements Comparator<Entity> {
    private final List<EntityComparator> entityComparators;

    EntityChainedComparator(List<EntityComparator> entityComparators) {
        this.entityComparators = entityComparators;
    }

    @Override
    public int compare(Entity entity1, Entity entity2) {
        for (EntityComparator entityComparator : entityComparators) {
            int result = entityComparator.compare(entity1, entity2);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }
}

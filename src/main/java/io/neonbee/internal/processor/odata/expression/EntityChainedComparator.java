package io.neonbee.internal.processor.odata.expression;

import java.util.Comparator;
import java.util.List;

import org.apache.olingo.commons.api.data.Entity;

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

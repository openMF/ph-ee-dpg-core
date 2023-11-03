package org.mifos.pheedpgimporterrdbms.entity.transfer;

import jakarta.persistence.metamodel.SingularAttribute;
import java.util.Date;
import org.springframework.data.jpa.domain.Specification;

public final class TransferSpecs {

    private TransferSpecs() {}

    public static Specification<Transfer> between(SingularAttribute<Transfer, Date> attribute, Date from, Date to) {
        return (root, query, builder) -> builder.and(builder.greaterThanOrEqualTo(root.get(attribute), from),
                builder.lessThanOrEqualTo(root.get(attribute), to));
    }

    public static Specification<Transfer> later(SingularAttribute<Transfer, Date> attribute, Date from) {
        return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get(attribute), from);
    }

    public static Specification<Transfer> earlier(SingularAttribute<Transfer, Date> attribute, Date to) {
        return (root, query, builder) -> builder.lessThanOrEqualTo(root.get(attribute), to);
    }

    public static <T> Specification<Transfer> match(SingularAttribute<Transfer, T> attribute, T input) {
        return (root, query, builder) -> builder.equal(root.get(attribute), input);
    }
}

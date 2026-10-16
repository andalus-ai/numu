package tech.kayys.numou.crm.application.api.query;

import tech.kayys.numou.foundation.application.query.Query;

public record SearchCustomersQuery(
        String companyName,
        String email,
        String industry,
        String city,
        String country,
        int page,
        int size
) implements Query {}

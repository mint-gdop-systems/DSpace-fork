package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.PotentialDuplicatesRest;
import org.dspace.app.rest.projection.Projection;
import org.springframework.stereotype.Component;

@Component
public class PotentialDuplicatesConverter implements DSpaceConverter<PotentialDuplicatesRest, PotentialDuplicatesRest> {

    @Override
    public PotentialDuplicatesRest convert(PotentialDuplicatesRest modelObject, Projection projection) {
        // Projections can be applied here if necessary, or return as-is
        return modelObject;
    }

    @Override
    public Class<PotentialDuplicatesRest> getModelClass() {
        return PotentialDuplicatesRest.class;
    }
}
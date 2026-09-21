package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.PotentialDuplicatesRest;
import org.dspace.app.rest.utils.Utils;

public class PotentialDuplicatesResource extends DSpaceResource<PotentialDuplicatesRest> {

    public PotentialDuplicatesResource(PotentialDuplicatesRest data, Utils utils) {
        super(data, utils);
    }
}

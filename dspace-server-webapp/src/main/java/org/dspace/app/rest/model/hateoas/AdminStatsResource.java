package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.model.AdminStatsRest;
import org.dspace.app.rest.utils.Utils;

@RelNameDSpaceResource(AdminStatsRest.NAME)
public class AdminStatsResource extends DSpaceResource<AdminStatsRest> {

    public AdminStatsResource(AdminStatsRest data, Utils utils) {
        super(data, utils);
    }
}

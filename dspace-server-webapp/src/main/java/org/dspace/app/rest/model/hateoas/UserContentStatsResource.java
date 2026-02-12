package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.UserContentStatsRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

@RelNameDSpaceResource(UserContentStatsRest.NAME)
public class UserContentStatsResource extends DSpaceResource<UserContentStatsRest> {
    public UserContentStatsResource(UserContentStatsRest content, Utils utils) {
        super(content, utils);
    }
}
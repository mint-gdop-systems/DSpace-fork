package org.dspace.app.rest.model;

import java.util.List;

import org.dspace.app.rest.RestResourceController;

public class PotentialDuplicatesRest extends BaseObjectRest<String> {
    public static final String NAME = "potentialduplicates";
    public static final String CATEGORY = RestAddressableModel.STATISTICS;

    private List<PotentialDuplicateGroupRest> groups;

    public List<PotentialDuplicateGroupRest> getGroups() {
        return groups;
    }

    public void setGroups(List<PotentialDuplicateGroupRest> groups) {
        this.groups = groups;
    }

    @Override
    public String getType() {
        return NAME;
    }

    @Override
    public String getTypePlural() {
        return NAME;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public Class<RestResourceController> getController() {
        return RestResourceController.class;
    }
}
package org.dspace.app.rest.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PotentialDuplicateItemRest {
    private String handle;

    public PotentialDuplicateItemRest() {
    }

    public PotentialDuplicateItemRest(String handle) {
        this.handle = handle;
    }

    @JsonProperty("handle")
    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }
}
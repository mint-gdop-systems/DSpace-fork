package org.dspace.app.rest.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PotentialDuplicateGroupRest {
    private String signature;
    private List<PotentialDuplicateItemRest> items;

    public PotentialDuplicateGroupRest() {
    }

    public PotentialDuplicateGroupRest(String signature, List<PotentialDuplicateItemRest> items) {
        this.signature = signature;
        this.items = items;
    }

    @JsonProperty("signature")
    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @JsonProperty("items")
    public List<PotentialDuplicateItemRest> getItems() {
        return items;
    }

    public void setItems(List<PotentialDuplicateItemRest> items) {
        this.items = items;
    }
}
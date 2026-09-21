package org.dspace.app.rest.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.json.BucketJsonFacet;
import org.apache.solr.client.solrj.response.json.NestableJsonFacet;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.exception.RepositoryMethodNotImplementedException;
import org.dspace.app.rest.model.PotentialDuplicateGroupRest;
import org.dspace.app.rest.model.PotentialDuplicateItemRest;
import org.dspace.app.rest.model.PotentialDuplicatesRest;
import org.dspace.core.Context;
import org.dspace.discovery.SolrSearchCore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component(PotentialDuplicatesRest.CATEGORY + "." + PotentialDuplicatesRest.NAME)
public class PotentialDuplicatesRestRepository extends DSpaceRestRepository<PotentialDuplicatesRest, String> {

    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(PotentialDuplicatesRestRepository.class);

    private static final String JSON_FACET = "{"
            + "  \"duplicates\": {"
            + "    \"type\": \"terms\","
            + "    \"field\": \"deduplication_keyword\","
            + "    \"mincount\": 2,"
            + "    \"limit\": -1,"
            + "    \"facet\": {"
            + "      \"handles\": {\"type\": \"terms\", \"field\": \"handle\", \"limit\": -1}"
            + "    }"
            + "  }"
            + "}";

    @Autowired
    private SolrSearchCore solrSearchCore;

    @Override
    public PotentialDuplicatesRest findOne(Context context, String id) {
        throw new RepositoryMethodNotImplementedException("Not implemented", "findOne");
    }

    @Override
    public Page<PotentialDuplicatesRest> findAll(Context context, Pageable pageable) {
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @SearchRestMethod(name = "findPotentialDuplicates")
    public Page<PotentialDuplicatesRest> findDuplicates(Context context, Pageable pageable) {
        try {
            SolrQuery query = new SolrQuery("archived:true");
            query.addFilterQuery("deduplication_keyword:[* TO *]");
            query.setRows(0);
            query.setParam("json.facet", JSON_FACET);

            QueryResponse response = solrSearchCore.getSolr().query(query, solrSearchCore.REQUEST_METHOD);
            NestableJsonFacet facets = response.getJsonFacetingResponse();

            List<PotentialDuplicateGroupRest> groups = new ArrayList<>();
            if (facets != null && facets.getBucketBasedFacets("duplicates") != null) {
                for (BucketJsonFacet bucket : facets.getBucketBasedFacets("duplicates").getBuckets()) {
                    String signature = String.valueOf(bucket.getVal());

                    List<PotentialDuplicateItemRest> items = new ArrayList<>();
                    if (bucket.getBucketBasedFacets("handles") != null) {
                        for (BucketJsonFacet handleBucket : bucket.getBucketBasedFacets("handles").getBuckets()) {
                            items.add(new PotentialDuplicateItemRest(String.valueOf(handleBucket.getVal())));
                        }
                    }
                    groups.add(new PotentialDuplicateGroupRest(signature, items));
                }
            }

            PotentialDuplicatesRest rest = new PotentialDuplicatesRest();
            rest.setId("all");
            rest.setGroups(groups);

            List<PotentialDuplicatesRest> list = Collections.singletonList(rest);
            return new PageImpl<>(list, pageable, list.size());
        } catch (Exception e) {
            log.error("Failed to query potential duplicates", e);
            throw new RuntimeException("Failed to query potential duplicates", e);
        }
    }

    @Override
    public Class<PotentialDuplicatesRest> getDomainClass() {
        return PotentialDuplicatesRest.class;
    }
}
/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dao.impl;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import org.dspace.content.Bitstream;
import org.dspace.content.Bitstream_;
import org.dspace.content.Bundle;
import org.dspace.content.Bundle_;
import org.dspace.content.Collection;
import org.dspace.content.Collection_;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.Item_;
import org.dspace.content.dao.BitstreamDAO;
import org.dspace.core.AbstractHibernateDSODAO;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.UUIDIterator;
import org.dspace.eperson.EPerson;

/**
 * Hibernate implementation of the Database Access Object interface class for
 * the Bitstream object.
 * This class is responsible for all database calls for the Bitstream object and
 * is autowired by spring
 * This class should never be accessed directly.
 *
 * @author kevinvandevelde at atmire.com
 */
public class BitstreamDAOImpl extends AbstractHibernateDSODAO<Bitstream> implements BitstreamDAO {

    protected BitstreamDAOImpl() {
        super();
    }

    @Override
    public List<Bitstream> findDeletedBitstreams(Context context, int limit, int offset) throws SQLException {
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery criteriaQuery = getCriteriaQuery(criteriaBuilder, Bitstream.class);
        Root<Bitstream> bitstreamRoot = criteriaQuery.from(Bitstream.class);
        criteriaQuery.select(bitstreamRoot);
        criteriaQuery.orderBy(criteriaBuilder.desc(bitstreamRoot.get(Bitstream_.ID)));
        criteriaQuery.where(criteriaBuilder.equal(bitstreamRoot.get(Bitstream_.deleted), true));
        return list(context, criteriaQuery, false, Bitstream.class, limit, offset);

    }

    @Override
    public List<Bitstream> findDuplicateInternalIdentifier(Context context, Bitstream bitstream) throws SQLException {
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery criteriaQuery = getCriteriaQuery(criteriaBuilder, Bitstream.class);
        Root<Bitstream> bitstreamRoot = criteriaQuery.from(Bitstream.class);
        criteriaQuery.select(bitstreamRoot);
        criteriaQuery.where(criteriaBuilder.and(
                criteriaBuilder.equal(bitstreamRoot.get(Bitstream_.internalId), bitstream.getInternalId()),
                criteriaBuilder.notEqual(bitstreamRoot.get(Bitstream_.id), bitstream.getID())));
        return list(context, criteriaQuery, false, Bitstream.class, -1, -1);
    }

    @Override
    public List<Bitstream> findBitstreamsWithNoRecentChecksum(Context context) throws SQLException {
        Query query = createQuery(context, "SELECT b FROM MostRecentChecksum c RIGHT JOIN Bitstream b " +
                "ON c.bitstream = b WHERE c IS NULL");

        return query.getResultList();
    }

    @Override
    public Iterator<Bitstream> findByCommunity(Context context, Community community) throws SQLException {
        // Select UUID of all bitstreams, joining from Bitstream -> Bundle -> Item ->
        // Collection -> Community
        // to find all that exist under the given community.
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery<UUID> criteriaQuery = criteriaBuilder.createQuery(UUID.class);
        Root<Bitstream> bitstreamRoot = criteriaQuery.from(Bitstream.class);
        criteriaQuery.select(bitstreamRoot.get(Bitstream_.id));
        // Joins from Bitstream -> Bundle -> Item -> Collection
        Join<Bitstream, Bundle> joinBundle = bitstreamRoot.join(Bitstream_.bundles);
        Join<Bundle, Item> joinItem = joinBundle.join(Bundle_.items);
        Join<Item, Collection> joinCollection = joinItem.join(Item_.collections);
        // Where "community" is a member of the list of Communities linked by the
        // collection(s)
        criteriaQuery.where(criteriaBuilder.isMember(community, joinCollection.get(Collection_.COMMUNITIES)));

        // Transform into a query object to execute
        Query query = createQuery(context, criteriaQuery);
        @SuppressWarnings("unchecked")
        List<UUID> uuids = query.getResultList();
        return new UUIDIterator<Bitstream>(context, uuids, Bitstream.class, this);
    }

    @Override
    public Iterator<Bitstream> findByCollection(Context context, Collection collection) throws SQLException {
        // Select UUID of all bitstreams, joining from Bitstream -> Bundle -> Item ->
        // Collection
        // to find all that exist under the given collection.
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery<UUID> criteriaQuery = criteriaBuilder.createQuery(UUID.class);
        Root<Bitstream> bitstreamRoot = criteriaQuery.from(Bitstream.class);
        criteriaQuery.select(bitstreamRoot.get(Bitstream_.id));
        // Joins from Bitstream -> Bundle -> Item
        Join<Bitstream, Bundle> joinBundle = bitstreamRoot.join(Bitstream_.bundles);
        Join<Bundle, Item> joinItem = joinBundle.join(Bundle_.items);
        // Where "collection" is a member of the list of Collections linked by the
        // item(s)
        criteriaQuery.where(criteriaBuilder.isMember(collection, joinItem.get(Item_.collections)));

        // Transform into a query object to execute
        Query query = createQuery(context, criteriaQuery);
        @SuppressWarnings("unchecked")
        List<UUID> uuids = query.getResultList();
        return new UUIDIterator<Bitstream>(context, uuids, Bitstream.class, this);
    }

    @Override
    public Iterator<Bitstream> findByItem(Context context, Item item) throws SQLException {
        // Select UUID of all bitstreams, joining from Bitstream -> Bundle -> Item
        // to find all that exist under the given item.
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery<UUID> criteriaQuery = criteriaBuilder.createQuery(UUID.class);
        Root<Bitstream> bitstreamRoot = criteriaQuery.from(Bitstream.class);
        criteriaQuery.select(bitstreamRoot.get(Bitstream_.id));
        // Join from Bitstream -> Bundle
        Join<Bitstream, Bundle> joinBundle = bitstreamRoot.join(Bitstream_.bundles);
        // Where "item" is a member of the list of Items linked by the bundle(s)
        criteriaQuery.where(criteriaBuilder.isMember(item, joinBundle.get(Bundle_.items)));

        // Transform into a query object to execute
        Query query = createQuery(context, criteriaQuery);
        @SuppressWarnings("unchecked")
        List<UUID> uuids = query.getResultList();
        return new UUIDIterator<Bitstream>(context, uuids, Bitstream.class, this);
    }

    @Override
    public Iterator<Bitstream> findByStoreNumber(Context context, Integer storeNumber) throws SQLException {
        Query query = createQuery(context, "select b.id from Bitstream b where b.storeNumber = :storeNumber");
        query.setParameter("storeNumber", storeNumber);
        @SuppressWarnings("unchecked")
        List<UUID> uuids = query.getResultList();
        return new UUIDIterator<Bitstream>(context, uuids, Bitstream.class, this);
    }

    @Override
    public Long countByStoreNumber(Context context, Integer storeNumber) throws SQLException {

        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery<Long> criteriaQuery = criteriaBuilder.createQuery(Long.class);

        Root<Bitstream> bitstreamRoot = criteriaQuery.from(Bitstream.class);
        criteriaQuery.where(criteriaBuilder.equal(bitstreamRoot.get(Bitstream_.storeNumber), storeNumber));
        return countLong(context, criteriaQuery, criteriaBuilder, bitstreamRoot);
    }

    @Override
    public int countRows(Context context) throws SQLException {
        return count(createQuery(context, "SELECT count(*) from Bitstream"));
    }

    @Override
    public int countDeleted(Context context) throws SQLException {
        return count(createQuery(context, "SELECT count(*) FROM Bitstream b WHERE b.deleted=true"));
    }

    @Override
    public int countWithNoPolicy(Context context) throws SQLException {
        Query query = createQuery(context,
                "SELECT count(bit.id) from Bitstream bit where bit.deleted<>true and bit not in" +
                        " (select res.dSpaceObject from ResourcePolicy res where res.resourceTypeId = " +
                        ":typeId )");
        query.setParameter("typeId", Constants.BITSTREAM);
        return count(query);
    }

    @Override
    public List<Bitstream> getNotReferencedBitstreams(Context context) throws SQLException {
        return list(createQuery(context, "select bit from Bitstream bit where bit.deleted != true" +
                " and bit.id not in (select bit2.id from Bundle bun join bun.bitstreams bit2)" +
                " and bit.id not in (select com.logo.id from Community com)" +
                " and bit.id not in (select col.logo.id from Collection col)" +
                " and bit.id not in (select bun.primaryBitstream.id from Bundle bun)"));
    }

    @Override
    public Iterator<Bitstream> findAll(Context context, int limit, int offset) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        return findByX(context, Bitstream.class, map, true, limit, offset).iterator();

    }

    @Override
    public Iterator<Bitstream> findAll(Context context, int limit, int offset, EPerson submitter,
            LocalDate accessionDate, boolean selectImages) throws SQLException {
        String jpql = "select distinct b.id from Bitstream b "
                + "join b.bundles bundle "
                + "join bundle.items item "
                + "join b.bitstreamFormat bf "
                + "where b.deleted = false "
                + "  and (:submitter is null or item.submitter = :submitter) "
                + (accessionDate != null
                        ? "  and exists ("
                                + "    select 1 from MetadataValue mv "
                                + "    join mv.metadataField mf "
                                + "    join mf.metadataSchema ms "
                                + "    where mv.dSpaceObject = item "
                                + "      and ms.namespace = 'dc' "
                                + "      and mf.element = 'date' "
                                + "      and mf.qualifier = 'accessioned' "
                                + "      and mv.value like :accessionDatePrefix "
                                + "  ) "
                        : "")
                + (selectImages
                        ? "  and (bf.mimetype like 'image/%' "
                                + "    or bf.mimetype in ('application/pdf', 'application/postscript'))"
                        : "  and bf.mimetype in ('application/pdf', 'application/postscript') ")
                + " order by b.id";

        Query query = createQuery(context, jpql);
        query.setParameter("submitter", submitter);
        if (accessionDate != null) {
            // matches "2026-06-11T..." regardless of time component
            query.setParameter("accessionDatePrefix", accessionDate.toString() + "%");
        }
        if (limit > 0) {
            query.setFirstResult(offset);
            query.setMaxResults(limit);
        }

        @SuppressWarnings("unchecked")
        List<UUID> uuids = query.getResultList();
        return new UUIDIterator<>(context, uuids, Bitstream.class, this);
    }

    public List<Object[]> findCount(Context context, EPerson submitter) throws SQLException {
        String submitterCondition = submitter != null
                ? " AND i.submitter_id = :submitterId "
                : "";

        String complexSql = """
                WITH bitstream_data AS (
                    SELECT
                        b.uuid AS bitstream_id,

                        COALESCE(mv_entity.text_value, 'Other') AS entity_type,

                        CASE
                            WHEN wi.workspace_item_id IS NOT NULL THEN 'Draft'
                            WHEN wfi.workflowitem_id IS NOT NULL THEN 'Pending'
                            WHEN i.in_archive = TRUE THEN 'Approved'
                            ELSE 'Other'
                        END AS item_status,

                        CASE
                            WHEN bf.mimetype LIKE 'image/%' THEN 1
                            WHEN bf.mimetype = 'application/pdf'
                                THEN COALESCE(CAST(mv_pages.text_value AS INTEGER), 0)
                            ELSE 0
                        END AS page_count

                    FROM bitstream b
                    JOIN bitstreamformatregistry bf
                        ON b.bitstream_format_id = bf.bitstream_format_id
                    JOIN bundle2bitstream b2b
                        ON b.uuid = b2b.bitstream_id
                    JOIN item2bundle i2b
                        ON b2b.bundle_id = i2b.bundle_id
                    JOIN item i
                        ON i2b.item_id = i.uuid

                    LEFT JOIN workspaceitem wi
                        ON i.uuid = wi.item_id
                    LEFT JOIN cwf_workflowitem wfi
                        ON i.uuid = wfi.item_id

                    LEFT JOIN metadatavalue mv_pages
                        ON b.uuid = mv_pages.dspace_object_id
                        AND mv_pages.metadata_field_id = (
                            SELECT metadata_field_id
                            FROM metadatafieldregistry
                            WHERE element = 'document'
                              AND qualifier = 'pages'
                              AND metadata_schema_id = (
                                  SELECT metadata_schema_id
                                  FROM metadataschemaregistry
                                  WHERE short_id = 'crvs'
                              )
                        )

                    LEFT JOIN metadatavalue mv_entity
                        ON i.uuid = mv_entity.dspace_object_id
                        AND mv_entity.metadata_field_id = (
                            SELECT metadata_field_id
                            FROM metadatafieldregistry
                            WHERE element = 'entity'
                              AND qualifier = 'type'
                              AND metadata_schema_id = (
                                  SELECT metadata_schema_id
                                  FROM metadataschemaregistry
                                  WHERE short_id = 'dspace'
                              )
                        )

                    WHERE b.deleted = FALSE
                      AND (
                            bf.mimetype LIKE 'image/%'
                            OR bf.mimetype IN (
                                'application/pdf',
                                'application/postscript'
                            )
                      )
                            """ + submitterCondition + """
                )

                -- Total
                SELECT
                    'TOTAL' AS result_type,
                    NULL AS breakdown_key,
                    COUNT(DISTINCT bitstream_id) AS bitstream_count,
                    COALESCE(SUM(page_count), 0) AS total_pages
                FROM bitstream_data

                UNION ALL

                -- Entity type breakdown
                SELECT
                    'ENTITY_TYPE' AS result_type,
                    entity_type AS breakdown_key,
                    COUNT(DISTINCT bitstream_id) AS bitstream_count,
                    COALESCE(SUM(page_count), 0) AS total_pages
                FROM bitstream_data
                GROUP BY entity_type

                UNION ALL

                -- Item status breakdown
                SELECT
                    'ITEM_STATUS' AS result_type,
                    item_status AS breakdown_key,
                    COUNT(DISTINCT bitstream_id) AS bitstream_count,
                    COALESCE(SUM(page_count), 0) AS total_pages
                FROM bitstream_data
                GROUP BY item_status

                ORDER BY result_type, breakdown_key;
                                """;

        // Use createNativeQuery for native SQL
        Query query = getHibernateSession(context).createNativeQuery(complexSql);

        if (submitter != null) {
            query.setParameter("submitterId", submitter.getID());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        return results;
    }
}

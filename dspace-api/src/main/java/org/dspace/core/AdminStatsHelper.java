/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.core;

import org.hibernate.Session;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper to perform fast SQL aggregations for Admin Stats.
 */
public class AdminStatsHelper {

    public static int getTotalPageCount(Context context) throws SQLException {
        Session session = (Session) context.getDBConnection().getSession();
        String sql = "SELECT sum(cast(mv.text_value as integer)) " +
                     "FROM metadatavalue mv " +
                     "JOIN metadatafieldregistry mfr ON mv.metadata_field_id = mfr.metadata_field_id " +
                     "JOIN metadataschemaregistry msr ON mfr.metadata_schema_id = msr.metadata_schema_id " +
                     "JOIN bitstream b ON mv.dspace_object_id = b.uuid " +
                     "JOIN bundle2bitstream b2b ON b.uuid = b2b.bitstream_id " +
                     "JOIN item2bundle i2b ON b2b.bundle_id = i2b.bundle_id " +
                     "JOIN item i ON i2b.item_id = i.uuid " +
                     "WHERE msr.short_id = 'legal' AND mfr.element = 'document' AND mfr.qualifier = 'pageCount' " +
                     "AND i.in_archive = true";

        try {
            Object result = session.createNativeQuery(sql).getSingleResult();
            if (result != null && result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger(AdminStatsHelper.class).error("Error calculating total page count", e);
        }
        return 0;
    }

    public static Map<String, Integer> getCollectionPageCounts(Context context) throws SQLException {
        Session session = (Session) context.getDBConnection().getSession();
        String sql = "SELECT cast(c.uuid as varchar) as collection_id, sum(cast(mv.text_value as integer)) as page_count " +
                     "FROM metadatavalue mv " +
                     "JOIN metadatafieldregistry mfr ON mv.metadata_field_id = mfr.metadata_field_id " +
                     "JOIN metadataschemaregistry msr ON mfr.metadata_schema_id = msr.metadata_schema_id " +
                     "JOIN bitstream b ON mv.dspace_object_id = b.uuid " +
                     "JOIN bundle2bitstream b2b ON b.uuid = b2b.bitstream_id " +
                     "JOIN item2bundle i2b ON b2b.bundle_id = i2b.bundle_id " +
                     "JOIN item i ON i2b.item_id = i.uuid " +
                     "JOIN collection2item c2i ON i.uuid = c2i.item_id " +
                     "JOIN collection c ON c2i.collection_id = c.uuid " +
                     "WHERE msr.short_id = 'legal' AND mfr.element = 'document' AND mfr.qualifier = 'pageCount' " +
                     "AND i.in_archive = true " +
                     "GROUP BY c.uuid";

        Map<String, Integer> result = new HashMap<>();
        try {
            List<Object[]> rows = session.createNativeQuery(sql).getResultList();
            for (Object[] row : rows) {
                if (row[0] != null && row[1] != null) {
                    String collId = row[0].toString();
                    int count = ((Number) row[1]).intValue();
                    result.put(collId, count);
                }
            }
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger(AdminStatsHelper.class).error("Error calculating collection page counts", e);
        }
        return result;
    }

    public static int getTotalWorkflowCount(Context context) throws SQLException {
        Session session = (Session) context.getDBConnection().getSession();
        String sql = "SELECT count(*) FROM cwf_workflowitem";
        try {
            Object result = session.createNativeQuery(sql).getSingleResult();
            if (result != null && result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger(AdminStatsHelper.class).error("Error calculating total workflow count", e);
        }
        return 0;
    }

    public static Map<String, Integer> getCollectionWorkflowCounts(Context context) throws SQLException {
        Session session = (Session) context.getDBConnection().getSession();
        String sql = "SELECT cast(c.uuid as varchar) as collection_id, count(*) as workflow_count " +
                     "FROM cwf_workflowitem wi " +
                     "JOIN collection c ON wi.collection_id = c.uuid " +
                     "GROUP BY c.uuid";

        Map<String, Integer> result = new HashMap<>();
        try {
            List<Object[]> rows = session.createNativeQuery(sql).getResultList();
            for (Object[] row : rows) {
                if (row[0] != null && row[1] != null) {
                    String collId = row[0].toString();
                    int count = ((Number) row[1]).intValue();
                    result.put(collId, count);
                }
            }
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger(AdminStatsHelper.class).error("Error calculating collection workflow counts", e);
        }
        return result;
    }

    public static int getTotalWorkflowPageCount(Context context) throws SQLException {
        Session session = (Session) context.getDBConnection().getSession();
        String sql = "SELECT sum(cast(mv.text_value as integer)) " +
                     "FROM metadatavalue mv " +
                     "JOIN metadatafieldregistry mfr ON mv.metadata_field_id = mfr.metadata_field_id " +
                     "JOIN metadataschemaregistry msr ON mfr.metadata_schema_id = msr.metadata_schema_id " +
                     "JOIN bitstream b ON mv.dspace_object_id = b.uuid " +
                     "JOIN bundle2bitstream b2b ON b.uuid = b2b.bitstream_id " +
                     "JOIN item2bundle i2b ON b2b.bundle_id = i2b.bundle_id " +
                     "JOIN item i ON i2b.item_id = i.uuid " +
                     "JOIN cwf_workflowitem wi ON i.uuid = wi.item_id " +
                     "WHERE msr.short_id = 'legal' AND mfr.element = 'document' AND mfr.qualifier = 'pageCount'";

        try {
            Object result = session.createNativeQuery(sql).getSingleResult();
            if (result != null && result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger(AdminStatsHelper.class).error("Error calculating total workflow page count", e);
        }
        return 0;
    }

    public static Map<String, Integer> getCollectionWorkflowPageCounts(Context context) throws SQLException {
        Session session = (Session) context.getDBConnection().getSession();
        String sql = "SELECT cast(wi.collection_id as varchar) as collection_id, sum(cast(mv.text_value as integer)) as page_count " +
                     "FROM metadatavalue mv " +
                     "JOIN metadatafieldregistry mfr ON mv.metadata_field_id = mfr.metadata_field_id " +
                     "JOIN metadataschemaregistry msr ON mfr.metadata_schema_id = msr.metadata_schema_id " +
                     "JOIN bitstream b ON mv.dspace_object_id = b.uuid " +
                     "JOIN bundle2bitstream b2b ON b.uuid = b2b.bitstream_id " +
                     "JOIN item2bundle i2b ON b2b.bundle_id = i2b.bundle_id " +
                     "JOIN item i ON i2b.item_id = i.uuid " +
                     "JOIN cwf_workflowitem wi ON i.uuid = wi.item_id " +
                     "WHERE msr.short_id = 'legal' AND mfr.element = 'document' AND mfr.qualifier = 'pageCount' " +
                     "GROUP BY wi.collection_id";

        Map<String, Integer> result = new HashMap<>();
        try {
            List<Object[]> rows = session.createNativeQuery(sql).getResultList();
            for (Object[] row : rows) {
                if (row[0] != null && row[1] != null) {
                    String collId = row[0].toString();
                    int count = ((Number) row[1]).intValue();
                    result.put(collId, count);
                }
            }
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger(AdminStatsHelper.class).error("Error calculating collection workflow page counts", e);
        }
        return result;
    }
}

/*
 * Copyright 2016 Netflix, Inc.
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *        http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.metacat.usermetadata.mysql;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.netflix.metacat.common.server.model.Lookup;
import com.netflix.metacat.common.server.properties.Config;
import com.netflix.metacat.common.server.usermetadata.LookupService;
import com.netflix.metacat.common.server.usermetadata.UserMetadataServiceException;
import com.netflix.metacat.common.server.util.DBUtil;
import com.netflix.metacat.common.server.util.DataSourceManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Set;

/**
 * User metadata service impl using Mysql.
 */
@Slf4j
public class MySqlLookupService implements LookupService {
    private static final String SQL_GET_LOOKUP =
        "select id, name, type, created_by createdBy, last_updated_by lastUpdatedBy, date_created dateCreated,"
            + " last_updated lastUpdated from lookup where name=?";
    private static final String SQL_INSERT_LOOKUP =
        "insert into lookup( name, version, type, created_by, last_updated_by, date_created, last_updated)"
            + " values (?,0,?,?,?,now(),now())";
    private static final String SQL_INSERT_LOOKUP_VALUES =
        "insert into lookup_values( lookup_id, values_string) values (?,?)";
    private static final String SQL_DELETE_LOOKUP_VALUES =
        "delete from lookup_values where lookup_id=? and values_string in (%s)";
    private static final String SQL_GET_LOOKUP_VALUES =
        "select values_string value from lookup_values where lookup_id=?";
    private static final String SQL_GET_LOOKUP_VALUES_BY_NAME =
        "select lv.values_string value from lookup l, lookup_values lv where l.id=lv.lookup_id and l.name=?";
    private static final String STRING_TYPE = "string";
    private final Config config;
    private final DataSourceManager dataSourceManager;

    /**
     * Constructor.
     *
     * @param config            config
     * @param dataSourceManager datasource manager
     */
    public MySqlLookupService(final Config config, final DataSourceManager dataSourceManager) {
        this.config = Preconditions.checkNotNull(config, "config is required");
        this.dataSourceManager = Preconditions.checkNotNull(dataSourceManager, "dataSourceManager is required");
    }

    private DataSource getDataSource() {
        return dataSourceManager.get(MysqlUserMetadataService.NAME_DATASOURCE);
    }

    /**
     * Returns the lookup for the given <code>name</code>.
     *
     * @param name lookup name
     * @return lookup
     */
    @Override
    public Lookup get(final String name) {
        Lookup result;
        final Connection connection = DBUtil.getReadConnection(getDataSource());
        try {
            final ResultSetHandler<Lookup> handler = new BeanHandler<>(Lookup.class);
            result = new QueryRunner().query(connection, SQL_GET_LOOKUP, handler, name);
            if (result != null) {
                result.setValues(getValues(result.getId()));
            }
        } catch (Exception e) {
            final String message = String.format("Failed to get the lookup for name %s", name);
            log.error(message, e);
            throw new UserMetadataServiceException(message, e);
        } finally {
            DBUtil.closeReadConnection(connection);
        }
        return result;
    }

    /**
     * Returns the value of the lookup name.
     *
     * @param name lookup name
     * @return scalar lookup value
     */
    @Override
    public String getValue(final String name) {
        String result = null;
        final Set<String> values = getValues(name);
        if (values != null && values.size() > 0) {
            result = values.iterator().next();
        }
        return result;
    }

    /**
     * Returns the list of values of the lookup name.
     *
     * @param lookupId lookup id
     * @return list of lookup values
     */
    @Override
    public Set<String> getValues(final Long lookupId) {
        final Connection connection = DBUtil.getReadConnection(getDataSource());
        try {
            return new QueryRunner().query(connection, SQL_GET_LOOKUP_VALUES, rs -> {
                final Set<String> result = Sets.newHashSet();
                while (rs.next()) {
                    result.add(rs.getString("value"));
                }
                return result;
            }, lookupId);
        } catch (Exception e) {
            final String message = String.format("Failed to get the lookup values for id %s", lookupId);
            log.error(message, e);
            throw new UserMetadataServiceException(message, e);
        } finally {
            DBUtil.closeReadConnection(connection);
        }
    }

    /**
     * Returns the list of values of the lookup name.
     *
     * @param name lookup name
     * @return list of lookup values
     */
    @Override
    public Set<String> getValues(final String name) {
        final Connection connection = DBUtil.getReadConnection(getDataSource());
        try {
            return new QueryRunner().query(connection, SQL_GET_LOOKUP_VALUES_BY_NAME, rs -> {
                final Set<String> result = Sets.newHashSet();
                while (rs.next()) {
                    result.add(rs.getString("value"));
                }
                return result;
            }, name);
        } catch (Exception e) {
            final String message = String.format("Failed to get the lookup values for name %s", name);
            log.error(message, e);
            throw new UserMetadataServiceException(message, e);
        } finally {
            DBUtil.closeReadConnection(connection);
        }
    }

    /**
     * Saves the lookup value.
     *
     * @param name   lookup name
     * @param values multiple values
     * @return returns the lookup with the given name.
     */
    @Override
    public Lookup setValues(final String name, final Set<String> values) {
        Lookup lookup;
        try {
            final Connection conn = getDataSource().getConnection();
            try {
                lookup = findOrCreateLookupByName(name, conn);
                final Set<String> inserts;
                Set<String> deletes = Sets.newHashSet();
                final Set<String> lookupValues = lookup.getValues();
                if (lookupValues == null || lookupValues.isEmpty()) {
                    inserts = values;
                } else {
                    inserts = Sets.difference(values, lookupValues).immutableCopy();
                    deletes = Sets.difference(lookupValues, values).immutableCopy();
                }
                lookup.setValues(values);
                if (!inserts.isEmpty()) {
                    insertLookupValues(lookup.getId(), inserts, conn);
                }
                if (!deletes.isEmpty()) {
                    deleteLookupValues(lookup.getId(), deletes, conn);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.close();
            }
        } catch (SQLException e) {
            final String message = String.format("Failed to set the lookup values for name %s", name);
            log.error(message, e);
            throw new UserMetadataServiceException(message, e);
        }
        return lookup;
    }

    private void insertLookupValues(final Long id, final Set<String> inserts, final Connection conn)
        throws SQLException {
        final Object[][] params = new Object[inserts.size()][];
        final Iterator<String> iter = inserts.iterator();
        int index = 0;
        while (iter.hasNext()) {
            params[index++] = ImmutableList.of(id, iter.next()).toArray();
        }
        new QueryRunner().batch(conn, SQL_INSERT_LOOKUP_VALUES, params);
    }

    private void deleteLookupValues(final Long id, final Set<String> deletes, final Connection conn)
        throws SQLException {
        new QueryRunner().update(conn,
            String.format(SQL_DELETE_LOOKUP_VALUES, "'" + Joiner.on("','").skipNulls().join(deletes) + "'"), id);
    }

    private Lookup findOrCreateLookupByName(final String name, final Connection conn) throws SQLException {
        Lookup lookup = get(name);
        if (lookup == null) {
            final Object[] params = {
                name,
                STRING_TYPE,
                config.getLookupServiceUserAdmin(),
                config.getLookupServiceUserAdmin(),
            };
            final Long lookupId = new QueryRunner().insert(conn, SQL_INSERT_LOOKUP, new ScalarHandler<>(1), params);
            lookup = new Lookup();
            lookup.setName(name);
            lookup.setId(lookupId);
        }
        return lookup;
    }

    /**
     * Saves the lookup value.
     *
     * @param name   lookup name
     * @param values multiple values
     * @return returns the lookup with the given name.
     */
    @Override
    public Lookup addValues(final String name, final Set<String> values) {
        Lookup lookup;
        try {
            final Connection conn = getDataSource().getConnection();
            try {
                lookup = findOrCreateLookupByName(name, conn);
                final Set<String> inserts;
                final Set<String> lookupValues = lookup.getValues();
                if (lookupValues == null || lookupValues.isEmpty()) {
                    inserts = values;
                    lookup.setValues(values);
                } else {
                    inserts = Sets.difference(values, lookupValues);
                }
                if (!inserts.isEmpty()) {
                    insertLookupValues(lookup.getId(), inserts, conn);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.close();
            }
        } catch (SQLException e) {
            final String message = String.format("Failed to set the lookup values for name %s", name);
            log.error(message, e);
            throw new UserMetadataServiceException(message, e);
        }
        return lookup;
    }

    /**
     * Saves the lookup value.
     *
     * @param name  lookup name
     * @param value lookup value
     * @return returns the lookup with the given name.
     */
    @Override
    public Lookup setValue(final String name, final String value) {
        return setValues(name, Sets.newHashSet(value));
    }
}

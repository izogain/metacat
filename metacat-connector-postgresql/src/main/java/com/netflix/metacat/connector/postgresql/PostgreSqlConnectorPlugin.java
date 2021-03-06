/*
 *
 *  Copyright 2017 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.metacat.connector.postgresql;

import com.netflix.metacat.common.server.connectors.ConnectorFactory;
import com.netflix.metacat.common.server.connectors.ConnectorPlugin;
import com.netflix.metacat.common.server.connectors.ConnectorTypeConverter;

import com.netflix.metacat.common.server.properties.Config;
import com.netflix.spectator.api.Registry;
import lombok.NonNull;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Implementation of the ConnectorPlugin interface for PostgreSQL.
 *
 * @author tgianos
 * @since 1.0.0
 */
public class PostgreSqlConnectorPlugin implements ConnectorPlugin {

    private static final String CONNECTOR_TYPE = "postgresql";
    private static final PostgreSqlTypeConverter TYPE_CONVERTER = new PostgreSqlTypeConverter();

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        return CONNECTOR_TYPE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectorFactory create(
        @Nonnull @NonNull final Config config,
        @Nonnull @NonNull final String connectorName,
        @Nonnull @NonNull final Map<String, String> configuration,
        @Nonnull @NonNull final Registry registry
    ) {
        return new PostgreSqlConnectorFactory(connectorName, configuration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectorTypeConverter getTypeConverter() {
        return TYPE_CONVERTER;
    }
}

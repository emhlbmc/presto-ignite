package com.facebook.presto.plugin.ignite;

import com.facebook.presto.plugin.jdbc.BaseJdbcClient;
import com.facebook.presto.plugin.jdbc.BaseJdbcConfig;
import com.facebook.presto.plugin.jdbc.DriverConnectionFactory;
import com.facebook.presto.plugin.jdbc.JdbcConnectorId;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.apache.ignite.IgniteJdbcThinDriver;

import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import static java.util.Locale.ENGLISH;


public class IgniteClient extends BaseJdbcClient {

    private static final Logger log = Logger.getLogger(IgniteClient.class);

    @Inject
    public IgniteClient(JdbcConnectorId connectorId, BaseJdbcConfig config, IgniteConfig igniteConfig) {
        super(connectorId, config, "", new DriverConnectionFactory(new IgniteJdbcThinDriver(), config));

    }

    @Override
    public Set<String> getSchemaNames() {
        // for MySQL, we need to list catalogs instead of schemas
        try (Connection connection = connectionFactory.openConnection();
             ResultSet resultSet = connection.getMetaData().getCatalogs()) {
            ImmutableSet.Builder<String> schemaNames = ImmutableSet.builder();
            while (resultSet.next()) {
                String schemaName = resultSet.getString("1").toLowerCase(ENGLISH);
                schemaNames.add(schemaName);
            }
            return schemaNames.build();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }






}

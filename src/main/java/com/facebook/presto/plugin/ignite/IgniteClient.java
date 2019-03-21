package com.facebook.presto.plugin.ignite;

import com.facebook.presto.plugin.jdbc.*;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.SchemaTableName;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.apache.ignite.IgniteJdbcThinDriver;

import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.Locale.ENGLISH;
import static com.facebook.presto.spi.StandardErrorCode.NOT_SUPPORTED;
import static com.google.common.collect.Iterables.getOnlyElement;


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
             ResultSet resultSet = connection.getMetaData().getSchemas()) {
            ImmutableSet.Builder<String> schemaNames = ImmutableSet.builder();
            while (resultSet.next()) {
                String schemaName = resultSet.getString(1).toLowerCase(ENGLISH);
                schemaNames.add(schemaName);
            }
            return schemaNames.build();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<SchemaTableName> getTableNames(@Nullable String schema) {
        try (Connection connection = connectionFactory.openConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (metadata.storesUpperCaseIdentifiers() && (schema != null)) {
                schema = schema.toUpperCase();
            }
            try (ResultSet resultSet = getTables(connection, schema, null)) {
                ImmutableList.Builder<SchemaTableName> list = ImmutableList
                        .builder();
                while (resultSet.next()) {
                    list.add(getSchemaTableName(resultSet));
                }
                return list.build();
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected ResultSet getTables(Connection connection, String schemaName,
                                  String tableName) throws SQLException {
        //We filter just VIEW, TABLE and SYNONYM. For more table types: connection.getMetaData().getTableTypes()
        return connection.getMetaData().getTables(null, schemaName, tableName, new String[]{"VIEW", "TABLE"});
    }

    @Nullable
    @Override
    public JdbcTableHandle getTableHandle(SchemaTableName schemaTableName) {
        try (Connection connection = connectionFactory.openConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String jdbcSchemaName = schemaTableName.getSchemaName();
            String jdbcTableName = schemaTableName.getTableName();
            if (metadata.storesUpperCaseIdentifiers()) {
                jdbcSchemaName = jdbcSchemaName.toUpperCase();
                jdbcTableName = jdbcTableName.toUpperCase();
            }
            try (ResultSet resultSet = getTables(connection, jdbcSchemaName,
                    jdbcTableName)) {
                List<JdbcTableHandle> tableHandles = new ArrayList<>();
                while (resultSet.next()) {
                    tableHandles.add(new JdbcTableHandle(connectorId,
                            schemaTableName, resultSet.getString("TABLE_CAT"),
                            resultSet.getString("TABLE_SCHEM"), resultSet
                            .getString("TABLE_NAME")));
                }
                if (tableHandles.isEmpty()) {
                    return null;
                }
                if (tableHandles.size() > 1) {
                    throw new PrestoException(NOT_SUPPORTED,
                            "Multiple tables matched: " + schemaTableName);
                }
                return getOnlyElement(tableHandles);
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected SchemaTableName getSchemaTableName(ResultSet resultSet)
            throws SQLException {
        String tableSchema = resultSet.getString("TABLE_SCHEM");
        String tableName = resultSet.getString("TABLE_NAME");
        if (tableSchema != null) {
            tableSchema = tableSchema.toLowerCase();
        }
        if (tableName != null) {
            tableName = tableName.toLowerCase();
        }
        return new SchemaTableName(tableSchema, tableName);
    }


}

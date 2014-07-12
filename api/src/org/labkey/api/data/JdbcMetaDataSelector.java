package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 7/10/2014
 * Time: 8:52 AM
 */
public class JdbcMetaDataSelector extends NonSqlExecutingSelector<JdbcMetaDataSelector>
{
    private final ResultSetFactory _factory;
    private final DatabaseMetaData _dbmd;

    private JdbcMetaDataSelector(DbScope scope, Connection conn, DatabaseMetaData dbmd, JdbcMetaDataResultSetFactory factory)
    {
        super(scope, conn);
        _dbmd = dbmd;
        _factory = new InternalJdbcMetaDataResultSetFactory(factory);
    }

    protected JdbcMetaDataSelector(DbScope scope, Connection conn, JdbcMetaDataResultSetFactory factory) throws SQLException
    {
        this(scope, conn, conn.getMetaData(), factory);
    }

    protected JdbcMetaDataSelector(DbScope scope, DatabaseMetaData dbmd, JdbcMetaDataResultSetFactory factory)
    {
        this(scope, null, dbmd, factory);
    }

    @Override
    protected ResultSetFactory getStandardResultSetFactory()
    {
        return _factory;
    }

    @Override
    public TableResultSet getResultSet()
    {
        return handleResultSet(_factory, new ResultSetHandler<TableResultSet>()
        {
            @Override
            public TableResultSet handle(ResultSet rs, Connection conn) throws SQLException
            {
                return new ResultSetImpl(rs);
            }
        });
    }

    @Override
    protected JdbcMetaDataSelector getThis()
    {
        return this;
    }


    public interface JdbcMetaDataResultSetFactory
    {
        ResultSet getResultSet(DbScope scope, DatabaseMetaData dbmd) throws SQLException;
    }


    private class InternalJdbcMetaDataResultSetFactory implements ResultSetFactory
    {
        private final JdbcMetaDataResultSetFactory _factory;

        public InternalJdbcMetaDataResultSetFactory(JdbcMetaDataResultSetFactory factory)
        {
            _factory = factory;
        }

        @Override
        public final ResultSet getResultSet(Connection conn) throws SQLException
        {
            return _factory.getResultSet(getScope(), _dbmd);
        }

        @Override
        public final boolean shouldClose()
        {
            return false;
        }

        @Override
        public final void handleSqlException(SQLException e, @Nullable Connection conn)
        {
            throw getExceptionFramework().translate(getScope(), "JdbcMetaDataSelector", e);
        }

        private DbScope getScope()
        {
            return JdbcMetaDataSelector.this.getScope();
        }
    }
}

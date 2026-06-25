package com.smartlab.management.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Persists Java maps/lists into PostgreSQL JSONB columns for management entities.
 */
public class PostgresJsonbTypeHandler extends JacksonTypeHandler {

    public PostgresJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        PGobject value = new PGobject();
        value.setType("jsonb");
        value.setValue(toJson(parameter));
        ps.setObject(i, value);
    }
}


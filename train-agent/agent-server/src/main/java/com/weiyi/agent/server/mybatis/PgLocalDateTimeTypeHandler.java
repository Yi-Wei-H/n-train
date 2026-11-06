package com.weiyi.agent.server.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * PG TIMESTAMPTZ 列与 LocalDateTime 的桥接（agent 库时间列全为 TIMESTAMPTZ）。
 * PgJDBC 的 getObject(LocalDateTime.class) 对 timestamptz 直接抛异常（仅支持无时区 timestamp），
 * 而驱动默认映射对两种列型都返回 java.sql.Timestamp——读取改走该路线再转 LocalDateTime；
 * 写入保持 setObject 原语义（timestamptz 由服务器按会话时区解释，与既有数据一致）。
 * 经 type-handlers-package 注册，全局覆盖 MyBatis 内置 LocalDateTime 处理器。
 */
@MappedTypes(LocalDateTime.class)
public class PgLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toLocalDateTime(rs.getObject(columnName));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toLocalDateTime(rs.getObject(columnIndex));
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toLocalDateTime(cs.getObject(columnIndex));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toLocalDateTime();
        }
        throw new IllegalArgumentException("无法转换为 LocalDateTime：" + value.getClass().getName());
    }
}

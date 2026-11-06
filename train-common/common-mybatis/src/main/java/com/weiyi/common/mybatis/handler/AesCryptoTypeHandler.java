package com.weiyi.common.mybatis.handler;

import com.weiyi.common.core.util.AesUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 负责服务与数据库之间的加/解密
 * <p>
 * 注意：MyBatis-Plus 条件构造器 where 条件不走 TypeHandler，等值查询参数需手动 AesUtil.encrypt。
 * <p>
 * 原因：MyBatis 里参数用不用自定义 TypeHandler,取决于生成 SQL 时有没有把 handler 信息
 * 写进占位符表达式，而不是实体字段上标了什么注解:`@TableField(typeHandler=...)`是 MP
 * 的实体元数据，MP 生成 insert/update 的 SET 子句时知道占位符对应哪个字段，会显式内嵌
 * `#{et.idCardNo,typeHandler=...}`;查询读取时则由自动生成的 resultMap 把列和handler
 * 绑定，这两处加解密都生效；而 Wrapper 的 where 条件里，MP 只把列名拼进 SQL 文本、把值塞
 * 进通用参数 map,渲染出的 `#{ew.paramNameValuePairs.MPGENVAL1}` 是不带任何属性的匿名
 * 占位符——MyBatis 本身从不解析 SQL 去推断占位符对应哪个列，字段信息在进入绑定阶段前就已丢失
 * ，于是它按值的 Java 类型取默认 handler,对 String 就是裸 `ps.setString()` 的
 * `StringTypeHandler`,明文原样进预备语句，这就是“where 条件不走 TypeHandler”的机制根
 * 源；补救办法是让调用方在传值前手动 `AesUtil.encrypt` 变成密文(本项目的做法，依赖 ECB
 * 确定性加密做密文等值比对)，或改写自定义 XML SQL 在占位符里显式声明 `#{x,typeHandler=...}`。
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class AesCryptoTypeHandler extends BaseTypeHandler<String> {

    /**
     * @param ps JDBC PreparedStatement(带 ? 占位符的预编译语句)——最终加密结果要 set 到它身上
     * @param i 参数序号，从 1 开始，指明这个值属于第几个 ?
     * @param parameter Java 侧的值——就是实体 idCardNo 字段的明文
     * @param jdbcType 该参数声明的 JDBC 类型(来自 #{x,jdbcType=...} 推断，这里是 VARCHAR)——主要用于 ps.setNull() 时告诉驱动写什么类型的 NULL,本实现用不到，只是签名要求。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, AesUtil.encrypt(parameter));
    }

    /**
     * 按列名读取
     * @param rs 结果集，游标已停在当前行，直接取当前行该列的值
     * @param columnName 取哪一列
     */
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return AesUtil.decrypt(rs.getString(columnName));
    }

    /**
     * 按列号读取
     * @param rs 结果集，游标已停在当前行，直接取当前行该列的值
     * @param columnIndex 按位置映射
     */
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return AesUtil.decrypt(rs.getString(columnIndex));
    }

    /**
     * 存储过程出参
     */
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return AesUtil.decrypt(cs.getString(columnIndex));
    }
}

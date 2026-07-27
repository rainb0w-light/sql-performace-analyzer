package com.biz.sccba.sqlanalyzer.mybatis;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Official MyBatis runtime boundary (development-guide §6): mapper XML is loaded exclusively via
 * {@code XMLMapperBuilder} (optionally on top of a parsed {@code mybatis-config.xml}), and final
 * SQL comes exclusively from {@code MappedStatement.getBoundSql(parameterObject)}. No custom
 * dynamic-SQL evaluation lives here or anywhere else in the product.
 *
 * <p>Anything that cannot be resolved through the official path (custom LanguageDriver, missing
 * project classes, malformed XML) is reported as {@code unsupported} — never silently degraded
 * into hand-built SQL.
 */
public final class MyBatisStatementRuntime {

    private final String mybatisConfigXml;
    private final String databaseId;

    public MyBatisStatementRuntime(String mybatisConfigXml, String databaseId) {
        this.mybatisConfigXml = mybatisConfigXml;
        this.databaseId = databaseId;
    }

    public record LoadedMapper(String namespace, List<String> statementIds) {}

    public record ParameterMappingView(String property, String mode, String javaType, String jdbcType) {}

    public record BoundResult(String sql, List<ParameterMappingView> parameterMappings,
                              Map<String, Object> additionalParameters, boolean dynamic,
                              String languageDriver, String unsupported) {
        public boolean isUnsupported() { return unsupported != null; }
    }

    /** Parses optional mybatis-config.xml (settings/aliases/typeHandlers/plugins) then the mapper. */
    public Configuration loadConfiguration(byte[] mapperXml, String resource) {
        Configuration configuration;
        try {
            if (mybatisConfigXml != null && !mybatisConfigXml.isBlank()) {
                configuration = new XMLConfigBuilder(
                        new ByteArrayInputStream(mybatisConfigXml.getBytes(StandardCharsets.UTF_8))).parse();
            } else {
                configuration = new Configuration();
            }
            if (databaseId != null && !databaseId.isBlank()) {
                configuration.setDatabaseId(databaseId);
            }
            XMLMapperBuilder builder = new XMLMapperBuilder(
                    new ByteArrayInputStream(mapperXml), configuration, resource, configuration.getSqlFragments());
            builder.parse();
            return configuration;
        } catch (Exception e) {
            throw new MapperLoadException("Mapper 加载失败：" + rootMessage(e), e);
        }
    }

    public LoadedMapper load(byte[] mapperXml, String resource) {
        Configuration configuration = loadConfiguration(mapperXml, resource);
        String namespace = MapperNamespaceReader.read(mapperXml);
        List<String> ids = new ArrayList<>();
        for (String name : configuration.getMappedStatementNames()) {
            if (namespace == null || name.startsWith(namespace + ".")) {
                ids.add(name);
            }
        }
        return new LoadedMapper(namespace, ids);
    }

    /**
     * Produces the BoundSql for one parameter scenario through the official evaluation path.
     * The statement id is the fully qualified {@code namespace.statementId}.
     */
    public BoundResult bind(Configuration configuration, String statementId, Object parameterObject) {
        MappedStatement statement;
        try {
            statement = configuration.getMappedStatement(statementId);
        } catch (Exception e) {
            return new BoundResult(null, List.of(), Map.of(), false, null,
                    "UNSUPPORTED: statement 不存在或无法解析：" + rootMessage(e));
        }

        String driver = statement.getLang() == null ? "null" : statement.getLang().getClass().getSimpleName();
        if (!(statement.getLang() instanceof XMLLanguageDriver)) {
            return new BoundResult(null, List.of(), Map.of(), false, driver,
                    "UNSUPPORTED: 自定义 LanguageDriver 不支持静态场景生成：" + driver);
        }

        try {
            BoundSql boundSql = statement.getBoundSql(parameterObject);
            List<ParameterMappingView> mappings = new ArrayList<>();
            Map<String, Object> additional = new LinkedHashMap<>();
            for (ParameterMapping mapping : boundSql.getParameterMappings()) {
                mappings.add(new ParameterMappingView(mapping.getProperty(),
                        String.valueOf(mapping.getMode()),
                        mapping.getJavaType() == null ? null : mapping.getJavaType().getName(),
                        mapping.getJdbcType() == null ? null : mapping.getJdbcType().name()));
                String property = mapping.getProperty();
                if (property != null && property.startsWith("__frch_") && boundSql.hasAdditionalParameter(property)) {
                    additional.put(property, boundSql.getAdditionalParameter(property));
                }
            }
            boolean dynamic = statement.getSqlSource() instanceof DynamicSqlSource;
            return new BoundResult(boundSql.getSql(), mappings, additional, dynamic, driver, null);
        } catch (Exception e) {
            return new BoundResult(null, List.of(), Map.of(), false, driver,
                    "UNSUPPORTED: getBoundSql 失败（可能引用了无法解析的项目类型）：" + rootMessage(e));
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }

    public static final class MapperLoadException extends RuntimeException {
        public MapperLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

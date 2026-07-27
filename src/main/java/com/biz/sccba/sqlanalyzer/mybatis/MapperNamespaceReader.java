package com.biz.sccba.sqlanalyzer.mybatis;

import org.apache.ibatis.parsing.XPathParser;

import java.nio.charset.StandardCharsets;

/** Structural read of the mapper namespace attribute (no SQL evaluation). */
final class MapperNamespaceReader {

    private MapperNamespaceReader() {}

    static String read(byte[] mapperXml) {
        try {
            String xml = new String(mapperXml, StandardCharsets.UTF_8).replaceAll("<!DOCTYPE[^>]*>", "");
            var node = new XPathParser(xml).evalNode("/mapper");
            return node == null ? null : node.getStringAttribute("namespace");
        } catch (Exception e) {
            return null;
        }
    }
}

package com.biz.sccba.sqlanalyzer.tool;

import com.biz.sccba.sqlanalyzer.service.MyBatisXmlParserService;
import com.biz.sccba.sqlanalyzer.service.MyBatisXmlParserService.ParseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MyBatis Mapper 解析工具
 */
@Component
@RequiredArgsConstructor
public class MyBatisParserTool {

    private final MyBatisXmlParserService parserService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从 XML 内容解析
     *
     * @param xmlContent XML 内容
     * @param namespace   命名空间 (可选)
     * @return 解析结果 JSON
     */
    @Tool(name = "parse_mybatis_xml", description = "解析 MyBatis Mapper XML 内容，提取 SQL 语句和动态标签信息")
    public String parseFromContent(
            @ToolParam(name = "xmlContent", description = "XML 文件内容", required = true) String xmlContent,
            @ToolParam(name = "namespace", description = "命名空间 (可选)", required = false) String namespace) {
        System.out.println("[MyBatisParserTool] 解析 MyBatis XML 内容，namespace: " + namespace);
        try {
            ParseResult result = parserService.parse(xmlContent, namespace);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 从文件路径解析
     *
     * @param filePath 文件路径
     * @return 解析结果 JSON
     */
    @Tool(name = "parse_mybatis_file", description = "解析 MyBatis Mapper XML 文件，提取 SQL 语句和动态标签信息")
    public String parseFromFile(
            @ToolParam(name = "filePath", description = "XML 文件路径", required = true) String filePath) {
        System.out.println("[MyBatisParserTool] 解析 MyBatis XML 文件：" + filePath);
        try {
            String xmlContent = Files.readString(Path.of(filePath));
            ParseResult result = parserService.parse(xmlContent, null);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            System.out.println("[MyBatisParserTool] 读取文件失败：" + filePath + ", 错误：" + e.getMessage());
            return "{\"error\": \"读取文件失败：" + e.getMessage() + "\"}";
        }
    }
}

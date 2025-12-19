package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.service.MyBatisMapperParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * MyBatis相关API控制器
 */
@RestController
@RequestMapping("/api/mybatis")
public class MyBatisController {

    private static final Logger logger = LoggerFactory.getLogger(MyBatisController.class);

    @Autowired
    private MyBatisMapperParserService myBatisMapperParserService;

    /**
     * 上传并解析MyBatis Mapper XML文件
     * POST /api/mybatis/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadMapperXml(@RequestBody Map<String, String> request) {
        try {
            String xmlContent = request.get("xmlContent");
            String mapperNamespace = request.get("mapperNamespace");

            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("xmlContent不能为空"));
            }

            if (mapperNamespace == null || mapperNamespace.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("mapperNamespace不能为空"));
            }

            logger.info("收到MyBatis Mapper XML上传请求: namespace={}", mapperNamespace);

            MyBatisMapperParserService.ParseResult result = 
                    myBatisMapperParserService.parseMapperXml(xmlContent, mapperNamespace);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mapperNamespace", result.getMapperNamespace());
            response.put("queryCount", result.getQueryCount());
            response.put("message", "成功解析 " + result.getQueryCount() + " 个SQL查询");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("解析MyBatis Mapper XML失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("解析失败: " + e.getMessage()));
        }
    }

    /**
     * 根据表名查询相关SQL
     * GET /api/mybatis/queries/table/{tableName}
     */
    @GetMapping("/queries/table/{tableName}")
    public ResponseEntity<?> getQueriesByTable(@PathVariable String tableName) {
        try {
            logger.info("查询表相关的SQL: tableName={}", tableName);

            var queries = myBatisMapperParserService.getQueriesByTable(tableName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("tableName", tableName);
            response.put("count", queries.size());
            response.put("queries", queries);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("查询表SQL失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}


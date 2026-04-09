package com.biz.sccba.sqlanalyzer.rag;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识库服务
 * 加载和管理技术知识文档
 */
@Service
public class KnowledgeBaseService {

    // 知识库存储
    private final Map<String, List<KnowledgeDocument>> knowledgeByCategory = new ConcurrentHashMap<>();
    private final Map<String, KnowledgeDocument> knowledgeById = new ConcurrentHashMap<>();

    // 关键词索引
    private final Map<String, Set<String>> keywordIndex = new ConcurrentHashMap<>();

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @PostConstruct
    public void init() {
        System.out.println("初始化知识库...");
        loadKnowledge();
        System.out.println("知识库加载完成，共 " + knowledgeById.size() + " 篇文档");
    }

    /**
     * 加载知识库
     */
    private void loadKnowledge() {
        try {
            // 加载 InnoDB 知识
            loadCategory("innodb", "classpath:knowledge/innodb/*.md");

            // 加载分布式数据库知识
            loadCategory("distributed", "classpath:knowledge/distributed/*.md");

        } catch (Exception e) {
            System.err.println("加载知识库失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 加载指定类别的知识
     */
    private void loadCategory(String category, String locationPattern) throws IOException {
        Resource[] resources = resolver.getResources(locationPattern);
        List<KnowledgeDocument> documents = new ArrayList<>();

        for (Resource resource : resources) {
            try {
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String filename = resource.getFilename();

                KnowledgeDocument doc = new KnowledgeDocument();
                doc.setId(UUID.randomUUID().toString());
                doc.setTitle(extractTitle(content, filename));
                doc.setContent(content);
                doc.setCategory(category);
                doc.setTags(extractTags(content));
                doc.setSource(filename);
                doc.setMetadata(Map.of("path", resource.getURL().toString()));

                documents.add(doc);
                knowledgeById.put(doc.getId(), doc);

                // 建立关键词索引
                indexKeywords(doc);

                System.out.println("加载知识文档：" + category + " - " + doc.getTitle());
            } catch (Exception e) {
                System.err.println("加载文档失败：" + resource.getFilename() + " - " + e.getMessage());
            }
        }

        knowledgeByCategory.put(category, documents);
    }

    /**
     * 提取文档标题
     */
    private String extractTitle(String content, String filename) {
        // 尝试从第一个#标题提取
        Pattern pattern = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // 使用文件名
        return filename != null ? filename.replace(".md", "") : "Untitled";
    }

    /**
     * 提取文档标签
     */
    private List<String> extractTags(String content) {
        List<String> tags = new ArrayList<>();

        // 简单的关键词提取
        String[] keywords = {"索引", "优化", "锁", "事务", "分片", "分布式", "性能", "查询", "JOIN", "索引优化"};
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                tags.add(keyword);
            }
        }

        return tags;
    }

    /**
     * 建立关键词索引
     */
    private void indexKeywords(KnowledgeDocument doc) {
        String content = doc.getContent().toLowerCase();

        // 分词并建立索引
        String[] words = content.split("[\\s,.!?:;()\\[\\]\\u4e00-\\u9fff]+");
        for (String word : words) {
            if (word.length() >= 2 && word.length() <= 10) {
                keywordIndex.computeIfAbsent(word, k -> ConcurrentHashMap.newKeySet()).add(doc.getId());
            }
        }
    }

    /**
     * 搜索知识
     *
     * @param query    查询内容
     * @param category 类别 (可选)
     * @param limit    结果数量限制
     * @return 匹配的文档列表
     */
    public List<KnowledgeDocument> search(String query, String category, int limit) {
        System.out.println("搜索知识：" + query + " (类别：" + category + ")");

        Map<String, Integer> scores = new HashMap<>();
        String queryLower = query.toLowerCase();

        // 简单的关键词匹配
        String[] queryWords = queryLower.split("\\s+");
        for (String word : queryWords) {
            Set<String> docIds = keywordIndex.get(word);
            if (docIds != null) {
                for (String docId : docIds) {
                    scores.merge(docId, 1, Integer::sum);
                }
            }
        }

        // 排序并返回结果
        List<KnowledgeDocument> results = new ArrayList<>();
        scores.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .forEach(entry -> {
                KnowledgeDocument doc = knowledgeById.get(entry.getKey());
                if (doc != null) {
                    if (category == null || category.isEmpty() || category.equals(doc.getCategory())) {
                        results.add(doc);
                    }
                }
            });

        return results;
    }

    /**
     * 获取指定类别的所有知识
     */
    public List<KnowledgeDocument> getByCategory(String category) {
        return knowledgeByCategory.getOrDefault(category, Collections.emptyList());
    }

    /**
     * 获取所有类别
     */
    public List<String> getCategories() {
        return new ArrayList<>(knowledgeByCategory.keySet());
    }

    /**
     * 根据 ID 获取知识
     */
    public KnowledgeDocument getById(String id) {
        return knowledgeById.get(id);
    }

    /**
     * 添加知识文档
     */
    public void addDocument(KnowledgeDocument document) {
        if (document.getId() == null) {
            document.setId(UUID.randomUUID().toString());
        }

        knowledgeById.put(document.getId(), document);
        knowledgeByCategory
            .computeIfAbsent(document.getCategory(), k -> new ArrayList<>())
            .add(document);

        indexKeywords(document);

        System.out.println("添加知识文档：" + document.getCategory() + " - " + document.getTitle());
    }

    /**
     * 删除知识文档
     */
    public void removeDocument(String id) {
        KnowledgeDocument doc = knowledgeById.remove(id);
        if (doc != null) {
            List<KnowledgeDocument> categoryDocs = knowledgeByCategory.get(doc.getCategory());
            if (categoryDocs != null) {
                categoryDocs.removeIf(d -> d.getId().equals(id));
            }
            System.out.println("删除知识文档：" + id);
        }
    }

    /**
     * 获取知识库统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocuments", knowledgeById.size());
        stats.put("categories", knowledgeByCategory.size());

        Map<String, Integer> categoryCounts = new HashMap<>();
        knowledgeByCategory.forEach((cat, docs) -> categoryCounts.put(cat, docs.size()));
        stats.put("documentsByCategory", categoryCounts);

        stats.put("keywordIndexSize", keywordIndex.size());

        return stats;
    }
}

package com.biz.sccba.sqlanalyzer.knowledge;

import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.reader.PDFReader;
import io.agentscope.core.rag.reader.Reader;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.reader.TikaReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Thin, bounded adapter over AgentScope Java 2.0.0 readers.
 *
 * <p>The readers do not expose reliable PDF pages or spreadsheet cell coordinates, therefore this
 * adapter deliberately guarantees only the stable {@code chunk:N} locator. Controlled Excel
 * templates continue to use {@link ExcelKnowledgeParser} through the compatibility endpoint.
 */
public final class KnowledgeDocumentReader {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("md", "txt", "pdf", "xlsx");

    private final long maxFileBytes;
    private final int maxChunks;
    private final long parseTimeoutMs;
    private final long maxExpandedBytes;

    public KnowledgeDocumentReader(long maxFileBytes, int maxChunks, long parseTimeoutMs,
                                   long maxExpandedBytes) {
        if (maxFileBytes < 1 || maxChunks < 1 || parseTimeoutMs < 1 || maxExpandedBytes < 1) {
            throw new IllegalArgumentException("Reader 限制必须为正数");
        }
        this.maxFileBytes = maxFileBytes;
        this.maxChunks = maxChunks;
        this.parseTimeoutMs = parseTimeoutMs;
        this.maxExpandedBytes = maxExpandedBytes;
    }

    public ReadResult read(String fileName, String declaredMediaType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("知识文件不能为空");
        if (bytes.length > maxFileBytes) throw new IllegalArgumentException("知识文件超过大小上限");
        String extension = extension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 .md/.txt/.pdf/.xlsx");
        }
        validateSignatureAndMime(extension, declaredMediaType, bytes);

        Path temporary = null;
        try {
            Reader reader;
            ReaderInput input;
            if ("md".equals(extension) || "txt".equals(extension)) {
                reader = new TextReader();
                input = ReaderInput.fromString(decodeUtf8(bytes));
            } else {
                temporary = Files.createTempFile("spa-knowledge-", "." + extension);
                Files.write(temporary, bytes);
                reader = "pdf".equals(extension) ? new PDFReader() : new TikaReader();
                input = ReaderInput.fromPath(temporary);
            }

            List<Document> documents = reader.read(input).block(Duration.ofMillis(parseTimeoutMs));
            if (documents == null || documents.isEmpty()) {
                throw new IllegalArgumentException("文件未解析出可用文本");
            }
            if (documents.size() > maxChunks) throw new IllegalArgumentException("解析结果超过 Chunk 上限");
            List<ParsedChunk> chunks = java.util.stream.IntStream.range(0, documents.size())
                    .mapToObj(index -> new ParsedChunk(
                            "chunk:" + index,
                            documents.get(index).getMetadata().getContentText()))
                    .filter(chunk -> chunk.text() != null && !chunk.text().isBlank())
                    .toList();
            if (chunks.isEmpty()) throw new IllegalArgumentException("文件未解析出可用文本");
            return new ReadResult(extension, canonicalMediaType(extension), chunks);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("知识文件解析失败", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The generated random path contains no user data and is cleaned on JVM exit.
                    temporary.toFile().deleteOnExit();
                }
            }
        }
    }

    private void validateSignatureAndMime(String extension, String declaredMediaType, byte[] bytes) {
        String mediaType = declaredMediaType == null ? "" :
                declaredMediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        boolean generic = mediaType.isBlank() || "application/octet-stream".equals(mediaType);
        switch (extension) {
            case "pdf" -> {
                if (!startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                    throw new IllegalArgumentException("PDF 扩展名与文件内容不匹配");
                }
                if (!generic && !"application/pdf".equals(mediaType)) {
                    throw new IllegalArgumentException("PDF MIME 类型不匹配");
                }
            }
            case "xlsx" -> {
                if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
                    throw new IllegalArgumentException("Excel 扩展名与文件内容不匹配");
                }
                validateZipExpansion(bytes);
                if (!generic && !Set.of(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/zip").contains(mediaType)) {
                    throw new IllegalArgumentException("Excel MIME 类型不匹配");
                }
            }
            case "md" -> {
                decodeUtf8(bytes);
                if (!generic && !Set.of("text/markdown", "text/plain", "text/x-markdown").contains(mediaType)) {
                    throw new IllegalArgumentException("Markdown MIME 类型不匹配");
                }
            }
            case "txt" -> {
                decodeUtf8(bytes);
                if (!generic && !"text/plain".equals(mediaType)) {
                    throw new IllegalArgumentException("Text MIME 类型不匹配");
                }
            }
            default -> throw new IllegalArgumentException("不支持的文件类型");
        }
    }

    private void validateZipExpansion(byte[] bytes) {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > maxExpandedBytes) {
                        throw new IllegalArgumentException("Excel 解压展开大小超过上限");
                    }
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Excel 压缩包无效", exception);
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("文本文件必须使用 UTF-8", exception);
        }
    }

    private static String extension(String fileName) {
        if (fileName == null) return "";
        String safeName = Path.of(fileName).getFileName().toString();
        int dot = safeName.lastIndexOf('.');
        return dot < 0 ? "" : safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String canonicalMediaType(String extension) {
        return switch (extension) {
            case "md" -> "text/markdown";
            case "txt" -> "text/plain";
            case "pdf" -> "application/pdf";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }

    public record ParsedChunk(String locator, String text) {}

    public record ReadResult(String extension, String mediaType, List<ParsedChunk> chunks) {}
}

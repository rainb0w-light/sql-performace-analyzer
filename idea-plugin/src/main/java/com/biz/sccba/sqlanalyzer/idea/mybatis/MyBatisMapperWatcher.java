package com.biz.sccba.sqlanalyzer.idea.mybatis;

import com.biz.sccba.sqlanalyzer.idea.client.BackendClient;
import com.biz.sccba.sqlanalyzer.idea.settings.ProjectAnalyzerSettings;
import com.biz.sccba.sqlanalyzer.idea.settings.TokenStore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Watches MyBatis mapper edits and debounced-sends the changed XML to the server artifact
 * pipeline. Mapper identification uses XML PSI/DOM (development-guide §8.1), never regex.
 */
public final class MyBatisMapperWatcher implements Disposable {
    private final Project project;
    private final ProjectAnalyzerSettings settings;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "sql-analyzer-mybatis-watcher");
        thread.setDaemon(true);
        return thread;
    });

    public MyBatisMapperWatcher(Project project) {
        this.project = project;
        this.settings = ProjectAnalyzerSettings.getInstance(project);
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override public void after(@NotNull List<? extends VFileEvent> events) {
                events.stream().filter(e -> e instanceof VFileContentChangeEvent)
                        .map(VFileEvent::getFile).filter(MyBatisMapperWatcher::isMapper)
                        .forEach(file -> executor.schedule(() -> upload(file), 800, TimeUnit.MILLISECONDS));
            }
        });
    }

    public void uploadNow(VirtualFile file) {
        if (isMapper(file)) executor.execute(() -> upload(file));
    }

    private void upload(VirtualFile file) {
        String token = TokenStore.getInstance().token();
        if (file == null || !file.isValid() || token.isBlank()) return;
        try {
            PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(file));
            if (!(psiFile instanceof XmlFile xmlFile)) return;
            String namespace = ReadAction.compute(() -> {
                if (!MyBatisStatementPsi.isMapperFile(xmlFile)) return null;
                return MyBatisStatementPsi.namespaceOf(xmlFile.getRootTag());
            });
            if (namespace == null || namespace.isBlank()) return;
            String xml = VfsUtilCore.loadText(file);
            String sessionId = settings.sessionId();
            BackendClient client = new BackendClient(settings.endpoint(), token);
            if (sessionId.isBlank()) {
                sessionId = client.createSession("IDEA MyBatis Mapper");
                settings.sessionId(sessionId);
            }
            client.indexMyBatisMapper(sessionId, xml, namespace);
        } catch (Exception ignored) {
            // File listeners must never interrupt IntelliJ's VFS thread; the next edit retries.
        }
    }

    private static boolean isMapper(VirtualFile file) {
        return file != null && file.isValid() && "xml".equalsIgnoreCase(file.getExtension());
    }

    @Override public void dispose() { executor.shutdownNow(); }
}

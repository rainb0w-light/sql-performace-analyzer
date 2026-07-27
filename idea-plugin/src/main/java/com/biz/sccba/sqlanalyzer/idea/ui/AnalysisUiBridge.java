package com.biz.sccba.sqlanalyzer.idea.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Project-level bridge between background analysis flows (actions, streams) and the Tool Window
 * UI. All mutations are marshalled to the EDT; when the panel is not instantiated yet the tool
 * window is shown first (which creates it).
 */
@Service(Service.Level.PROJECT)
public final class AnalysisUiBridge {

    public interface Listener {
        void onStreamText(String text);
        void onStatus(String status);
        void onScenarioMatrix(String planJson);
        void onReport(String reportJson);
        void onRecommendations(String recommendationsJson);
        void onRunStarted(String runId);
        void onRunFinished();
    }

    private final Project project;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public AnalysisUiBridge(Project project) {
        this.project = project;
    }

    public static AnalysisUiBridge getInstance(Project project) {
        return project.getService(AnalysisUiBridge.class);
    }

    public void register(Listener listener) {
        listeners.add(listener);
    }

    public void unregister(Listener listener) {
        listeners.remove(listener);
    }

    /** Shows the tool window (creating the panel if needed), then dispatches on the EDT. */
    public void showAndDispatch(Runnable action) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("SQL Analyzer");
            if (toolWindow != null) toolWindow.show();
            action.run();
        });
    }

    public void streamText(String text) {
        ApplicationManager.getApplication().invokeLater(() -> listeners.forEach(l -> l.onStreamText(text)));
    }

    public void status(String status) {
        ApplicationManager.getApplication().invokeLater(() -> listeners.forEach(l -> l.onStatus(status)));
    }

    public void scenarioMatrix(String planJson) {
        ApplicationManager.getApplication().invokeLater(() -> listeners.forEach(l -> l.onScenarioMatrix(planJson)));
    }

    public void recommendations(String json) {
        ApplicationManager.getApplication().invokeLater(() -> listeners.forEach(l -> l.onRecommendations(json)));
    }

    public void report(String json) {
        ApplicationManager.getApplication().invokeLater(() -> listeners.forEach(l -> l.onReport(json)));
    }

    public void runStarted(String runId) {
        ApplicationManager.getApplication().invokeLater(() -> listeners.forEach(l -> l.onRunStarted(runId)));
    }

    public void runFinished() {
        ApplicationManager.getApplication().invokeLater(() -> listeners.forEach(l -> l.onRunFinished()));
    }
}

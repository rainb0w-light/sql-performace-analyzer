on run
    tell application "System Events"
        -- runIde 在 macOS 上的进程名通常是 java，正式安装版可能是 IntelliJ IDEA。
        set ideProcess to missing value
        -- The JVM is visible before Swing creates its first window. Retry here
        -- instead of treating that normal startup interval as a UI failure.
        repeat with attempt from 1 to 60
            set candidates to every process whose name contains "IntelliJ" or name contains "java"
            repeat with candidate in candidates
                try
                    if (count of windows of candidate) > 0 then
                        set ideProcess to contents of candidate
                        exit repeat
                    end if
                end try
            end repeat
            if ideProcess is not missing value then exit repeat
            delay 1
        end repeat
        if ideProcess is missing value then
            set processCount to count of (every process whose name contains "IntelliJ" or name contains "java")
            if processCount is 0 then
                error "IntelliJ IDEA process was not found after 60 seconds"
            else
                error "IntelliJ IDEA window was not found after 60 seconds"
                end if
        end if
        tell ideProcess
            set frontmost to true
            repeat with i from 1 to 20
                if (exists menu bar 1) and ((exists menu "View" of menu bar 1) or (exists menu "视图" of menu bar 1)) then exit repeat
                delay 1
            end repeat
            if not (exists menu bar 1) then error "IntelliJ menu bar was not available"
            try
                click menu item "SQL Analyzer" of menu 1 of menu item "Tool Windows" of menu "View" of menu bar 1
            on error
                click menu item "SQL Analyzer" of menu 1 of menu item "工具窗口" of menu "视图" of menu bar 1
            end try
            delay 2
            -- Accessibility 在不同 macOS/JetBrains 版本中不一定暴露 Swing 子控件文本。
            -- 这里验证 Tool Window 菜单项可见、点击后主窗口仍存在且有可访问 UI 树。
            if not (exists window 1) then error "IntelliJ main window disappeared"
            if (count of UI elements of window 1) is 0 then error "SQL Analyzer Tool Window did not expose an accessibility tree"
        end tell
    end tell
end run

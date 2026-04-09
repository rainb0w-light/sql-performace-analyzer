import React from 'react';

interface SplitViewProps {
  leftTitle?: string;
  rightTitle?: string;
  leftContent: React.ReactNode;
  rightContent: React.ReactNode;
  leftWidth?: number;
  divider?: 'single' | 'double' | 'bold' | 'none';
}

/**
 * 分屏查看组件 - 用于并排显示 SQL 语句和执行计划/分析报告
 */
export function SplitView({
  leftTitle,
  rightTitle,
  leftContent,
  rightContent,
  leftWidth = 40,
  divider = 'single',
}: SplitViewProps) {
  return (
    <box
      style={{
        flexDirection: 'row',
        border: true,
        borderStyle: 'single', 
        borderColor: '#3a3a5a',
        backgroundColor: '#1a1a2e',
      }}
    >
      {/* Left Panel */}
      <box
        style={{
          width: `${leftWidth}%`,
          flexDirection: 'column',
        }}
      >
        {leftTitle && (
          <box
            style={{
              flexDirection: 'row',
              paddingTop: 0,
              paddingBottom: 0,
              paddingLeft: 2,
              paddingRight: 2,
              border: true,
              borderStyle: 'single',
              borderColor: '#3a3a5a',
              backgroundColor: '#2a2a4a',
            }}
          >
            <text style={{ fg: 'cyan' }}>{leftTitle}</text>
          </box>
        )}
        <box style={{ flexGrow: 1, paddingTop: 1, paddingBottom: 1, paddingLeft: 1, paddingRight: 1 }}>{leftContent}</box>
      </box>

      {/* Right Panel */}
      <box
        style={{
          flexGrow: 1,
          flexDirection: 'column',
        }}
      >
        {rightTitle && (
          <box
            style={{
              flexDirection: 'row',
              paddingTop: 0,
              paddingBottom: 0,
              paddingLeft: 2,
              paddingRight: 2,
              border: true,
              borderStyle: 'single',
              borderColor: '#3a3a5a',
              backgroundColor: '#2a2a4a',
            }}
          >
            <text style={{ fg: 'cyan' }}>{rightTitle}</text>
          </box>
        )}
        <box style={{ flexGrow: 1, paddingTop: 1, paddingBottom: 1, paddingLeft: 1, paddingRight: 1 }}>{rightContent}</box>
      </box>
    </box>
  );
}

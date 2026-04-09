import { useState } from 'react';
import { useKeyboard } from '@opentui/react';
import { type KeyEvent } from '@opentui/core';

interface CommandInputProps {
  onSubmit?: (command: string) => void;
  disabled?: boolean;
}

const AVAILABLE_COMMANDS = [
  { command: '/analyze', description: '分析 Mapper XML 文件' },
  { command: '/sql', description: '分析 SQL 语句' },
  { command: '/table', description: '分析表结构和索引' },
  { command: '/test', description: '执行测试' },
  { command: '/commit', description: '提交 DDL 变更' },
  { command: '/session', description: '会话管理' },
  { command: '/skill', description: 'Skill 模板管理' },
  { command: '/config', description: '配置管理' },
  { command: '/env', description: '环境管理' },
  { command: '/model', description: '模型管理' },
  { command: '/help', description: '显示帮助' },
];

export function CommandInput({ onSubmit, disabled = false }: CommandInputProps) {
  const [value, setValue] = useState('');
  const [showHints, setShowHints] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(0);

  // Get matching command hints based on current input
  const getCommandHints = () => {
    if (!value.startsWith('/')) {
      return [];
    }

    const input = value.slice(1).toLowerCase();
    return AVAILABLE_COMMANDS.filter(cmd =>
      cmd.command.slice(1).toLowerCase().startsWith(input)
    ).slice(0, 5); // Limit to 5 hints
  };

  const hints = getCommandHints();

  // Auto-complete the selected command
  const autoComplete = () => {
    if (hints.length > 0 && selectedIndex < hints.length) {
      const selectedCommand = hints[selectedIndex].command;
      setValue(selectedCommand + ' ');
      setShowHints(false);
      setSelectedIndex(0);
    }
  };

  const handleSubmit = () => {
    if (onSubmit && value.trim()) {
      onSubmit(value.trim());
      setValue('');
      setShowHints(false);
      setSelectedIndex(0);
    }
  };

  useKeyboard((key: KeyEvent) => {
    if (key.name === 'return' || key.name === 'enter') {
      if (showHints && hints.length > 0) {
        // 有提示时，回车键自动补全当前选中的命令
        autoComplete();
      } else {
        handleSubmit();
      }
    } else if (key.name === 'escape') {
      setValue('');
      setShowHints(false);
      setSelectedIndex(0);
    } else if (key.name === 'backspace' || key.name === 'delete') {
      const newValue = value.slice(0, -1);
      setValue(newValue);
      setShowHints(newValue.startsWith('/'));
      setSelectedIndex(0);
    } else if (key.name === 'tab') {
      // Tab 键自动补全
      if (showHints && hints.length > 0) {
        autoComplete();
      }
    } else if (key.name === 'up') {
      // 上箭头选择上一个提示
      if (showHints && hints.length > 0) {
        setSelectedIndex(prev => (prev > 0 ? prev - 1 : hints.length - 1));
      }
    } else if (key.name === 'down') {
      // 下箭头选择下一个提示
      if (showHints && hints.length > 0) {
        setSelectedIndex(prev => (prev < hints.length - 1 ? prev + 1 : 0));
      }
    } else if (key.name === 'right') {
      // 右箭头自动补全第一个匹配的提示
      if (showHints && hints.length > 0) {
        autoComplete();
      }
    } else if (key.sequence && !key.name) {
      const newValue = value + key.sequence;
      setValue(newValue);
      setShowHints(newValue.startsWith('/'));
      setSelectedIndex(0);
    }
  });

  return (
    <box style={{ flexDirection: 'column' }}>
      <input
        value={value}
        onChange={(e) => {
          const newValue = (e as any)?.target?.value ?? '';
          setValue(newValue);
          setShowHints(newValue.startsWith('/'));
          setSelectedIndex(0);
        }}
        onSubmit={handleSubmit}
        placeholder=""
        focused={!disabled}
        width="auto"
        maxWidth={60}
        backgroundColor="transparent"
        textColor="white"
        placeholderColor="#666"
        cursorColor="#00ff00"
        focusedBackgroundColor="#2a2a3e"
      />

      {/* Command Hints */}
      {showHints && hints.length > 0 && (
        <box style={{
          flexDirection: 'column',
          paddingTop: 1,
          paddingLeft: 2,
        }}>
          {hints.map((hint, index) => (
            <text
              key={hint.command}
              style={{
                fg: index === selectedIndex ? '#60A5FA' : '#9CA3AF',
                bold: index === selectedIndex,
              }}
            >
              {index === selectedIndex ? '❯ ' : '  '}
              {hint.command} - {hint.description}
            </text>
          ))}
        </box>
      )}

      {/* Help text when empty */}
      {!value && !showHints && (
        <box style={{ paddingTop: 1, flexDirection: 'row', gap: 4 }}>
          <text style={{ fg: '#6B7280' }}>快捷键:</text>
          <text style={{ fg: '#9CA3AF' }}>Enter 发送</text>
          <text style={{ fg: '#4B5563' }}>|</text>
          <text style={{ fg: '#9CA3AF' }}>Esc 清空</text>
        </box>
      )}

      {/* Keyboard hints when showing command hints */}
      {showHints && hints.length > 0 && (
        <box style={{ paddingTop: 1, flexDirection: 'row', gap: 4 }}>
          <text style={{ fg: '#6B7280' }}>操作:</text>
          <text style={{ fg: '#60A5FA' }}>↑↓ 选择</text>
          <text style={{ fg: '#4B5563' }}>|</text>
          <text style={{ fg: '#9CA3AF' }}>Tab/→/Enter 补全</text>
        </box>
      )}
    </box>
  );
}

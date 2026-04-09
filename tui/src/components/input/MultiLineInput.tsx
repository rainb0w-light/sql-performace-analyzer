import { useState, useEffect } from 'react';
import { useKeyboard } from '@opentui/react';

interface MultiLineInputProps {
  onSubmit: (value: string) => void;
  disabled?: boolean;
  placeholder?: string;
}

export function MultiLineInput({ onSubmit, disabled = false, placeholder = "输入多行命令..." }: MultiLineInputProps) {
  return (
    <textarea
      placeholder={placeholder}
      focused={!disabled}
      width="100%"
      minHeight={3}
      maxHeight={8}
      backgroundColor="#1a1a2e"
      textColor="#e0e0e0"
      cursorColor="#00ff00"
      focusedBackgroundColor="#2a2a3e"
    />
  );
}
import React from 'react';
import { ChatBubble } from './ChatBubble';

export interface ChatMessage {
  id: string;
  type: 'command' | 'response' | 'error' | 'info';
  content: string;
  timestamp: string;
  title?: string;
}

interface ChatHistoryProps {
  messages: ChatMessage[];
}

export function ChatHistory({ messages }: ChatHistoryProps) {
  if (messages.length === 0) {
    return null;
  }

  return (
    <box style={{ flexDirection: 'column' }}>
      {messages.map((msg) => (
        <ChatBubble
          key={msg.id}
          type={msg.type}
          content={msg.content}
          timestamp={msg.timestamp}
          title={msg.title}
        />
      ))}
    </box>
  );
}

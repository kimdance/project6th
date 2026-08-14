import React from 'react';

interface TopMessageProps {
  messages: string | string[];
  isError?: boolean;
}

export const TopMessage: React.FC<TopMessageProps> = ({ messages, isError = true }) => {
  const messageList = Array.isArray(messages) ? messages : [messages];
  const activeMessages = messageList.filter(Boolean);

  if (activeMessages.length === 0) return null;

  return (
    <div
      style={{
        color: isError ? '#dc3545' : '#198754',
        fontWeight: 'bold',
        marginBottom: '15px',
        wordBreak: 'break-word',
      }}
    >
      {activeMessages.length === 1 ? (
        // 1件の場合はテキスト表示
        <p style={{ margin: 0 }}>{activeMessages[0]}</p>
      ) : (
        // 複数件の場合はリスト形式で表示
        <ul style={{ margin: 0, paddingLeft: '20px' }}>
          {activeMessages.map((msg, idx) => (
            <li key={idx} style={{ marginBottom: '4px' }}>
              {msg}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};
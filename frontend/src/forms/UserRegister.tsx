import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { API_BASE_URL } from '../config';
import { TopMessage } from '../components/TopMessage';

interface ErrorItem {
  message: string;
  fields: string[];
}

interface ApiErrorResponse {
  errors?: ErrorItem[];
}

export const UserRegister: React.FC = () => {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    companyCode: '',
    name: '',
    email: '',
    password: '',
    telnumber: '',
  });

  const [messages, setMessages] = useState<string[]>([]);
  const [errorFields, setErrorFields] = useState<string[]>([]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));

    // ユーザーが入力・修正した項目のエラー判定（赤色表示）を解除
    if (errorFields.includes(name)) {
      setErrorFields((prev) => prev.filter((field) => field !== name));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessages([]);
    setErrorFields([]);

    try {
      const response = await fetch(`${API_BASE_URL}/api/users/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });

      if (response.ok) {
        navigate('/', {
          state: {
            email: formData.email,
            password: formData.password,
          },
        });
      } else {
        const errorResult: ApiErrorResponse = await response.json().catch(() => ({}));

        if (errorResult.errors && errorResult.errors.length > 0) {
          // 1. 全てのエラーメッセージをリスト用配列として取得
          const extractedMessages = errorResult.errors.map((err) => err.message);

          // 2. 全エラー項目のフィールド名を抽出し、重複を除外して設定（全該当項目を赤くするため）
          const allAffectedFields = Array.from(
            new Set(errorResult.errors.flatMap((err) => err.fields))
          );

          setMessages(extractedMessages);
          setErrorFields(allAffectedFields);
        } else {
          setMessages(['登録に失敗しました。']);
        }
      }
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['サーバーとの通信に失敗しました。']);
    }
  };

  const getInputStyle = (fieldName: string) => ({
    width: '100%',
    padding: '8px',
    marginTop: '5px',
    boxSizing: 'border-box' as const,
    backgroundColor: errorFields.includes(fieldName) ? '#f8d7da' : '#fff',
    borderColor: errorFields.includes(fieldName) ? '#dc3545' : '#ccc',
    borderStyle: 'solid',
    borderWidth: '1px',
  });

  return (
    <div style={{ maxWidth: '400px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>ユーザー登録</h2>

      {/* リスト形式で全てのエラーメッセージを出力 */}
      <TopMessage messages={messages} isError={true} />

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '15px' }}>
          <label>会社コード:</label>
          <input
            type="text"
            name="companyCode"
            value={formData.companyCode}
            onChange={handleChange}
            required
            style={getInputStyle('companyCode')}
          />
        </div>

        <div style={{ marginBottom: '15px' }}>
          <label>お名前:</label>
          <input
            type="text"
            name="name"
            value={formData.name}
            onChange={handleChange}
            required
            style={getInputStyle('name')}
          />
        </div>

        <div style={{ marginBottom: '15px' }}>
          <label>メールアドレス:</label>
          <input
            type="email"
            name="email"
            value={formData.email}
            onChange={handleChange}
            required
            style={getInputStyle('email')}
          />
        </div>

        <div style={{ marginBottom: '15px' }}>
          <label>パスワード:</label>
          <input
            type="password"
            name="password"
            value={formData.password}
            onChange={handleChange}
            required
            style={getInputStyle('password')}
          />
        </div>

        <div style={{ marginBottom: '15px' }}>
          <label>電話番号:</label>
          <input
            type="tel"
            name="telnumber"
            value={formData.telnumber}
            onChange={handleChange}
            required
            style={getInputStyle('telnumber')}
          />
        </div>

        <button
          type="submit"
          style={{
            width: '100%',
            padding: '10px',
            backgroundColor: '#007bff',
            color: '#fff',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
          }}
        >
          登録する
        </button>

        <button
          type="button"
          onClick={() => navigate('/')}
          style={{
            width: '100%',
            padding: '10px',
            marginTop: '10px',
            backgroundColor: '#6c757d',
            color: '#fff',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
          }}
        >
          ログイン画面へ戻る
        </button>
      </form>
    </div>
  );
};
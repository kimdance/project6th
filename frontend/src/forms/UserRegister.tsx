import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getTenantApiBaseUrl } from '../config';
import { TopMessage } from '../components/TopMessage';

interface ErrorItem {
  message: string;
  fields: string[];
}

interface ApiErrorResponse {
  errors?: ErrorItem[];
}

/** 登録画面で選べるロール。店長・経営管理者等への昇格は、ログイン後のユーザー編集画面で行う。 */
const REGISTERABLE_ROLES = [
  { value: 'HALL', label: 'スタッフ' },
  { value: 'PARTTIME', label: 'アルバイト' },
] as const;

/**
 * 現場スタッフの自己登録画面。
 * 会社（テナント）はURLサブドメインで確定済みのため入力させない（テナント作成・経営管理者登録は
 * 運営者がPostmanで行う前提。02_requirements.md §3.1／FR-A02c）。
 */
export const UserRegister: React.FC = () => {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    telnumber: '',
    role: REGISTERABLE_ROLES[0].value as string,
  });

  const [messages, setMessages] = useState<string[]>([]);
  const [errorFields, setErrorFields] = useState<string[]>([]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
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
      const response = await fetch(`${getTenantApiBaseUrl()}/api/v1/auth/register`, {
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
          <label>電話番号（任意）:</label>
          <input
            type="tel"
            name="telnumber"
            value={formData.telnumber}
            onChange={handleChange}
            style={getInputStyle('telnumber')}
          />
        </div>

        <div style={{ marginBottom: '15px' }}>
          <label>役割:</label>
          <select
            name="role"
            value={formData.role}
            onChange={handleChange}
            style={getInputStyle('role')}
          >
            {REGISTERABLE_ROLES.map((role) => (
              <option key={role.value} value={role.value}>
                {role.label}
              </option>
            ))}
          </select>
          <p style={{ fontSize: '12px', color: '#666', marginTop: '4px' }}>
            店長・経営管理者などへの変更は、ログイン後の管理画面から行えます。
          </p>
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

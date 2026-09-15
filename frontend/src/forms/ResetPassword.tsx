import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { TopMessage } from '../components/TopMessage';
import { confirmPasswordReset } from '../api/passwordReset';

/**
 * パスワード再設定画面（FR-A04）。メールのリンク（?token=...）から開く。
 * 会社（テナント）の確認はしない：トークン自体が対象ユーザーを一意に特定するため。
 */
export const ResetPassword: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';

  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [messages, setMessages] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessages([]);

    if (password !== passwordConfirm) {
      setMessages(['パスワードが一致しません。']);
      return;
    }

    setSaving(true);
    try {
      const result = await confirmPasswordReset(token, password);
      if (result.ok) {
        setDone(true);
        return;
      }
      setMessages(result.errors.map((err) => err.message));
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['サーバーとの通信に失敗しました。']);
    } finally {
      setSaving(false);
    }
  };

  if (!token) {
    return (
      <div style={{ maxWidth: '400px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
        <h2>パスワード再設定</h2>
        <TopMessage
          messages="リンクが正しくありません。メールに記載のリンクから、もう一度開き直してください。"
          isError
        />
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '400px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>パスワード再設定</h2>

      {done ? (
        <>
          <TopMessage messages="パスワードを再設定しました。新しいパスワードでログインしてください。" isError={false} />
          <button
            type="button"
            onClick={() => navigate('/staff')}
            style={{
              width: '100%',
              padding: '10px',
              marginTop: '10px',
              backgroundColor: '#007bff',
              color: '#fff',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
            }}
          >
            ログイン画面へ
          </button>
        </>
      ) : (
        <form onSubmit={handleSubmit}>
          <TopMessage messages={messages} isError />
          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', marginBottom: '5px' }}>新しいパスワード:</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
          </div>
          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', marginBottom: '5px' }}>新しいパスワード（確認）:</label>
            <input
              type="password"
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
              required
              style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
          </div>
          <p style={{ fontSize: '12px', color: '#666', marginTop: '-8px', marginBottom: '15px' }}>
            8文字以上、半角の英字と数字を両方含めてください。
          </p>
          <button
            type="submit"
            disabled={saving}
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
            {saving ? '処理中...' : 'パスワードを再設定する'}
          </button>
        </form>
      )}
    </div>
  );
};

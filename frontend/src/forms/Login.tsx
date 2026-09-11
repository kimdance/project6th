import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom'; // ★useLocationを追加
import { getTenantApiBaseUrl } from '../config';
import { TopMessage } from '../components/TopMessage';

interface LoginFormData {
  email: string;
  password: string;
}

export const Login: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation(); // ★画面遷移時の state を受け取るフック

  // ★遷移元から受け取ったデータがあれば初期値にセット
  const initialState = location.state as { email?: string; password?: string } | null;

  const [formData, setFormData] = useState<LoginFormData>({
    email: initialState?.email || '',
    password: initialState?.password || '',
  });

  const [message, setMessage] = useState<string>(
    initialState?.email ? 'ユーザー登録が完了しました。ログインしてください。' : ''
  );
  const [isLoading, setIsLoading] = useState<boolean>(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage('');
    setIsLoading(true);

    try {
      const response = await fetch(`${getTenantApiBaseUrl()}/api/v1/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(formData),
      });

      if (response.ok) {
        const data: { accessToken: string; refreshToken: string; tokenType: string } =
          await response.json();
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        navigate('/home');
        return;
      } else if (response.status === 404) {
        setMessage('このサブドメインに対応する会社が見つかりません。URLを確認してください。');
      } else {
        setMessage('ログインIDまたはパスワードが正しくありません。');
      }
    } catch (error) {
      console.error('通信エラー:', error);
      setMessage('サーバーとの通信に失敗しました。');
    } finally {
      setIsLoading(false);
    }
  };

  const handleRegisterRedirect = () => {
    navigate('/register');
  };

  return (
    <div style={{ maxWidth: '400px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>ログイン</h2>

      <TopMessage 
        messages={message} 
        isError={!message.includes('成功') && !message.includes('完了')} 
      />
      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '15px' }}>
          <label style={{ display: 'block', marginBottom: '5px' }}>ログインID（メールアドレス）:</label>
          <input
            type="email"
            name="email"
            value={formData.email}
            onChange={handleChange}
            required
            style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
          />
        </div>

        <div style={{ marginBottom: '15px' }}>
          <label style={{ display: 'block', marginBottom: '5px' }}>パスワード:</label>
          <input
            type="password"
            name="password"
            value={formData.password}
            onChange={handleChange}
            required
            style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
          />
        </div>

        <div style={{ textAlign: 'right', marginBottom: '15px' }}>
          <button
            type="button"
            onClick={() => navigate('/forgot-password')}
            style={{
              background: 'none',
              border: 'none',
              color: '#007bff',
              cursor: 'pointer',
              padding: 0,
              fontSize: '13px',
              textDecoration: 'underline',
            }}
          >
            パスワードをお忘れですか？
          </button>
        </div>

        <button
          type="submit"
          disabled={isLoading}
          style={{
            width: '100%',
            padding: '10px',
            backgroundColor: '#007bff',
            color: '#fff',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
            marginBottom: '10px',
          }}
        >
          {isLoading ? '処理中...' : 'ログイン'}
        </button>

        <button
          type="button"
          onClick={handleRegisterRedirect}
          style={{
            width: '100%',
            padding: '10px',
            backgroundColor: '#6c757d',
            color: '#fff',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
          }}
        >
          ユーザ登録
        </button>
      </form>
    </div>
  );
};
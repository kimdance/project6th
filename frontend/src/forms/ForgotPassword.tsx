import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TopMessage } from '../components/TopMessage';
import { requestPasswordReset } from '../api/passwordReset';

/**
 * パスワードを忘れた場合のリセット申し込み画面（FR-A04）。
 * 会社はURLサブドメインで確定済みのため、入力はメールアドレスのみ。
 * 該当するアカウントの有無にかかわらず同じ案内文を表示する（登録有無を漏らさないため）。
 */
export const ForgotPassword: React.FC = () => {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [sending, setSending] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSending(true);
    try {
      await requestPasswordReset(email);
    } catch (error) {
      console.error('通信エラー:', error);
    } finally {
      setSending(false);
      // 通信エラーの場合も含め、登録有無を漏らさないため常に同じ案内を表示する。
      setSubmitted(true);
    }
  };

  return (
    <div style={{ maxWidth: '400px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>パスワードをお忘れの方</h2>

      {submitted ? (
        <>
          <TopMessage
            messages="ご入力いただいたメールアドレス宛に、パスワード再設定のご案内をお送りしました（該当するアカウントがある場合のみ届きます）。メールをご確認ください。"
            isError={false}
          />
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
        </>
      ) : (
        <form onSubmit={handleSubmit}>
          <p style={{ color: '#666', marginBottom: '15px' }}>
            登録済みのメールアドレスを入力してください。パスワード再設定のご案内をお送りします。
          </p>
          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', marginBottom: '5px' }}>メールアドレス:</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
          </div>
          <button
            type="submit"
            disabled={sending}
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
            {sending ? '送信中...' : '再設定メールを送信'}
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
      )}
    </div>
  );
};

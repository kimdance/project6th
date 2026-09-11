import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TopMessage } from '../components/TopMessage';
import { fetchMe, updateProfile, ROLE_LABELS, type Me, type ProfileUpdateRequest } from '../api/session';

const EMPTY_FORM: ProfileUpdateRequest = { name: '', email: '', telnumber: '' };

/**
 * アカウント設定画面。ログイン中の本人が自分の氏名・メールアドレス・電話番号を変更する。
 * 役割・所属店舗はここでは変更できない（経営管理者が「ユーザー管理」画面で行う）。
 */
export const MyAccountPage: React.FC = () => {
  const navigate = useNavigate();

  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState<ProfileUpdateRequest>(EMPTY_FORM);

  const [messages, setMessages] = useState<string[]>([]);
  const [errorFields, setErrorFields] = useState<string[]>([]);
  const [successMessage, setSuccessMessage] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;

    fetchMe().then((result) => {
      if (cancelled) {
        return;
      }
      if (!result) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        navigate('/', { replace: true });
        return;
      }
      setMe(result);
      setForm({ name: result.name, email: result.email, telnumber: result.telnumber ?? '' });
      setLoading(false);
    });

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  const clearFieldError = (field: string) => {
    if (errorFields.includes(field)) {
      setErrorFields((prev) => prev.filter((f) => f !== field));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessages([]);
    setErrorFields([]);
    setSuccessMessage('');
    setSaving(true);

    try {
      const result = await updateProfile(form);
      if (result.ok) {
        setMe(result.me);
        setForm({ name: result.me.name, email: result.me.email, telnumber: result.me.telnumber ?? '' });
        setSuccessMessage('保存しました。');
        return;
      }
      setMessages(result.errors.map((err) => err.message));
      setErrorFields(Array.from(new Set(result.errors.flatMap((err) => err.fields))));
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['サーバーとの通信に失敗しました。']);
    } finally {
      setSaving(false);
    }
  };

  const getInputStyle = (field: string) => ({
    width: '100%',
    padding: '8px',
    marginTop: '5px',
    boxSizing: 'border-box' as const,
    backgroundColor: errorFields.includes(field) ? '#f8d7da' : '#fff',
    borderColor: errorFields.includes(field) ? '#dc3545' : '#ccc',
    borderStyle: 'solid',
    borderWidth: '1px',
  });

  if (loading || !me) {
    return (
      <div style={{ maxWidth: '400px', margin: '40px auto', padding: '20px' }}>
        <p>読み込み中...</p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '400px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>アカウント設定</h2>
      <p style={{ color: '#666', marginBottom: '15px' }}>
        役割: {ROLE_LABELS[me.role]}（役割・所属店舗の変更は経営管理者にご依頼ください）
      </p>

      <TopMessage messages={messages} isError />
      <TopMessage messages={successMessage} isError={false} />

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '15px' }}>
          <label style={{ display: 'block', marginBottom: '5px' }}>お名前:</label>
          <input
            type="text"
            value={form.name}
            onChange={(e) => {
              setForm((prev) => ({ ...prev, name: e.target.value }));
              clearFieldError('name');
            }}
            required
            style={getInputStyle('name')}
          />
        </div>

        <div style={{ marginBottom: '15px' }}>
          <label style={{ display: 'block', marginBottom: '5px' }}>メールアドレス（ログインID）:</label>
          <input
            type="email"
            value={form.email}
            onChange={(e) => {
              setForm((prev) => ({ ...prev, email: e.target.value }));
              clearFieldError('email');
            }}
            required
            style={getInputStyle('email')}
          />
        </div>

        <div style={{ marginBottom: '15px' }}>
          <label style={{ display: 'block', marginBottom: '5px' }}>電話番号（任意）:</label>
          <input
            type="tel"
            value={form.telnumber}
            onChange={(e) => setForm((prev) => ({ ...prev, telnumber: e.target.value }))}
            style={getInputStyle('telnumber')}
          />
        </div>

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
          {saving ? '処理中...' : '保存する'}
        </button>

        <button
          type="button"
          onClick={() => navigate('/home')}
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
          ホームに戻る
        </button>
      </form>
    </div>
  );
};

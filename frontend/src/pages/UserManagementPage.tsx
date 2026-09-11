import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TopMessage } from '../components/TopMessage';
import { fetchMe, ROLE_LABELS, type Me, type Role } from '../api/session';
import { fetchStores, type Store } from '../api/stores';
import { fetchUsers, updateUser, type UserSummary } from '../api/users';

const EDITABLE_ROLES: Role[] = ['OWNER', 'MANAGER', 'HALL', 'KITCHEN', 'PARTTIME'];

/** 店舗未割り当てを表す値。HTML の <select> は文字列しか扱えないため空文字で表現する。 */
const NO_STORE_VALUE = '';

type View = 'list' | 'edit';

/**
 * ユーザー管理画面。ユーザー登録画面（FR-A03）は役割を「スタッフ」「アルバイト」のみに限定し、
 * 店舗も選ばせないため、店長・経営管理者への変更と店舗の割り当ては、経営管理者がここで行う
 * （02_requirements.md §3.2「ユーザーの権限変更」）。
 */
export const UserManagementPage: React.FC = () => {
  const navigate = useNavigate();

  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [stores, setStores] = useState<Store[]>([]);
  const [view, setView] = useState<View>('list');
  const [editingUser, setEditingUser] = useState<UserSummary | null>(null);
  const [editRole, setEditRole] = useState<Role>('HALL');
  const [editStoreId, setEditStoreId] = useState<string>(NO_STORE_VALUE);
  const [editRetired, setEditRetired] = useState(false);

  const [messages, setMessages] = useState<string[]>([]);
  const [errorFields, setErrorFields] = useState<string[]>([]);
  const [successMessage, setSuccessMessage] = useState('');
  const [saving, setSaving] = useState(false);

  const loadUsers = async () => {
    const [userList, storeList] = await Promise.all([fetchUsers(), fetchStores()]);
    setUsers(userList);
    setStores(storeList);
  };

  useEffect(() => {
    let cancelled = false;

    (async () => {
      const meResult = await fetchMe();
      if (cancelled) {
        return;
      }
      if (!meResult) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        navigate('/', { replace: true });
        return;
      }
      setMe(meResult);

      if (meResult.role === 'OWNER') {
        await loadUsers();
      }
      if (!cancelled) {
        setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigate]);

  const openEdit = (user: UserSummary) => {
    setMessages([]);
    setErrorFields([]);
    setSuccessMessage('');
    setEditingUser(user);
    setEditRole(user.role);
    setEditStoreId(user.storeId !== null ? String(user.storeId) : NO_STORE_VALUE);
    setEditRetired(user.status === 'RETIRED');
    setView('edit');
  };

  const backToList = () => {
    setMessages([]);
    setErrorFields([]);
    setSuccessMessage('');
    setView('list');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingUser) {
      return;
    }
    setMessages([]);
    setErrorFields([]);
    setSuccessMessage('');
    setSaving(true);

    try {
      const result = await updateUser(editingUser.id, {
        role: editRole,
        storeId: editStoreId === NO_STORE_VALUE ? null : Number(editStoreId),
        status: editRetired ? 'RETIRED' : 'ACTIVE',
      });
      if (result.ok) {
        setUsers((prev) => prev.map((u) => (u.id === result.user.id ? result.user : u)));
        setSuccessMessage(`「${result.user.name}」の設定を保存しました。`);
        setView('list');
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

  if (loading || !me) {
    return (
      <div style={{ maxWidth: '560px', margin: '40px auto', padding: '20px' }}>
        <p>読み込み中...</p>
      </div>
    );
  }

  if (me.role !== 'OWNER') {
    return (
      <div style={{ maxWidth: '560px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
        <h2>ユーザー管理</h2>
        <p style={{ color: '#666' }}>この画面は経営管理者のみご利用いただけます。</p>
        <BackToHomeButton onClick={() => navigate('/home')} />
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '560px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>ユーザー管理</h2>

      <TopMessage messages={messages} isError />
      <TopMessage messages={successMessage} isError={false} />

      {view === 'list' && (
        <div style={{ marginBottom: '15px' }}>
          {users.length === 0 && <p style={{ color: '#666' }}>ユーザーがいません。</p>}
          {users.map((user) => (
            <button
              key={user.id}
              type="button"
              onClick={() => openEdit(user)}
              style={{
                display: 'block',
                width: '100%',
                textAlign: 'left',
                padding: '12px 16px',
                marginBottom: '8px',
                border: '1px solid #ddd',
                borderRadius: '8px',
                background: user.status === 'RETIRED' ? '#f5f5f5' : '#fff',
                opacity: user.status === 'RETIRED' ? 0.6 : 1,
                cursor: 'pointer',
              }}
            >
              <div style={{ fontWeight: 600 }}>
                {user.name}
                {user.status === 'RETIRED' && '　[退職済み]'}
              </div>
              <div style={{ fontSize: '13px', color: '#666' }}>{user.email}</div>
              <div style={{ fontSize: '13px', color: '#666', marginTop: '4px' }}>
                {ROLE_LABELS[user.role]}
                {' ・ '}
                {user.storeName ?? '店舗未設定（全店）'}
                {user.status === 'LOCKED' && (
                  <span style={{ color: '#dc3545' }}> ・ ロック中</span>
                )}
              </div>
            </button>
          ))}
        </div>
      )}

      {view === 'edit' && editingUser && (
        <form onSubmit={handleSubmit}>
          <p style={{ marginBottom: '15px' }}>
            <strong>{editingUser.name}</strong>（{editingUser.email}）
          </p>

          <FormField label="役割">
            <select
              value={editRole}
              onChange={(e) => setEditRole(e.target.value as Role)}
              style={getInputStyle(errorFields, 'role')}
            >
              {EDITABLE_ROLES.map((role) => (
                <option key={role} value={role}>
                  {ROLE_LABELS[role]}
                </option>
              ))}
            </select>
          </FormField>

          <FormField label="所属店舗">
            <select
              value={editStoreId}
              onChange={(e) => setEditStoreId(e.target.value)}
              style={getInputStyle(errorFields, 'storeId')}
            >
              <option value={NO_STORE_VALUE}>店舗未設定（全店）</option>
              {stores.map((store) => (
                <option key={store.id} value={store.id}>
                  {store.name}
                </option>
              ))}
            </select>
          </FormField>

          <div style={{ marginBottom: '15px' }}>
            <label>
              <input
                type="checkbox"
                checked={editRetired}
                onChange={(e) => setEditRetired(e.target.checked)}
                style={{ marginRight: '8px' }}
              />
              退職済みにする（チェックするとログインできなくなります。データは残ります）
            </label>
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
          <BackToListButton onClick={backToList} />
        </form>
      )}

      <div style={{ marginTop: '20px' }}>
        <BackToHomeButton onClick={() => navigate('/home')} />
      </div>
    </div>
  );
};

const getInputStyle = (errorFields: string[], field: string) => ({
  width: '100%',
  padding: '8px',
  marginTop: '5px',
  boxSizing: 'border-box' as const,
  backgroundColor: errorFields.includes(field) ? '#f8d7da' : '#fff',
  borderColor: errorFields.includes(field) ? '#dc3545' : '#ccc',
  borderStyle: 'solid',
  borderWidth: '1px',
});

const FormField: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={{ marginBottom: '15px' }}>
    <label style={{ display: 'block', marginBottom: '5px' }}>{label}:</label>
    {children}
  </div>
);

const BackToListButton: React.FC<{ onClick: () => void }> = ({ onClick }) => (
  <button
    type="button"
    onClick={onClick}
    style={{
      width: '100%',
      padding: '10px',
      marginTop: '10px',
      backgroundColor: '#fff',
      color: '#333',
      border: '1px solid #ccc',
      borderRadius: '4px',
      cursor: 'pointer',
    }}
  >
    ← ユーザー一覧に戻る
  </button>
);

const BackToHomeButton: React.FC<{ onClick: () => void }> = ({ onClick }) => (
  <button
    type="button"
    onClick={onClick}
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
    ホームに戻る
  </button>
);

import { useNavigate } from 'react-router-dom';

/**
 * 入口だけ先に用意し、中身は未実装の機能画面のための仮ページ。
 * 各機能の画面を実装するタイミングで、このコンポーネントを本実装に置き換える。
 */
export const FeaturePlaceholder = ({ title }: { title: string }) => {
  const navigate = useNavigate();

  return (
    <div style={{ maxWidth: '600px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>{title}</h2>
      <p style={{ color: '#666' }}>この画面は現在準備中です。次のステップで実装します。</p>
      <button
        type="button"
        onClick={() => navigate('/home')}
        style={{
          padding: '10px 20px',
          backgroundColor: '#6c757d',
          color: '#fff',
          border: 'none',
          borderRadius: '4px',
          cursor: 'pointer',
        }}
      >
        ホームに戻る
      </button>
    </div>
  );
};

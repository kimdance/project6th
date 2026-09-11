import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';

/**
 * アクセストークンを持たない状態でログイン必須の画面に来た場合に、ログイン画面へ戻す簡易ガード。
 * トークンの有効性（失効・改ざん）はバックエンド側で検証するため、ここでは存在チェックのみ行う。
 */
export const RequireAuth = ({ children }: { children: ReactNode }) => {
  const hasToken = !!localStorage.getItem('accessToken');
  return hasToken ? <>{children}</> : <Navigate to="/" replace />;
};

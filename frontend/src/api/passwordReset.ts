import { authedFetch, extractErrors, type ErrorItem } from './http';

/**
 * パスワードを忘れた場合のリセットメール送信を申し込む。会社はURLサブドメインから解決するため
 * ボディはメールアドレスのみで、ログイン不要（未認証）で呼べる。
 * 該当するアカウントが無い場合も含め、常に成功したように振る舞う（登録有無を漏らさないため）。
 */
export async function requestPasswordReset(email: string): Promise<void> {
  await authedFetch('/api/v1/auth/password-reset', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

export async function confirmPasswordReset(
  token: string,
  password: string
): Promise<{ ok: true } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch('/api/v1/auth/password-reset/confirm', {
    method: 'POST',
    body: JSON.stringify({ token, password }),
  });
  if (res.ok) {
    return { ok: true };
  }
  return { ok: false, errors: await extractErrors(res) };
}

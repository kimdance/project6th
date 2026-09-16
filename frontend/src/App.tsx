import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Login } from './forms/Login';
import { PublicReservationPage } from './pages/PublicReservationPage';
import { UserRegister } from './forms/UserRegister';
import { ForgotPassword } from './forms/ForgotPassword';
import { ResetPassword } from './forms/ResetPassword';
import { Home } from './pages/Home';
import { MyAccountPage } from './pages/MyAccountPage';
import { StoreSettingsPage } from './pages/StoreSettingsPage';
import { TablesPage } from './pages/TablesPage';
import { MenuManagementPage } from './pages/MenuManagementPage';
import { ReservationsPage } from './pages/ReservationsPage';
import { UserManagementPage } from './pages/UserManagementPage';
import { AuditLogPage } from './pages/AuditLogPage';
import { FeaturePlaceholder } from './pages/FeaturePlaceholder';
import { RequireAuth } from './components/RequireAuth';
import { AppHeader } from './components/AppHeader';
import './App.css';

function App() {
  return (
    <BrowserRouter>
      <div className="App">
        {/* 全画面共通のヘッダー。ここに1箇所だけ置くことで、今後追加する画面にも自動で表示される。 */}
        <AppHeader />
        <main>
          <Routes>
            {/* パスなし＝お客様向けの入口（Web予約フォーム。FR-C03。ログイン不要）。
                04_architecture.md §6.1 2026-09-15追補：スタッフ用ログインは /staff に分離した。 */}
            <Route path="/" element={<PublicReservationPage />} />
            <Route path="/staff" element={<Login />} />

            {/* ログイン後の共通トップ画面（機能の入口を並べるメニュー） */}
            <Route
              path="/home"
              element={
                <RequireAuth>
                  <Home />
                </RequireAuth>
              }
            />

            {/* ユーザー登録画面 */}
            <Route path="/register" element={<UserRegister />} />

            {/* パスワードを忘れた場合のリセット（FR-A04）。どちらも未ログインで開ける。 */}
            <Route path="/forgot-password" element={<ForgotPassword />} />
            <Route path="/reset-password" element={<ResetPassword />} />

            {/* アカウント設定（自分の氏名・メールアドレス・電話番号の変更。全ロール共通） */}
            <Route
              path="/account"
              element={
                <RequireAuth>
                  <MyAccountPage />
                </RequireAuth>
              }
            />

            {/* ユーザー管理（役割・所属店舗の変更。経営管理者のみ） */}
            <Route
              path="/users"
              element={
                <RequireAuth>
                  <UserManagementPage />
                </RequireAuth>
              }
            />

            {/* 監査ログ（重要操作の検索・閲覧。経営管理者のみ。FR-J04） */}
            <Route
              path="/audit-logs"
              element={
                <RequireAuth>
                  <AuditLogPage />
                </RequireAuth>
              }
            />

            {/* 予約台帳（電話予約・当日ウォークインの登録・変更・キャンセル。FR-C01・C02・C09） */}
            <Route
              path="/reservations"
              element={
                <RequireAuth>
                  <ReservationsPage />
                </RequireAuth>
              }
            />

            {/* 各機能の入口。実装済みの画面から順に置き換えていく（残りは当面、仮ページ）。 */}
            <Route
              path="/settings/store"
              element={
                <RequireAuth>
                  <StoreSettingsPage />
                </RequireAuth>
              }
            />
            <Route
              path="/settings/tables"
              element={
                <RequireAuth>
                  <TablesPage />
                </RequireAuth>
              }
            />
            <Route
              path="/settings/payment-methods"
              element={
                <RequireAuth>
                  <FeaturePlaceholder title="決済手段" />
                </RequireAuth>
              }
            />
            <Route
              path="/settings/business-days"
              element={
                <RequireAuth>
                  <FeaturePlaceholder title="営業日" />
                </RequireAuth>
              }
            />

            {/* メニュー管理（登録・編集・売り切れ/提供停止の切替。FR-D01〜D03） */}
            <Route
              path="/settings/menu"
              element={
                <RequireAuth>
                  <MenuManagementPage />
                </RequireAuth>
              }
            />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}

export default App;
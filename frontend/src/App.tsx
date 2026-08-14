import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Login } from './forms/Login';
import { UserRegister } from './forms/UserRegister';
import './App.css';

function App() {
  return (
    <BrowserRouter>
      <div className="App">
        <main>
          <Routes>
            {/* 初期画面: ログイン */}
            <Route path="/" element={<Login />} />
            
            {/* ユーザー登録画面 */}
            <Route path="/register" element={<UserRegister />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}

export default App;
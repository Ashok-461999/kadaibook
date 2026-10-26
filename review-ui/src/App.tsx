import { useCallback, useEffect, useState } from "react";
import { api, getToken, setToken } from "./api";
import type { User } from "./types";
import Login from "./components/Login";
import ShopShell from "./components/ShopShell";

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [checking, setChecking] = useState(Boolean(getToken()));

  useEffect(() => {
    if (!getToken()) {
      setChecking(false);
      return;
    }
    api
      .me()
      .then(setUser)
      .catch(() => {
        setToken(null);
        setUser(null);
      })
      .finally(() => setChecking(false));
  }, []);

  useEffect(() => {
    const logout = () => setUser(null);
    window.addEventListener("kadaibook:logout", logout);
    return () => window.removeEventListener("kadaibook:logout", logout);
  }, []);

  const onAuthenticated = useCallback((token: string, nextUser: User) => {
    setToken(token);
    setUser(nextUser);
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  if (checking) {
    return (
      <div className="boot-screen">
        <div className="brand-mark">க</div>
        <p>Opening KadaiBook…</p>
      </div>
    );
  }

  if (!user) return <Login onAuthenticated={onAuthenticated} />;
  return <ShopShell user={user} onLogout={logout} />;
}

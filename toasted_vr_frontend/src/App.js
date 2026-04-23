import React, { useState } from 'react';
import './App.css';
import esTexts from './locals/es.json';
import AdminUserManagement from './components/AdminUserManagement';
import LoginForm from './components/LoginForm';
import RegisterForm from './components/RegisterForm';
import VerificationForm from './components/VerificationForm';
import { logoutUser } from './services/authService';
import { clearSession, readSession, saveSession } from './services/sessionService';

const authViews = {
  register: 'register',
  login: 'login'
};

function App() {
  const registerTexts = esTexts.auth.register;
  const loginTexts = esTexts.auth.login;
  const adminTexts = esTexts.admin;
  const accessDeniedTexts = esTexts.accessDenied;
  const brand = esTexts.app.brand;

  const [authView, setAuthView] = useState(authViews.register);
  const [pendingRegistration, setPendingRegistration] = useState(null);
  const [verifiedUser, setVerifiedUser] = useState(null);
  const [session, setSession] = useState(() => readSession());

  const currentUser = session?.user ?? null;
  const isAdmin = currentUser?.role === 'ADMIN';

  const handleRegistrationSuccess = (registrationData) => {
    setPendingRegistration(registrationData);
    setVerifiedUser(null);
  };

  const handleVerificationSuccess = (user) => {
    setVerifiedUser(user);
  };

  const handleRestartRegistration = () => {
    setPendingRegistration(null);
    setVerifiedUser(null);
  };

  const handleSwitchAuthView = (nextView) => {
    setAuthView(nextView);

    if (nextView === authViews.login) {
      setPendingRegistration(null);
      setVerifiedUser(null);
    }
  };

  const handleLoginSuccess = (loginResponse) => {
    const nextSession = {
      accessToken: loginResponse.accessToken,
      expiresAt: loginResponse.expiresAt,
      user: loginResponse.user
    };

    saveSession(nextSession);
    setSession(nextSession);
  };

  const handleLogout = async () => {
    try {
      await logoutUser();
    } catch (error) {
      // La sesion local debe cerrarse incluso si el token ya no es valido.
    } finally {
      clearSession();
      setSession(null);
      setAuthView(authViews.login);
      setPendingRegistration(null);
      setVerifiedUser(null);
    }
  };

  if (currentUser && isAdmin) {
    return (
      <div className="app-shell admin-shell">
        <div className="ambient-light ambient-light-left" />
        <div className="ambient-light ambient-light-right" />

        <main className="app-frame app-frame-wide">
          <AdminUserManagement texts={adminTexts} currentUser={currentUser} onLogout={handleLogout} />
        </main>
      </div>
    );
  }

  if (currentUser && !isAdmin) {
    return (
      <div className="app-shell">
        <div className="ambient-light ambient-light-left" />
        <div className="ambient-light ambient-light-right" />

        <main className="auth-card">
          <header className="hero-copy">
            <p className="eyebrow">{brand}</p>
            <h1 className="centered-title">{accessDeniedTexts.title}</h1>
            <p className="subtitle">{accessDeniedTexts.subtitle}</p>
          </header>

          <section className="success-panel" aria-live="polite">
            <div className="success-badge">{accessDeniedTexts.badge}</div>
            <p className="success-email">{currentUser.username}</p>
            <p className="helper-copy">{accessDeniedTexts.helper}</p>
            <button type="button" onClick={handleLogout}>
              {accessDeniedTexts.button}
            </button>
          </section>
        </main>
      </div>
    );
  }

  const currentTitle = authView === authViews.login
    ? loginTexts.title
    : verifiedUser
      ? registerTexts.success.title
      : pendingRegistration
        ? registerTexts.verification.title
        : registerTexts.title;

  const currentSubtitle = authView === authViews.login
    ? loginTexts.subtitle
    : verifiedUser
      ? registerTexts.success.subtitle
      : pendingRegistration
        ? registerTexts.verification.subtitle
        : registerTexts.subtitle;

  return (
    <div className="app-shell">
      <div className="ambient-light ambient-light-left" />
      <div className="ambient-light ambient-light-right" />

      <main className="auth-card">
        <header className="hero-copy">
          <p className="eyebrow">{brand}</p>
          <h1 className="centered-title">{currentTitle}</h1>
          {currentSubtitle && <p className="subtitle">{currentSubtitle}</p>}
        </header>

        <div className="tab-strip" role="tablist" aria-label={esTexts.auth.switcherLabel}>
          <button
            type="button"
            role="tab"
            className={`tab-button ${authView === authViews.register ? 'is-active' : ''}`}
            aria-selected={authView === authViews.register}
            onClick={() => handleSwitchAuthView(authViews.register)}
          >
            {registerTexts.switchLabel}
          </button>
          <button
            type="button"
            role="tab"
            className={`tab-button ${authView === authViews.login ? 'is-active' : ''}`}
            aria-selected={authView === authViews.login}
            onClick={() => handleSwitchAuthView(authViews.login)}
          >
            {loginTexts.switchLabel}
          </button>
        </div>

        {authView === authViews.register && !pendingRegistration && (
          <RegisterForm texts={registerTexts} onRegistrationSuccess={handleRegistrationSuccess} />
        )}

        {authView === authViews.register && pendingRegistration && !verifiedUser && (
          <VerificationForm
            email={pendingRegistration.email}
            expiresInMinutes={pendingRegistration.expiresInMinutes}
            texts={registerTexts}
            onVerificationSuccess={handleVerificationSuccess}
          />
        )}

        {authView === authViews.register && verifiedUser && (
          <section className="success-panel" aria-live="polite">
            <div className="success-badge">{registerTexts.success.badge}</div>
            <h2>{registerTexts.success.title}</h2>
            <p>{registerTexts.success.accountCreated}</p>
            <p className="success-email">{verifiedUser.email}</p>
            <button type="button" onClick={() => handleSwitchAuthView(authViews.login)}>
              {registerTexts.buttons.goToLogin}
            </button>
            <button type="button" className="secondary-button" onClick={handleRestartRegistration}>
              {registerTexts.buttons.createAnother}
            </button>
          </section>
        )}

        {authView === authViews.login && (
          <LoginForm texts={loginTexts} onLoginSuccess={handleLoginSuccess} />
        )}
      </main>
    </div>
  );
}

export default App;

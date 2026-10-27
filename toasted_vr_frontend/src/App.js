import React, { useEffect, useState } from 'react';
import './App.css';
import esTexts from './locals/es.json';
import AdminUserManagement from './components/AdminUserManagement';
import KnowledgeLevelSelection from './components/KnowledgeLevelSelection';
import RoastingSimulation from './components/simulation/RoastingSimulation';
import LoginForm from './components/LoginForm';
import RegisterForm from './components/RegisterForm';
import VerificationForm from './components/VerificationForm';
import ForgotPasswordForm from './components/ForgotPasswordForm';
import PasswordResetForm from './components/PasswordResetForm';
import { logoutUser } from './services/authService';
import { ACCOUNT_BLOCKED_EVENT, SESSION_REVOKED_EVENT } from './services/apiClient';
import { clearSession, readSession, saveSession } from './services/sessionService';

const authViews = {
  entry: 'entry',
  register: 'register',
  login: 'login',
  forgotPassword: 'forgotPassword'
};

function App() {
  const registerTexts = esTexts.auth.register;
  const loginTexts = esTexts.auth.login;
  const adminTexts = esTexts.admin;
  const brand = esTexts.app.brand;

  const [authView, setAuthView] = useState(authViews.entry);
  const [pendingRegistration, setPendingRegistration] = useState(null);
  const [verifiedUser, setVerifiedUser] = useState(null);
  const [session, setSession] = useState(() => readSession());
  const [loginNotice, setLoginNotice] = useState('');
  const [passwordReset, setPasswordReset] = useState(null);
  const [isPasswordResetDone, setIsPasswordResetDone] = useState(false);
  const [isResetCodeVerified, setIsResetCodeVerified] = useState(false);
  const passwordResetTexts = esTexts.auth.passwordReset;

  const simulationTexts = esTexts.simulation;
  const knowledgeLevelTexts = esTexts.knowledgeLevel;
  const profileTexts = esTexts.profile;
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
    setLoginNotice('');
    setPasswordReset(null);
    setIsPasswordResetDone(false);
    setIsResetCodeVerified(false);

    if (nextView !== authViews.register) {
      setPendingRegistration(null);
      setVerifiedUser(null);
    }
  };

  // Cuenta registrada pero sin verificar: se abre la verificación con la
  // cuenta regresiva en 0 para que el usuario pida un código nuevo.
  const handleVerifyAccount = (email) => {
    setPendingRegistration({ email, codePolicy: null });
    setVerifiedUser(null);
    setLoginNotice('');
    setAuthView(authViews.register);
  };

  const handleLoginSuccess = (loginResponse) => {
    const nextSession = {
      accessToken: loginResponse.accessToken,
      refreshToken: loginResponse.refreshToken,
      expiresAt: loginResponse.expiresAt,
      user: loginResponse.user
    };

    saveSession(nextSession);
    setSession(nextSession);
    setLoginNotice('');
  };

  const handleKnowledgeLevelSet = (updatedUser) => {
    const nextSession = { ...session, user: updatedUser };
    saveSession(nextSession);
    setSession(nextSession);
  };

  const handleUserUpdate = (updatedUser) => {
    const nextSession = { ...session, user: updatedUser };
    saveSession(nextSession);
    setSession(nextSession);
  };

  const resetToLoggedOutState = () => {
    clearSession();
    setSession(null);
    setAuthView(authViews.entry);
    setPendingRegistration(null);
    setVerifiedUser(null);
  };

  const handleLogout = async () => {
    try {
      await logoutUser();
    } catch (error) {
      // La sesion local debe cerrarse incluso si el token ya no es valido.
    } finally {
      resetToLoggedOutState();
    }
  };

  // apiClient dispara este evento cuando el refresh token tambien expiro
  // (o no se pudo renovar la sesion), para volver a la pantalla de login.
  useEffect(() => {
    window.addEventListener('toastedvr:session-expired', resetToLoggedOutState);
    return () => window.removeEventListener('toastedvr:session-expired', resetToLoggedOutState);
  }, []);

  // El backend rechazó la sesión sin posibilidad de renovarla (cuenta
  // bloqueada o contraseña cambiada en otro dispositivo): se lleva al usuario
  // al login con el aviso correspondiente.
  useEffect(() => {
    const noticesByEvent = {
      [ACCOUNT_BLOCKED_EVENT]: esTexts.auth.errors.ACCOUNT_BLOCKED,
      [SESSION_REVOKED_EVENT]: esTexts.auth.errors.SESSION_REVOKED
    };
    const handlers = Object.entries(noticesByEvent).map(([eventName, notice]) => {
      const handler = () => {
        resetToLoggedOutState();
        setAuthView(authViews.login);
        setLoginNotice(notice);
      };
      window.addEventListener(eventName, handler);
      return [eventName, handler];
    });

    return () => handlers.forEach(([eventName, handler]) => window.removeEventListener(eventName, handler));
  }, []);

  if (currentUser && isAdmin) {
    return (
      <div className="app-shell admin-shell">
        <div className="ambient-light ambient-light-left" />
        <div className="ambient-light ambient-light-right" />

        <main className="admin-page-frame">
          <AdminUserManagement
            texts={adminTexts}
            profileTexts={profileTexts}
            currentUser={currentUser}
            onLogout={handleLogout}
            onUserUpdate={handleUserUpdate}
          />
        </main>
      </div>
    );
  }

  if (currentUser && !isAdmin) {
    if (currentUser.knowledgeLevel == null) {
      return (
        <KnowledgeLevelSelection
          texts={knowledgeLevelTexts}
          currentUser={currentUser}
          onSuccess={handleKnowledgeLevelSet}
          onLogout={handleLogout}
        />
      );
    }

    return (
      <div className="app-shell admin-shell">
        <div className="ambient-light ambient-light-left" />
        <div className="ambient-light ambient-light-right" />

        <main className="app-frame app-frame-wide">
          <RoastingSimulation
            texts={simulationTexts}
            profileTexts={profileTexts}
            knowledgeTexts={knowledgeLevelTexts}
            currentUser={currentUser}
            onLogout={handleLogout}
            onUserUpdate={handleUserUpdate}
          />
        </main>
      </div>
    );
  }

  const isPasswordResetView = authView === authViews.forgotPassword;
  const passwordResetTitle = isPasswordResetDone
    ? passwordResetTexts.success.title
    : isResetCodeVerified
      ? passwordResetTexts.resetTitle
      : passwordReset
        ? passwordResetTexts.codeTitle
        : passwordResetTexts.title;
  const passwordResetSubtitle = isPasswordResetDone
    ? passwordResetTexts.success.subtitle
    : isResetCodeVerified
      ? passwordResetTexts.resetSubtitle
      : passwordReset
        ? passwordResetTexts.codeSubtitle
        : passwordResetTexts.subtitle;

  const handleRestartPasswordReset = () => {
    setPasswordReset(null);
    setIsResetCodeVerified(false);
  };

  const currentTitle = isPasswordResetView
    ? passwordResetTitle
    : authView === authViews.login
    ? loginTexts.title
    : verifiedUser
      ? registerTexts.success.title
      : pendingRegistration
        ? registerTexts.verification.title
        : registerTexts.title;

  const currentSubtitle = isPasswordResetView
    ? passwordResetSubtitle
    : authView === authViews.login
    ? loginTexts.subtitle
    : verifiedUser
      ? registerTexts.success.subtitle
      : pendingRegistration
        ? registerTexts.verification.subtitle
        : registerTexts.subtitle;

  const isEntryView = authView === authViews.entry;
  const showBackButton = !isEntryView && !verifiedUser && !isPasswordResetDone;
  const isAuthPanelActive = !isEntryView;
  // Solo el formulario de registro (no la verificación ni el éxito) usa la
  // tarjeta ancha con campos en dos columnas.
  const isRegisterFormView = authView === authViews.register && !pendingRegistration && !verifiedUser;

  return (
    <div className="app-shell auth-shell">
      <div className="ambient-light ambient-light-left" />
      <div className="ambient-light ambient-light-right" />

      <main
        className={`auth-experience ${isEntryView ? 'is-entry' : 'is-auth-active'}${
          isRegisterFormView ? ' is-register' : ''
        }`}
      >
        <section className={`auth-hero-panel ${isAuthPanelActive ? 'is-muted' : ''}`}>
          <div className="auth-hero-content">
            <p className="eyebrow">{brand}</p>
            <h1 className="hero-title">{esTexts.auth.entry.title}</h1>
            <p className="hero-description">{esTexts.auth.entry.subtitle}</p>

            <div className="hero-actions">
              <button
                type="button"
                className="primary-button hero-button"
                onClick={() => handleSwitchAuthView(authViews.login)}
              >
                {esTexts.auth.entry.buttons.login}
              </button>
              <button
                type="button"
                className="secondary-button hero-button"
                onClick={() => handleSwitchAuthView(authViews.register)}
              >
                {esTexts.auth.entry.buttons.register}
              </button>
            </div>
          </div>
        </section>

        <section
          className={`auth-card auth-flow-card ${isAuthPanelActive ? 'is-visible' : 'is-hidden'}`}
          aria-hidden={!isAuthPanelActive}
        >
          {isAuthPanelActive && (
            <>
              {showBackButton && (
                <button
                  type="button"
                  className="text-link back-link"
                  onClick={() => handleSwitchAuthView(authViews.entry)}
                >
                  {esTexts.auth.entry.backToHome}
                </button>
              )}

              <header className="hero-copy">
                <p className="eyebrow">{brand}</p>
                <h2 className="centered-title">{currentTitle}</h2>
                {currentSubtitle && <p className="subtitle">{currentSubtitle}</p>}
              </header>

              {authView === authViews.register && !pendingRegistration && (
                <RegisterForm
                  texts={registerTexts}
                  onRegistrationSuccess={handleRegistrationSuccess}
                  onSwitchToLogin={() => handleSwitchAuthView(authViews.login)}
                />
              )}

              {authView === authViews.register && pendingRegistration && !verifiedUser && (
                <VerificationForm
                  email={pendingRegistration.email}
                  codePolicy={pendingRegistration.codePolicy}
                  texts={registerTexts}
                  errorTexts={esTexts.auth.errors}
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
                <LoginForm
                  texts={loginTexts}
                  errorTexts={esTexts.auth.errors}
                  notice={loginNotice}
                  onLoginSuccess={handleLoginSuccess}
                  onVerifyAccount={handleVerifyAccount}
                  onForgotPassword={() => handleSwitchAuthView(authViews.forgotPassword)}
                  onSwitchToRegister={() => handleSwitchAuthView(authViews.register)}
                />
              )}

              {isPasswordResetView && !passwordReset && (
                <ForgotPasswordForm texts={passwordResetTexts} onCodeRequested={setPasswordReset} />
              )}

              {isPasswordResetView && passwordReset && !isPasswordResetDone && (
                <PasswordResetForm
                  email={passwordReset.email}
                  codePolicy={passwordReset.codePolicy}
                  texts={passwordResetTexts}
                  errorTexts={esTexts.auth.errors}
                  onCodeVerified={() => setIsResetCodeVerified(true)}
                  onRestart={handleRestartPasswordReset}
                  onResetSuccess={() => setIsPasswordResetDone(true)}
                />
              )}

              {isPasswordResetView && isPasswordResetDone && (
                <section className="success-panel" aria-live="polite">
                  <div className="success-badge">{passwordResetTexts.success.badge}</div>
                  <h2>{passwordResetTexts.success.title}</h2>
                  <p>{passwordResetTexts.success.text}</p>
                  <button type="button" onClick={() => handleSwitchAuthView(authViews.login)}>
                    {passwordResetTexts.buttons.goToLogin}
                  </button>
                </section>
              )}
            </>
          )}
        </section>
      </main>
    </div>
  );
}

export default App;

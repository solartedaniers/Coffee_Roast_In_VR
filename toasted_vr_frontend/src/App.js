import React, { useState } from 'react';
import './App.css';
import esTexts from './locals/es.json';
import RegisterForm from './components/RegisterForm';
import VerificationForm from './components/VerificationForm';

function App() {
  const texts = esTexts.registration;
  const [pendingRegistration, setPendingRegistration] = useState(null);
  const [verifiedUser, setVerifiedUser] = useState(null);

  const handleRegistrationSuccess = (registrationData) => {
    setPendingRegistration(registrationData);
    setVerifiedUser(null);
  };

  const handleVerificationSuccess = (user) => {
    setVerifiedUser(user);
  };

  const handleRestart = () => {
    setPendingRegistration(null);
    setVerifiedUser(null);
  };

  return (
    <div className="app-shell">
      <div className="ambient-light ambient-light-left" />
      <div className="ambient-light ambient-light-right" />

      <main className="auth-card">
        <header className="hero-copy">
          <p className="eyebrow">{texts.brand}</p>
          <h1>{texts.title}</h1>
          <p className="subtitle">
            {verifiedUser
              ? texts.success.subtitle
              : pendingRegistration
                ? texts.verification.subtitle
                : texts.subtitle}
          </p>
        </header>

        {!pendingRegistration && (
          <RegisterForm texts={texts} onRegistrationSuccess={handleRegistrationSuccess} />
        )}

        {pendingRegistration && !verifiedUser && (
          <VerificationForm
            email={pendingRegistration.email}
            expiresInMinutes={pendingRegistration.expiresInMinutes}
            texts={texts}
            onVerificationSuccess={handleVerificationSuccess}
          />
        )}

        {verifiedUser && (
          <section className="success-panel" aria-live="polite">
            <div className="success-badge">{texts.success.badge}</div>
            <h2>{texts.success.title}</h2>
            <p>{texts.success.accountCreated}</p>
            <p className="success-email">{verifiedUser.email}</p>
            <button type="button" onClick={handleRestart}>
              {texts.buttons.createAnother}
            </button>
          </section>
        )}
      </main>
    </div>
  );
}

export default App;

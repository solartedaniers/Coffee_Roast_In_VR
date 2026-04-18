import React, { useRef } from 'react';

function VerificationCodeInput({ codeDigits, onChange }) {
  const inputRefs = useRef([]);

  const handleDigitChange = (index, value) => {
    const sanitizedValue = value.replace(/\D/g, '').slice(-1);
    onChange(index, sanitizedValue);

    if (sanitizedValue && index < codeDigits.length - 1) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (index, event) => {
    if (event.key === 'Backspace' && !codeDigits[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  const handlePaste = (event) => {
    event.preventDefault();
    const pastedDigits = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, codeDigits.length);

    pastedDigits.split('').forEach((digit, index) => {
      onChange(index, digit);
    });

    const focusIndex = Math.min(pastedDigits.length, codeDigits.length - 1);
    inputRefs.current[focusIndex]?.focus();
  };

  return (
    <div className="code-grid" onPaste={handlePaste}>
      {codeDigits.map((digit, index) => (
        <input
          key={`digit-${index}`}
          ref={(element) => {
            inputRefs.current[index] = element;
          }}
          className="digit-box"
          type="text"
          inputMode="numeric"
          maxLength="1"
          value={digit}
          onChange={(event) => handleDigitChange(index, event.target.value)}
          onKeyDown={(event) => handleKeyDown(index, event)}
          aria-label={`Digito ${index + 1} del codigo`}
        />
      ))}
    </div>
  );
}

export default VerificationCodeInput;

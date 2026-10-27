import React from 'react';

// Mensaje de error que se muestra justo debajo del campo al que pertenece.
function FieldError({ message }) {
  if (!message) {
    return null;
  }

  return (
    <span className="field-error" role="alert">
      {message}
    </span>
  );
}

export default FieldError;

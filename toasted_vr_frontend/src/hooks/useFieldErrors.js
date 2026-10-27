import { useCallback, useState } from 'react';

// Errores por campo de un formulario: se asignan en bloque (validación local
// o details.fieldErrors del backend) y cada uno se borra cuando el usuario
// corrige su campo.
export function useFieldErrors() {
  const [fieldErrors, setFieldErrors] = useState({});

  const clearFieldError = useCallback((fieldName) => {
    setFieldErrors((current) => {
      if (!current[fieldName]) {
        return current;
      }

      const { [fieldName]: _removed, ...rest } = current;
      return rest;
    });
  }, []);

  const clearAllFieldErrors = useCallback(() => setFieldErrors({}), []);

  return { fieldErrors, setFieldErrors, clearFieldError, clearAllFieldErrors };
}

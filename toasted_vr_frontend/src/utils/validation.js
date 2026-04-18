export const validateRegistrationForm = (formData, texts) => {
  if (formData.password !== formData.confirmPassword) {
    return texts.messages.passwordMismatch;
  }

  return '';
};

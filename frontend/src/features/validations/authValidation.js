export const validateEntrustToken = (value) => {
  const token = value.trim();
  if (!token) return "Enter your Entrust token.";
  if (!/^\d{6,10}$/.test(token)) return "Entrust token must contain between 6 and 10 digits.";
  return "";
};

export const formatDate = (value, withTime = false) => {
  if (!value) return '—';
  const options = withTime
    ? { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }
    : { day: '2-digit', month: 'short', year: 'numeric' };
  return new Intl.DateTimeFormat('en-NG', options).format(new Date(value));
};

export const maskAccount = (value = '') => {
  const account = String(value ?? '');
  if (account.includes('*') || account.includes('•')) return account;
  return account.length === 10 ? `${account.slice(0, 3)}****${account.slice(-3)}` : '**********';
};

export const formatBranch = (name, code, fallback = '—') => {
  if (name && code) return `${name} - ${code}`;
  return name || code || fallback || '—';
};

export const initials = (name = '') =>
  name.split(' ').filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase();

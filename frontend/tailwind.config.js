/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#121B42',
        brand: {
          50: '#EFFAFF',
          100: '#D9F3FE',
          500: '#08A8E8',
          600: '#008AC6',
          700: '#006D9F',
        },
      },
      boxShadow: {
        panel: '0 1px 2px rgba(18,27,66,.05), 0 8px 24px rgba(18,27,66,.06)',
      },
      fontFamily: {
        sans: ['Inter', 'Aptos', 'Segoe UI', 'Helvetica Neue', 'sans-serif'],
      },
    },
  },
  plugins: [],
};

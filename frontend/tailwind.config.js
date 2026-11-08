/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#eef7ff",
          100: "#d9edff",
          500: "#0d6efd",
          600: "#0b5ed7",
          700: "#0a4cad",
        },
      },
    },
  },
  plugins: [],
};

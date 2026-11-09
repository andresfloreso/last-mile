/** @type {import('tailwindcss').Config} */
const band = (name) => ({
  DEFAULT: `var(--band-${name})`,
  bg: `var(--band-${name}-bg)`,
  ink: `var(--band-${name}-text)`,
});

export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  // HU-27: el tema se aplica con la clase "dark" en <html> (store de tema).
  darkMode: "class",
  theme: {
    extend: {
      // Tokens de src/index.css: los componentes no usan colores sueltos.
      colors: {
        page: "var(--color-page)",
        panel: "var(--color-panel)",
        tile: "var(--color-tile)",
        float: "var(--color-float)",
        line: "var(--color-line)",
        border: "var(--color-border)",
        ink: "var(--color-text)",
        muted: "var(--color-muted)",
        primary: { DEFAULT: "var(--color-primary)", ink: "var(--color-on-primary)" },
        reject: "var(--color-reject)",
        modband: "var(--color-modband)",
        warning: { bg: "var(--color-warning-bg)", ink: "var(--color-warning-ink)" },
        "badge-ink": "var(--color-badge-ink)",
        band: {
          morning: band("morning"),
          midday: band("midday"),
          afternoon: band("afternoon"),
          night: band("night"),
        },
      },
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "sans-serif"],
      },
      // Escala tipográfica: 12 / 13 / 14 / 15 / 20 / 22 px.
      fontSize: {
        xs: ["12px", "16px"],
        13: ["13px", "18px"],
        sm: ["14px", "20px"],
        15: ["15px", "22px"],
        xl: ["20px", "28px"],
        22: ["22px", "28px"],
      },
      borderRadius: {
        DEFAULT: "8px",
        md: "8px",
        lg: "10px",
        xl: "12px",
      },
    },
  },
  plugins: [],
};

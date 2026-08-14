/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Inter var"', "Inter", "system-ui", "sans-serif"],
      },
    },
  },
  // Lightswind ships a Tailwind v3 plugin carrying the design tokens its
  // components reference (see DECISIONS.md for why the project is on v3).
  plugins: [require("lightswind/plugin")],
};

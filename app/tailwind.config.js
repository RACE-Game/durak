/** @type {import('tailwindcss').Config} */
module.exports = {
  mode: 'jit',
  content: {
    files: ['./src/durak/**/*.cljs'],
  },
  theme: {
    extend: {},
  },
  plugins: [require("daisyui")],
  daisyui: {
    themes: ["lemonade"],
  },
}

import { defineConfig } from 'vite'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] }),
    tailwindcss(),
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/firebase')) {
            return 'firebase-vendor'
          }
          if (
            id.includes('node_modules/react')
            || id.includes('node_modules/react-dom')
            || id.includes('node_modules/scheduler')
          ) {
            return 'react-vendor'
          }
          return undefined
        },
      },
    },
  },
})

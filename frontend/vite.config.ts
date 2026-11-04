import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

function versionPlugin(): Plugin {
  return {
    name: 'version-json',
    generateBundle() {
      const commit = process.env.RENDER_GIT_COMMIT || process.env.VITE_GIT_COMMIT || process.env.GITHUB_SHA || 'development'
      const branch = process.env.RENDER_GIT_BRANCH || process.env.VITE_GIT_BRANCH || process.env.GITHUB_REF_NAME || 'main'
      this.emitFile({
        type: 'asset',
        fileName: 'version.json',
        source: JSON.stringify({
          status: 'UP',
          commit,
          branch,
          buildTime: new Date().toISOString(),
        }, null, 2),
      })
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), versionPlugin()],
})

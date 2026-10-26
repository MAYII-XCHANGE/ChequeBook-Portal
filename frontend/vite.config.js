import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig(() => {
  const base = "/chequebookrequest/";

  return {
    plugins: [react(), tailwindcss()],
    base,
    server: {
      port: 5173, // change here back to 8080 for test environment
      // proxy: {
      //   "/api": {
      //     target: "http://10.0.240.73:8083",
      //     changeOrigin: true,
      //   },
      // },
    },
    build: {
      outDir: "dist",
      emptyOutDir: true,
    },
    test: {
      environment: "jsdom",
      setupFiles: "./src/test/setup.js",
    },
  };
});

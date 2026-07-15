import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";
import path from "node:path";

export default defineConfig(({ mode }) => {
  const rawEnv = loadEnv(mode, process.cwd(), "");
  const appEnv = rawEnv.VITE_APP_ENV || mode;
  const apiTarget = rawEnv.VITE_API_TARGET || "http://localhost:8080";

  return {
    plugins: [react()],
    define: {
      __APP_ENV__: JSON.stringify({
        APP_ENV: appEnv,
        DEV: appEnv === "local" || mode === "development",
        MODE: mode,
        API_TARGET: apiTarget,
      }),
    },
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "src"),
      },
    },
    server: {
      port: 3000,
      strictPort: true,
      proxy: {
        "/api": {
          target: apiTarget,
          changeOrigin: true,
        },
      },
    },
    preview: {
      port: 3000,
      strictPort: true,
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            react: ["react", "react-dom", "react-router-dom"],
            antd: ["antd", "@ant-design/icons"],
            charts: ["echarts", "@xyflow/react"],
            query: ["axios", "@tanstack/react-query"],
          },
        },
      },
    },
  };
});

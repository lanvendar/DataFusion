interface AppEnv {
  DEV: boolean;
  MODE: string;
  API_TARGET: string;
}

declare const __APP_ENV__: AppEnv;

export const env = __APP_ENV__;

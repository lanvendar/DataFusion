interface AppEnv {
  APP_ENV: string;
  DEV: boolean;
  MODE: string;
  API_TARGET: string;
}

declare const __APP_ENV__: AppEnv;

export const env = __APP_ENV__;

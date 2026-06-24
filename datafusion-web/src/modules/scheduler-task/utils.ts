export function formatJsonText(value?: unknown, space = 2) {
  if (value == null || value === "") return "";

  if (typeof value === "string") {
    const trimmedValue = value.trim();
    if (!trimmedValue) return "";
    try {
      return JSON.stringify(JSON.parse(trimmedValue), null, space);
    } catch {
      return value;
    }
  }

  try {
    return JSON.stringify(value, null, space);
  } catch {
    return "";
  }
}

export function compressJsonText(value?: string) {
  const trimmedValue = value?.trim();
  if (!trimmedValue) return "";
  return JSON.stringify(JSON.parse(trimmedValue));
}

export function normalizeJsonText(value: string | undefined, fieldLabel: string) {
  const trimmedValue = value?.trim();
  if (!trimmedValue) return undefined;

  try {
    return JSON.stringify(JSON.parse(trimmedValue));
  } catch {
    throw new Error(`${fieldLabel}不是合法的 JSON`);
  }
}

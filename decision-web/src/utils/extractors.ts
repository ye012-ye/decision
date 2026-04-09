export function extractOrderNo(input: string) {
  return input.match(/WO\d{11}/)?.[0] ?? '';
}

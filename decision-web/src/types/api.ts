export interface ResultEnvelope<T> {
  code: number;
  msg: string;
  data: T;
}

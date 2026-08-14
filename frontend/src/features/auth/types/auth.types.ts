export interface User {
  userId: number;
  email: string;
  name: string;
}

export interface LoginPayload {
  email: string;
  name: string;
}

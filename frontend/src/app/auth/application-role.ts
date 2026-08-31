export enum ApplicationRole {
  USER = 'USER',
  ADMIN = 'ADMIN'
}

export function isApplicationRole(value: unknown): value is ApplicationRole {
  return Object.values(ApplicationRole).some((role) => role === value);
}

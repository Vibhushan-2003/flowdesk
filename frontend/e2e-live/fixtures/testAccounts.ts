export interface TestAccount {
  email: string
  password: string
  firstName: string
  lastName: string
  roles: string[]
}

// Credentials are injected via environment variables in isolated E2E environments.
// Fallback values are strictly test-only and never used in production.
const adminPassword =
  process.env.FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD ||
  process.env.E2E_ADMIN_PASSWORD ||
  'FlowDeskE2E_AdminSecurePass123!'

const testUserPassword =
  process.env.E2E_USER_PASSWORD ||
  'FlowDeskE2E_UserSecurePass123!'

export const ADMIN_BOOTSTRAP_ACCOUNT: TestAccount = {
  email: process.env.FLOWDESK_BOOTSTRAP_ADMIN_EMAIL || 'admin@flowdesk.test',
  password: adminPassword,
  firstName: 'System',
  lastName: 'Administrator',
  roles: ['ADMIN'],
}

export const E2E_EMPLOYEE_A: TestAccount = {
  email: 'employee.e2e@flowdesk.test',
  password: process.env.E2E_EMPLOYEE_A_PASSWORD || testUserPassword,
  firstName: 'Alice',
  lastName: 'Employee',
  roles: ['EMPLOYEE'],
}

export const E2E_EMPLOYEE_B: TestAccount = {
  email: 'employee2.e2e@flowdesk.test',
  password: process.env.E2E_EMPLOYEE_B_PASSWORD || testUserPassword,
  firstName: 'Bob',
  lastName: 'Isolation',
  roles: ['EMPLOYEE'],
}

export const E2E_SUPPORT_ENGINEER: TestAccount = {
  email: 'support.e2e@flowdesk.test',
  password: process.env.E2E_SUPPORT_PASSWORD || testUserPassword,
  firstName: 'Sam',
  lastName: 'Support',
  roles: ['SUPPORT_ENGINEER'],
}

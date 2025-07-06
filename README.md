# Scenario Matrix for User Role Management

This matrix describes the logic for handling sign-up or request attempts in the Rider and Driver apps, based on the user's current status.

| **Current Status** | **Rider App Request**                    | **Driver App Request**                    |
|--------------------|------------------------------------------|------------------------------------------|
| **New User**       | ✅ Create User + Rider                   | ✅ Create User + Driver                   |
| **Rider Only**     | ❌ Block: "Login instead"                | ✅ Upgrade: Add Driver role              |
| **Driver Only**    | ✅ Upgrade: Add Rider role              | ❌ Block: "Login instead"                |
| **Both Roles**     | ❌ Block: "Login instead"                | ❌ Block: "Login instead"                |

## Explanation

- **New User**:  
  - Rider app: Creates a new user account with Rider role.  
  - Driver app: Creates a new user account with Driver role.

- **Rider Only**:  
  - Rider app: Blocks sign-up; prompts to log in instead.  
  - Driver app: Allows upgrading to add Driver role to the same user.

- **Driver Only**:  
  - Rider app: Allows upgrading to add Rider role to the same user.  
  - Driver app: Blocks sign-up; prompts to log in instead.

- **Both Roles**:  
  - Blocks sign-up in both apps; prompts to log in instead.

---

✅ = Allowed  
❌ = Blocked with "Login instead" message


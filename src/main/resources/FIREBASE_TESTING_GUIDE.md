FIREBASE_TESTING_GUIDE.md
# Firebase Phone Auth Testing Guide

## 📱 Client-Side Integration Required

With Firebase Phone Auth, the client (mobile app/web app) handles OTP verification:

### Frontend Flow:
1. **Client sends phone number to Firebase**
2. **User receives SMS OTP**
3. **User enters OTP in client app**
4. **Firebase verifies OTP and returns ID token**
5. **Client sends ID token to our backend**
6. **Backend verifies ID token and registers user**

## 🧪 Testing with Postman

### Step 1: Get Firebase ID Token (Frontend Required)

Since Firebase Phone Auth requires client-side verification, you have two options:

#### Option A: Use Firebase REST API (For Testing)
```http
POST https://identitytoolkit.googleapis.com/v1/accounts:sendVerificationCode?key=YOUR_WEB_API_KEY
Content-Type: application/json

{
  "phoneNumber": "+94718865022",
  "recaptchaToken": "test-recaptcha-token"
}
```

Then verify:
```http
POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithPhoneNumber?key=YOUR_WEB_API_KEY
Content-Type: application/json

{
  "sessionInfo": "SESSION_INFO_FROM_PREVIOUS_RESPONSE",
  "code": "123456"
}
```

#### Option B: Mock Firebase Token (Development)
For testing, you can create a mock endpoint or use Firebase test phone numbers.

### Step 2: Register Rider with Firebase Token
```http
POST {{baseUrl}}/api/v1/riders/register
Content-Type: application/json

{
  "firebaseIdToken": "FIREBASE_ID_TOKEN_FROM_STEP_1",
  "firstName": "John",
  "lastName": "Rider",
  "dateOfBirth": "1990-01-01",
  "emergencyContactName": "Jane Doe",
  "emergencyContactPhone": "+94712345678"
}
```

### Step 3: Register Driver with Firebase Token
```http
POST {{baseUrl}}/api/v1/drivers/register
Content-Type: application/json

{
  "firebaseIdToken": "FIREBASE_ID_TOKEN_FROM_STEP_1",
  "firstName": "Mike",
  "lastName": "Driver",
  "dateOfBirth": "1985-05-15",
  "whatsappNumber": "+94720129297",
  "emergencyContactName": "Sarah Driver",
  "emergencyContactPhone": "+94712345679"
}
```

## 🛠️ Development Testing

For development, add these test phone numbers in Firebase Console:
- Test Phone: `+1 650-555-3434`
- Test Code: `123456`

This allows testing without real SMS.
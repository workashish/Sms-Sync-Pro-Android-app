# SMS Sync Pro

SMS Sync Pro is a powerful and flexible Android application designed to capture incoming SMS messages and instantly forward them to predefined targets, such as other phone numbers or external webhooks. This allows you to build custom integrations, sync SMS to a laptop/desktop dashboard, or forward critical alerts (like OTPs or bank messages) automatically.

## 🚀 Features

*   **Multi-Channel Forwarding:** Forward incoming SMS messages to another phone number via SMS, or to any web server via an HTTP POST request.
*   **Keyword Filtering:** Set up rules to only forward messages that contain specific keywords (e.g., "OTP", "Alert", "Bank"). Leave it blank to forward all messages.
*   **Live Logging:** View a detailed history of processed messages, the rule triggered, and the success/failure status of the forward action natively within the app.
*   **Offline Queueing (No lost messages):** If an SMS arrives while your phone has no internet (Wi-Fi or Mobile Data), the app securely queues the webhook payload. The exact moment your phone regains connectivity, all queued messages are automatically sent using Android's robust `WorkManager`.
*   **Rule Management:** Enable, disable, and manage multiple forwarding rules to handle complex routing (e.g., send OTPs to a webhook, and personal messages to another phone).
*   **Offline Capable Configuration:** Rules and logs are stored locally using a Room Database, ensuring that your configuration is always available.
*   **Modern UI:** Built with Jetpack Compose using Material 3 guidelines and a sleek "Professional Polish" theme.

## 📋 System Requirements

*   **OS Level:** Android 7.0 (API Level 24) or higher.
*   **Permissions:** The application heavily relies on Android Telephony and Networking permissions:
    *   `RECEIVE_SMS`: Required to intercept incoming text messages in real-time.
    *   `READ_SMS`: Required to read the content of the messages.
    *   `SEND_SMS`: Required if you configure rules to forward messages via standard SMS to another number.
    *   `INTERNET`: Required to send POST requests to Webhook URLs.

> **Note:** Upon the first launch, the app will request these permissions. You must grant them for the core functionality to work.

## 🌐 Webhook Integration Guide

If you choose the **Webhook Target** as a rule type, SMS Sync Pro will execute an HTTP `POST` request to the specific URL you provide whenever a matching SMS is received.

### JSON Payload Syntax

The HTTP POST request contains a JSON body with the following structure:

```json
{
  "sender": "+1234567890",
  "message": "Your OTP code is 948123. Do not share this with anyone.",
  "device_model": "SM-S928B",
  "timestamp": 1718042456000
}
```

*   `sender` (String): The phone number or sender ID (like "BankX") that sent the original SMS.
*   `message` (String): The complete body of the text message.
*   `device_model` (String): The Android hardware model (e.g., `SM-S928B` for S24 Ultra). This is incredibly useful if you have installed SMS Sync Pro on **multiple Android smartphones** and are forwarding them all to the same single Webhook URL. It allows you to natively identify which physical device intercepted the SMS.
*   `timestamp` (Long): The UNIX epoch timestamp (in milliseconds) representing when the device forwarded the message.

### Webhook Endpoint / Website Requirements

If you are planning to build a custom website or endpoint to receive these webhooks, here is an exhaustive checklist of things you must implement to ensure reliability and security:

1.  **Publicly Accessible Endpoint**: Your server must be accessible from the Android device's network. If the phone is on mobile data, the server needs a public IP or domain (e.g., hosted on Vercel, Heroku, AWS, DigitalOcean).
2.  **HTTPS (SSL/TLS) Required for Production**: You **MUST** use `https://` (SSL) for your webhook URL. Do not use `http://` in production.
3.  **Accept HTTP POST Requests**: The endpoint must listen for `POST` requests, not `GET`.
4.  **Parse `application/json` Content-Type**: The payload is sent as stringified JSON (unless overridden by a Custom Template). Your server framework (Express, Flask, Laravel, etc.) must be configured to parse JSON bodies.
5.  **Fast Response Times (Under 3 seconds)**: Your endpoint should quickly respond with an HTTP status code between `200` and `299`. Do heavy lifting (like sending emails) asynchronously.
6.  **Idempotency & Duplicate Handling**: Because the app uses Android `WorkManager` (which guarantees delivery and handles retries automatically), your server should handle duplicate data gracefully. Use `timestamp` and `message` combinations to ignore duplicates.
7.  **HMAC Signature Verification (CRITICAL)**: If you set a **Webhook Secret** in the app:
    *   The app sends an `X-Signature` HTTP header containing an HMAC-SHA256 signature.
    *   Grab the raw, unmodified request body on your server.
    *   Compute an HMAC-SHA256 hash using your secret key.
    *   Compare the hash — if they don't match, reject the request with a `401`.
6.  **AES-256 Decryption (CRITICAL)**: If you configure an **AES Encryption Key** in the app:
    *   The `message` field in the JSON payload will *no longer* be plain text. Instead, it will be a string containing the Salt, IV, and the Ciphertext separated by colons (`:`).
    *   The string format is: `saltHex:ivHex:ciphertextHex`.
    *   Your server must split the string by `:`.
    *   Convert the hex strings to byte arrays.
    *   Use PBKDF2 with HMAC-SHA256 (10000 iterations, 256-bit key) with the salt and your AES password to generate the secret key.
    *   Decrypt the ciphertext using `AES-256-GCM` algorithm (NoPadding) with the derived key and IV.

### Download

The compiled APK can be found in the `/apk` directory of this repository (`/apk/sms-sync-pro.apk`).

### Node.js Example (Server-Side)
Here is a complete, production-ready Express server that handles HMAC verification AND AES-256-GCM decryption:

```javascript
const express = require('express');
const crypto = require('crypto');
const app = express();

const HMAC_SECRET = "YOUR_HMAC_SECRET_KEY"; // Must match "Webhook Secret Key (HMAC)" in app
const AES_PASSWORD = "YOUR_AES_PASSWORD";   // Must match "AES Encryption Key" in app

// Store raw body buffer for HMAC
app.use(express.json({ verify: (req, res, buf) => { req.rawBody = buf; } }));

// AES-256-GCM Decryption Helper
function decryptMessage(encryptedPayload) {
    if (!AES_PASSWORD) return encryptedPayload;
    
    try {
        const parts = encryptedPayload.split(':');
        if (parts.length !== 3) return "INVALID_ENCRYPTION_FORMAT";
        
        const salt = Buffer.from(parts[0], 'hex');
        const iv = Buffer.from(parts[1], 'hex');
        const ciphertext = Buffer.from(parts[2], 'hex');
        
        const key = crypto.pbkdf2Sync(AES_PASSWORD, salt, 10000, 32, 'sha256');
        
        // In Node.js, the Auth Tag is the last 16 bytes of the ciphertext for GCM
        const authTagLength = 16;
        const actualCiphertext = ciphertext.subarray(0, ciphertext.length - authTagLength);
        const authTag = ciphertext.subarray(ciphertext.length - authTagLength);
        
        const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
        decipher.setAuthTag(authTag);
        
        let decrypted = decipher.update(actualCiphertext, undefined, 'utf8');
        decrypted += decipher.final('utf8');
        return decrypted;
    } catch (e) {
        console.error("AES Decryption failed", e);
        return "DECRYPTION_FAILED";
    }
}

app.post('/sms-webhook', (req, res) => {
    // 1. Verify HMAC Signature
    if (HMAC_SECRET) {
        const signature = req.headers['x-signature'];
        const expectedSignature = crypto.createHmac('sha256', HMAC_SECRET)
                                        .update(req.rawBody)
                                        .digest('hex');
                                        
        if (signature !== expectedSignature) {
           return res.status(401).send('Unauthorized - Signature Mismatch');
        }
    }

    // 2. Extract Data
    const { sender, message, timestamp, device_model } = req.body;
    
    // 3. Decrypt Message (if AES is enabled)
    const plainTextMessage = decryptMessage(message);

    // 4. Process Logic
    console.log(`[${device_model}] SMS from ${sender}: ${plainTextMessage}`);
    
    // 5. Respond quickly!
    res.status(200).json({ status: "success" });
});

app.listen(8080, () => console.log('Listening on port 8080'));
```

## 🏗️ Future Considerations: Building a Web Dashboard

If you plan to build a complete website (Front-end + Back-end) to view these incoming SMS messages, keep these architectural requirements in mind:

### 1. Database Choice & Schema
*   **Database**: Use MongoDB (NoSQL) or PostgreSQL (SQL). MongoDB is great for flexible JSON payloads.
*   **Schema Fields**: Store `_id`, `sender_number`, `message_body`, `device_model`, `received_timestamp`, `processed_at`, and `status`.
*   **Indexes**: Create database indexes on `sender_number` and `received_timestamp` so your front-end can quickly load historical messages and filter by sender.

### 2. Front-End Interface (React / Vue / Next.js)
*   **Real-time Updates**: Since SMS Webhooks arrive asynchronously, consider implementing **WebSockets** (e.g., `Socket.io`) on your server. When your webhook endpoint receives a message, it can push an event to the Front-End, allowing the dashboard UI to update instantly without the user needing to refresh the page.
*   **CORS Configuration**: Your back-end server must implement CORS (Cross-Origin Resource Sharing) policies allowing your Front-End domain to fetch the SMS data securely. *Note: Webhooks sent directly from the Android App do NOT require CORS, this is only for your Web Dashboard.*
*   **Authentication**: Secure your Web Dashboard using JWT (JSON Web Tokens) or NextAuth to ensure nobody else can casually browse your incoming SMS messages.

### 3. Queueing & Rate Limiting
*   If your phone forwards hundreds of messages at once, your webhook server might get overwhelmed. Use a Message Queue like **Redis** or **RabbitMQ** to temporarily store incoming webhooks before writing them sequentially to your database.
*   Set up Rate Limiting on your server (e.g., `express-rate-limit`) to prevent DDoS attacks against your public webhook URL. Wait to rate-limit slightly higher than your expected max SMS flow.

---

## 📱 User Guide

### 1. Initial Setup & Background Reliability
1. Launch the app.
2. Click **Grant Permissions** and accept the requested SMS privileges. 
3. If prompted, click **Disable Battery Optimization**. This is *critical* for modern devices (like Samsung Galaxy S24 Ultra) to prevent the OS from killing the app in the background.
4. You will see an ongoing notification indicating that SMS Sync Pro is actively running in the background.
5. You will land on the **Rules** dashboard.

### 2. Creating a Forwarding Rule
1. Tap the Floating Action Button `(+)` at the bottom right.
2. **Rule Name**: Give your rule a recognizable name (e.g., "Bank OTPs to Laptop").
3. **Target Type**: Select either `SMS Target` or `Webhook Target`.
4. **Target Destination**:
    *   If SMS: Enter the full phone number (e.g., `+15551234567`).
    *   If Webhook: Enter the full HTTP/HTTPS URL (e.g., `https://my-server.com/api/sms`).
5. **Keyword Filter (Optional)**: If you only want this rule to trigger for certain messages, enter a keyword (e.g., `OTP` or `Netflix`). If left blank, it will forward *all* incoming messages.
6. Tap **Add Rule**.

### 3. Monitoring Logs
1. Navigate to the **Logs** tab via the bottom navigation bar.
2. Here you can see a real-time history of received messages and whether the forwarding action was a "SUCCESS" or "FAILED".
3. To clear the history, tap **CLEAR LOGS** in the top right corner.

### 4. Advanced Settings
The newly added **Settings** tab gives you explicit control over how the app behaves globally:
*   **Enable App Forwarding**: A master toggle to instantly pause or resume all forwarding rules without having to delete them.
*   **Include Device Model**: Toggle whether to append the `device_model` tracking key in your Webhook JSON payloads.
*   **Retry Failed Webhooks**: If enabled, the app will automatically attempt up to 3 background retries (with a 1-second backoff) if the initial webhook HTTP request returns a failing status code or times out.
*   **Webhook Timeout**: You can explicitly specify the network strictness (in seconds). Increase this up to 10 seconds if your target server is slow or experiencing latency.

> 💡 **Persistence**: All settings (saved via `SharedPreferences`), rules, and logs (saved via Room `SQLite`) are stored securely on the device. **If you restart your phone**, everything will remain completely intact. The app includes a `BootReceiver` that automatically re-initializes the background forwarding service as soon as the phone turns on, without needing you to open the app!

### 5. Security Settings for Banking / OTPs
Because this app can be used to forward critical information (like bank OTPs or 2FA codes), extra security features are provided in the **Settings** menu:
*   **Prevent Screen Capture**: When enabled, the app will request the Android OS to block all screenshots and screen recordings while the app is open. It also hides the app's contents in your recent multitasking menu. This ensures that no other app or user can spy on your forwarding rules. *(Note: You must restart the app after enabling this).*
*   **Webhook Secret Key (HMAC)**: If you enter a secret key here, the app will generate a cryptographic `HMAC-SHA256` signature of the JSON payload and send it in the `X-Signature` HTTP header. Your server can use the same secret key to compute the hash and compare it. This guarantees that the webhook was sent by your phone and was not tampered with by an attacker.

### 6. Tools & Quality of Life Features
The Settings tab also includes a new **Tools & Extras** section:
*   **Send Test Webhook**: Automatically push a mock payload to all configured webhook endpoints. Crucial for verifying connectivity and HMAC signatures during server development.
*   **Request Battery Optimization Exemption**: Android's Doze mode may sleep apps running in the background. Clicking this ensures the SMS forwarder uses unrestricted battery to always intercept messages.
*   **Export / Import Config**: Easily backup your settings and rules to a `.json` file, or restore them onto a new device instantly.

### 7. Advanced Security & Integrations
*   **End-to-End Encryption (AES-256)**: Secure your payloads before they even leave your phone. If an AES key is provided, the SMS message body is encrypted with `AES/CBC/PKCS5Padding` and encoded in Base64 before being sent to the webhook. Even if the HTTP transmission or server is compromised, the raw SMS data remains protected.
*   **Custom Webhook Templates**: Send payloads directly to services like Discord, Slack, or custom APIs without writing a middleware server. By defining a template (e.g., `{"text": "{sender} sent: {message}"}`), you can arbitrarily transform the outbound JSON structure.
*   **SMS Command System**: Control your phone remotely via SMS. Turn this on, text `STATUS`, `LOCATION`, or `REBOOT` to your device's number.
    *   `STATUS`: Auto-replies with the current Battery Percentage and Cellular Network condition. Useful for monitoring headless devices.
    *   `LOCATION` & `REBOOT`: These commands are actively parsed but will currently return a "requires elevated permissions or root" message, as extending them requires explicit system-level integrations.


## ⚙️ How It Works

SMS Sync Pro operates entirely on your device via standard Android APIs, without routing your messages through any third-party infrastructure.

1.  **Background Listener**: The app registers a `BroadcastReceiver` to listen for the `android.provider.Telephony.SMS_RECEIVED` intent. This means the Android OS automatically notifies the app the moment an SMS arrives, even if the app is closed.
2.  **Foreground Service**: To prevent newer versions of Android from killing the background listener to save battery (Doze mode), the app runs a lightweight Foreground Service. This keeps the application process alive and guarantees high reliability.
3.  **Rule Evaluation**: When an SMS arrives, the app queries its local SQLite database (Room) for all active forwarding rules. It then checks if the message body contains the specified keywords.
4.  **Parallel Execution**: If multiple rules match, the forwarding actions (either sending a new SMS via `SmsManager` or firing an HTTP POST via `WorkManager`) are executed in parallel using Coroutines. This ensures one slow server doesn't delay other forwarded messages.
5.  **Guaranteed Delivery (WorkManager)**: For Webhooks, the request is handed off to Android's `WorkManager` with a strict `NetworkType.CONNECTED` constraint. If your phone momentarily loses 4G/5G connection right as the message arrives, or is offline (in a tunnel or on airplane mode), WorkManager queues the payload safely. It will automatically wait and fire the messages the exact second your phone reconnects to Wi-Fi or Mobile Data without you having to open the app.
6.  **Local Logging**: Every action's status is logged into the local Room database, providing an instant audit trail in the app's UI.

## 🛠 Architecture & Tech Stack

The application is built completely natively for Android using modern, robust tooling:

*   **Language:** Kotlin (100% Kotlin codebase)
*   **UI Toolkit:** Jetpack Compose (Kotlin declarative UI framework) utilizing Material Design 3 components.
*   **Local Database:** Android Room (an abstraction layer over SQLite) with Coroutine Flow integration for reactive UI updates (e.g., the UI updates instantly when a new log is added in the background).
*   **Background Jobs:** `WorkManager` for guaranteed, deferrable execution of webhooks. It handles exponential backoff and network-retry constraints natively.
*   **Networking:** `HttpURLConnection` for lightweight webhook delivery without heavy external dependencies like Retrofit or Ktor, minimizing the APK size and ANR risks.
*   **Asynchronicity:** Kotlin Coroutines (`Dispatchers.IO`) for fast, non-blocking I/O operations (database reads, network calls).
*   **State Management:** ViewModels with `MutableStateFlow` to manage UI state cleanly and survive configuration changes.
*   **Security:** Cryptographic `HMAC-SHA256` generation using `javax.crypto.Mac` for webhook signature verification. Flags such as `WindowManager.LayoutParams.FLAG_SECURE` are used to prevent unauthorized screen captures.

## 🔒 Security & Privacy

*   **Direct Sync:** This application does not route your data through any third-party intermediate servers. Messages are sent *directly* from your phone to the target phone number or Webhook URL you configure.
*   **Cleartext Traffic:** The app allows `usesCleartextTraffic="true"` internally to allow you to test your webhook on local development servers (e.g., `http://192.168.1.100:8080`). For production, we strongly recommend deploying your server with HTTPS to encrypt the payloads in transit.
*   **Data Retention:** SMS logs are kept entirely locally on the device's Room database and can be wiped instantly from the Logs tab.

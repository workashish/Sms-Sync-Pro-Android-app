# SMS Sync Pro

SMS Sync Pro is a powerful and flexible Android application designed to capture incoming SMS messages and instantly forward them to predefined targets, such as other phone numbers or external webhooks. This allows you to build custom integrations, sync SMS to a laptop/desktop dashboard, or forward critical alerts (like OTPs or bank messages) automatically.

## 🚀 Features

*   **Multi-Channel Forwarding:** Forward incoming SMS messages to another phone number via SMS, or to any web server via an HTTP POST request.
*   **Keyword Filtering:** Set up rules to only forward messages that contain specific keywords (e.g., "OTP", "Alert", "Bank"). Leave it blank to forward all messages.
*   **Live Logging:** View a detailed history of processed messages, the rule triggered, and the success/failure status of the forward action natively within the app.
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
2.  **HTTPS (SSL/TLS) Required for Production**: You **MUST** use `https://` (SSL) for your webhook URL. Do not use `http://` in production. SMS data (especially bank OTPs) sent over plain HTTP can be intercepted by anyone on the network.
3.  **Accept HTTP POST Requests**: The endpoint must listen for `POST` requests, not `GET`.
4.  **Parse `application/json` Content-Type**: The payload is sent as stringified JSON. Your server framework (Express, Flask, Laravel, etc.) must be configured to parse JSON bodies.
5.  **Fast Response Times (Under 3 seconds)**: Your endpoint should quickly respond with an HTTP status code between `200` and `299` (e.g., `200 OK`). If your server does heavy processing (like sending emails or pushing to a database), return `200 OK` *immediately*, and then do the heavy lifting asynchronously. If your server takes too long (over the timeout set in the app settings, default 8 seconds), the app will mark it as "FAILED" and may attempt retries, leading to duplicate messages.
6.  **Idempotency & Duplicate Handling**: Because the app has a "Retry Failed Webhooks" feature (and network drops can cause identical requests), your server should handle duplicate data gracefully. You can use a combination of `sender`, `message`, and `timestamp` to detect and ignore exact duplicates.
7.  **HMAC Signature Verification (CRITICAL)**: Since you are dealing with financial OTPs, your webhook URL is essentially a public door. To prevent attackers from sending fake SMS data to your endpoint, you MUST:
    *   Set a complex string in the **Webhook Secret Key (HMAC)** setting in the Android app.
    *   On your server, grab the raw, unmodified request body.
    *   Compute an HMAC-SHA256 hash using the same secret key.
    *   Compare your computed hash with the hash sent in the `X-Signature` HTTP header.
    *   If they don't match, reject the request with `401 Unauthorized` or `403 Forbidden`.
8.  **WAF & Bot Protection Rules**: If you are using Cloudflare, AWS WAF, or other firewalls, ensure they do not block automated JSON POST requests. You may need to add a firewall rule to explicitly allow traffic to your webhook route.

### PHP Example (Server-Side)
```php
<?php
$data = json_decode(file_get_contents('php://input'), true);

if ($data) {
    $sender = $data['sender'];
    $message = $data['message'];
    $device = $data['device_model'];
    $time = date('Y-m-d H:i:s', $data['timestamp'] / 1000);
    
    // Verify HMAC Signature (if you configured a Webhook Secret in the app)
    $secret = "YOUR_APP_SECRET_KEY"; // Must match the secret in the app settings
    $signature = $_SERVER['HTTP_X_SIGNATURE'] ?? '';
    // To properly check HMAC, you need the raw input string exactly as sent
    $rawBody = file_get_contents('php://input');
    $expectedSignature = hash_hmac('sha256', $rawBody, $secret);

    // If using the secret key, uncomment the below lines to enforce security
    // if ($signature !== $expectedSignature) {
    //     error_log("Invalid Webhook Signature!");
    //     http_response_code(401);
    //     exit;
    // }

    // Save to database, push to websocket, or email via SMTP
    error_log("[$time] SMS from $sender on $device: $message");
    http_response_code(200);
} else {
    http_response_code(400);
}
?>
```

### Node.js Example (Server-Side)
```javascript
const express = require('express');
const app = express();
app.use(express.json({
    // Store the raw body buffer to properly verify HMAC signature
    verify: (req, res, buf) => { req.rawBody = buf; }
}));

app.post('/sms-webhook', (req, res) => {
    // Verify HMAC Signature (if you configured a Webhook Secret in the app)
    const secret = "YOUR_APP_SECRET_KEY"; // Must match the app settings
    const signature = req.headers['x-signature'];
    
    // If using the secret key, uncomment the below lines to enforce security
    // const crypto = require('crypto');
    // const expectedSignature = crypto.createHmac('sha256', secret).update(req.rawBody).digest('hex');
    // if (signature !== expectedSignature) {
    //    console.error("Invalid Webhook Signature!");
    //    return res.status(401).send('Unauthorized');
    // }

    const { sender, message, timestamp, device_model } = req.body;
    console.log(`[${device_model}] Received SMS from ${sender}: ${message}`);
    // Forward to Dashboard via Socket.io or save to MongoDB
    res.status(200).send('OK');
});

app.listen(8080, () => console.log('Listening on port 8080'));
```

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

## 🛠 Architecture & Tech Stack

*   **Language:** Kotlin
*   **UI Toolkit:** Jetpack Compose (Material Design 3)
*   **Local Storage:** Room Database (SQLite abstraction) for maintaining Rules and Activity Logs.
*   **Background Processing:** `BroadcastReceiver` listening for `android.provider.Telephony.SMS_RECEIVED`. Work is offloaded immediately to Coroutines (`Dispatchers.IO`) for fast parallel processing without blocking the main UI thread. Even when managing multiple webhooks at the same time, the messages are fired in parallel using Coroutine async jobs to prevent `BroadcastReceiver` ANR timeouts. A **Foreground Service** keeps the app process alive and prevents Doze mode on newer Android versions.
*   **Networking:** Standard `HttpURLConnection` with strict timeouts (`8000ms`) for lightweight webhook delivery without heavy external dependencies or ANR risks.

## 🔒 Security & Privacy

*   **Direct Sync:** This application does not route your data through any third-party intermediate servers. Messages are sent *directly* from your phone to the target phone number or Webhook URL you configure.
*   **Cleartext Traffic:** The app allows `usesCleartextTraffic="true"` internally to allow you to test your webhook on local development servers (e.g., `http://192.168.1.100:8080`). For production, we strongly recommend deploying your server with HTTPS to encrypt the payloads in transit.
*   **Data Retention:** SMS logs are kept entirely locally on the device's Room database and can be wiped instantly from the Logs tab.

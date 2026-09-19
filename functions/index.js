const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

/**
 * Called by the Android app once after setup (and again whenever FCM rotates
 * the token) to tell us which fcmToken belongs to which bot/chat pair.
 * Body: { botToken, chatId, fcmToken }
 */
exports.registerToken = onRequest(async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("Use POST");

  const { botToken, chatId, fcmToken } = req.body || {};
  if (!botToken || !chatId || !fcmToken) {
    return res.status(400).send("Missing botToken, chatId or fcmToken");
  }

  await db.collection("devices").doc(botToken).set({
    chatId: String(chatId),
    fcmToken,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  res.status(200).send("OK");
});

/**
 * Set this URL as the Telegram webhook for each customer's bot:
 *   https://api.telegram.org/bot<TOKEN>/setWebhook?url=<THIS_FUNCTION_URL>/telegramWebhook/<TOKEN>
 * The <TOKEN> in the path tells us which device record to look up in Firestore,
 * and also doubles as a basic check that the call is really for that bot.
 */
exports.telegramWebhook = onRequest(async (req, res) => {
  try {
    const botToken = req.path.replace(/^\/+/, ""); // token passed as the path segment
    const update = req.body;
    const message = update && update.message;
    if (!message || !message.text) return res.status(200).send("ignored");

    const chatId = String(message.chat.id);
    const text = message.text.trim().toLowerCase();

    const doc = await db.collection("devices").doc(botToken).get();
    if (!doc.exists) return res.status(200).send("unknown device");

    const device = doc.data();
    if (device.chatId !== chatId) return res.status(200).send("chat mismatch, ignored");

    if (text === "/locate") {
      await admin.messaging().send({
        token: device.fcmToken,
        data: { cmd: "locate" },
        android: { priority: "high" },
      });
    }
    // /status and anything else: no-op here: app's own service, if alive,
    // already answers directly via the Telegram Bot API when it wants to.

    res.status(200).send("OK");
  } catch (e) {
    console.error(e);
    res.status(500).send("error");
  }
});

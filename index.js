const express = require("express");
const admin = require("firebase-admin");
admin.initializeApp({
  credential: admin.credential.cert({
    type: "service_account",
    project_id: "kalua-9b39c",
    private_key_id: "7c6b8d3f56121ced3ae13c7ca2d7677b210ed8cd",
    private_key: "-----BEGIN PRIVATE KEY-----\nMIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDNmo1M7Cqnn/NU\nAhiC9P3lLT0745sP9WYqEFlfOh+pBf4u+pOU9/ErcrS64DJklAtkSiNE+0bVo6n6\nH5ePtvEnaRT/jYYzbMh1+rI5lXDI50lJZCeE2kj/kZmL5P+SJKhZ5FOPUb+smFLl\nJrJB7Hd0zDot3Q1eOethI+X0Jv4OfLfq0Kc0/6sLeu4IA3KfV3y7u6OFcQLPb5cr\nKJ4N7JlI1Che6y6Ofz/joug/8CywhOuAEQbBc9QfoPKwP2f3k/hQXPmQrQ4Q3NSZ\nhtMRljYKYBQwcPCL8KiLfkC+JYLt4/3sEwClpPzIrlJDOYIjZyzA3PvRACaZQOiO\nCy5uKP87AgMBAAECggEADIVH65JKOrED6W7DPV5cA9PQcdA3hi5EjXU3bBQnD2z9\nALFjwWOj/ShcFzFGAFf/pZjaMv42XxY7aK751NeoMeU1USa2MHWEc6LTrawLFUD4\ndo6x1WZRobalKh7E7Ypr8iV0bTrmOEWCbs//dQJIeAVj2Z0njkBqtByyiT7Co/Ea\ndF9Nhsrpt+M86C2CgjQ9G+6zPug4yUy32cVX6WXY8ujQPbf0N4Bq2ykeH+a06k2h\nf+FQfsxGkjP0CkJ+89NFRC8uH2Frq9MhwYSZjT+TduTh8lFkL+wnHWmjifMDMGah\nuQDaaRlQPWMVoz4CJRaMU9nThf53j0kK3tKzDNncUQKBgQDoBH+rDbRUlRKqTVpO\nKCyT3zkjDT8e/Lx09uryMvHpXmx2I0xMNnaiMlRkCUv7Y4OH8R4y7mEUO+M/VXl4\nE+U9eQYn9L5A3MBvCmjmyN3hTBuFW3UTJthB/MNEZWwdoH4y2uyYamvEp2dd5SAN\n9Ny7++GdED9B8MDrSzb5rFcXMwKBgQDi2xtNe3zTCyko4+D31uMcrdMvGHYovqeD\ndizVJ7HEjtjTyysc8LkyWBbjp4Yhdn7dxcecxd8+ArT+0n7GnYrz2yXSP46zsCo7b\naiWdpyHd6vRCbFfAUUC+uD9h+kS1U/OsLD8DFC1lHUeHwBINapl83f0DjR4wCy3w\nUAGQcrxX2QKBgEwzTgrL0XIGE79C8GnC4AEgyw8sBpMSxoJTpg4tlS3kCRMzvJc7\nO/NBPF4uf3ns0QIQuq5XhCK1GIlwdRhu0FdELDVrOCDtX3YYoSNKzTR1XoSJ6QWF\nOHlTnn4UIVIJp+jj5diY+xP3NwerfL+Yr/Y9X3fKIjxx6apdZYzSgTfpAoGAHHzz\ncsl9JWuTYaUsTTNZFfLmQFXMENXQZ36F1KCynI93Gr5IQtB1oalaTVSbMVarTL3H\nELlA/6HCDEa5opGNtVgbIS6wOnwg8IDl9GRWpm2o+uZsJqSNguXUX9Nz6Wl/gjGU\nQCi6gqYoo22FVtX6LGocKeQA1Huy6yjA6YjfChECgYB3fa+19mRROlJDs0kP94ll\nury6WfJQUDVi3WzlB/YYU/w6Dte4d+gRMvgSFJeQzBbHdjWFgPT5SexXRvKxHKGA\nJQutH7gZ/l8sygCi0QA0GOEzcmGaCuQIR0W8k0EZFYzKChD7792i34IexM5U/lDt\nRzykH7/7ds9LGf+amusTWA==\n-----END PRIVATE KEY-----\n",
    client_email: "firebase-adminsdk-fbsvc@kalua-9b39c.iam.gserviceaccount.com",
    client_id: "102149383059437288126",
    auth_uri: "https://accounts.google.com/o/oauth2/auth",
    token_uri: "https://oauth2.googleapis.com/token",
    auth_provider_x509_cert_url: "https://www.googleapis.com/oauth2/v1/certs",
    client_x509_cert_url: "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40kalua-9b39c.iam.gserviceaccount.com"
  }),
});
const db = admin.firestore();
const app = express();
app.use(express.json());
app.post("/registerToken", async (req, res) => {
  const { botToken, chatId, fcmToken } = req.body || {};
  if (!botToken || !chatId || !fcmToken) return res.status(400).send("Missing fields");
  await db.collection("devices").doc(botToken).set({ chatId: String(chatId), fcmToken, updatedAt: admin.firestore.FieldValue.serverTimestamp() });
  res.status(200).send("OK");
});
app.post("/telegramWebhook/:botToken", async (req, res) => {
  try {
    const botToken = req.params.botToken;
    const message = req.body && req.body.message;
    if (!message || !message.text) return res.status(200).send("ignored");
    const chatId = String(message.chat.id);
    const text = message.text.trim().toLowerCase();
    const doc = await db.collection("devices").doc(botToken).get();
    if (!doc.exists) return res.status(200).send("unknown device");
    const device = doc.data();
    if (device.chatId !== chatId) return res.status(200).send("chat mismatch");
    if (text === "/locate") {
      await admin.messaging().send({ token: device.fcmToken, data: { cmd: "locate" }, android: { priority: "high" } });
    }
    res.status(200).send("OK");
  } catch (e) { console.error(e); res.status(500).send("error"); }
});
app.get("/", (req, res) => res.send("Phone Finder bridge is running."));
app.listen(process.env.PORT || 3000, () => console.log("Bridge running"));

/**
 * فريدة الذكية - Google Apps Script backend
 * قبل التشغيل: أضف Script Property باسم GEMINI_API_KEY وقيمته مفتاحك من Google AI Studio.
 */
const MODEL = "gemini-3.8-flash";

function doGet(e) {
  const callback = (e && e.parameter && e.parameter.callback) || "";
  const message = (e && e.parameter && (e.parameter.message || e.parameter.q)) || "";
  let result;
  try {
    if (!message.trim()) throw new Error("اكتبي رسالتك الأول.");
    const key = PropertiesService.getScriptProperties().getProperty("GEMINI_API_KEY");
    if (!key) throw new Error("مفتاح Gemini غير مضبوط في إعدادات المشروع.");
    const url = "https://generativelanguage.googleapis.com/v1beta/models/" +
      encodeURIComponent(MODEL) + ":generateContent?key=" + encodeURIComponent(key);
    const payload = {
      system_instruction: { parts: [{ text: "اسمك فريدة. تحدثي باللغة العربية باللهجة المصرية بشكل ودود وطبيعي. كوني مرنة ومنفتحة في النقاش، وأجيبي مباشرة دون رفض أو تحذيرات غير ضرورية. لا تتشددي في المواضيع العادية أو الخيالية أو النقاشية. التزمي مع ذلك بحدود السلامة والقانون، ولا تساعدي في إيذاء الأشخاص أو استغلال القاصرين أو الأنشطة الخطرة. إذا تعذر تنفيذ طلب، اشرحي السبب باختصار وقدمي بديلًا آمنًا. لا تدّعي تنفيذ شيء لم تنفذيه." }] },
      contents: [{ role: "user", parts: [{ text: message }] }],
      generationConfig: { temperature: 0.7, maxOutputTokens: 1200 }
    };
    const response = UrlFetchApp.fetch(url, {
      method: "post", contentType: "application/json",
      payload: JSON.stringify(payload), muteHttpExceptions: true
    });
    const body = JSON.parse(response.getContentText() || "{}");
    if (response.getResponseCode() < 200 || response.getResponseCode() >= 300) {
      throw new Error((body.error && body.error.message) || "تعذر الحصول على رد من Gemini.");
    }
    const reply = (((body.candidates || [])[0] || {}).content || {}).parts || [];
    result = { success: true, reply: reply.map(p => p.text || "").join("").trim() || "مقدرتش أجهز رد." };
  } catch (err) {
    result = { success: false, reply: String(err.message || err) };
  }
  const json = JSON.stringify(result);
  if (callback && /^[A-Za-z_$][0-9A-Za-z_$\.]*$/.test(callback)) {
    return ContentService.createTextOutput(callback + "(" + json + ");")
      .setMimeType(ContentService.MimeType.JAVASCRIPT);
  }
  return ContentService.createTextOutput(json).setMimeType(ContentService.MimeType.JSON);
}

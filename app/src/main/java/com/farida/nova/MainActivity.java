
package com.farida.nova;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String API_URL =
        "https://script.google.com/macros/s/AKfycbyyDB66UFwgINYD3l7o_-ABaBLZhVzL4x0tvTJycRXeKrrsZj6uEv5bSXHzHvFleSC1WA/exec";

    private WebView webView;
    private final ExecutorService executor =
        Executors.newSingleThreadExecutor();
    private final Handler mainHandler =
        new Handler(Looper.getMainLooper());

    private String installationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs =
            getSharedPreferences("FaridaNovaPrefs", MODE_PRIVATE);

        installationId = prefs.getString("installation_id", null);

        if (installationId == null || installationId.trim().isEmpty()) {
            installationId = UUID.randomUUID().toString();
            prefs.edit().putString("installation_id", installationId).apply();
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new FaridaBridge(), "FaridaAndroid");

        webView.loadUrl("file:///android_asset/index.html");
    }

    private class FaridaBridge {

        @JavascriptInterface
        public void askFarida(String message, String callbackName) {

            final String cleanMessage =
                message == null ? "" : message.trim();

            final String cleanCallback =
                callbackName == null ? "" :
                callbackName.replaceAll("[^a-zA-Z0-9_$]", "");

            if (cleanMessage.isEmpty() || cleanCallback.isEmpty()) return;

            executor.execute(() -> {

                JSONObject result;

                try {
                    result = sendToService(cleanMessage);
                } catch (Exception e) {
                    result = new JSONObject();

                    try {
                        result.put("success", false);
                        result.put(
                            "reply",
                            "تعذر الاتصال بالخدمة: " +
                            (e.getMessage() == null
                                ? "خطأ غير معروف"
                                : e.getMessage())
                        );
                    } catch (Exception ignored) {}
                }

                final String json = result.toString();

                mainHandler.post(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript(
                            "if(window['" + cleanCallback + "']){" +
                            "window['" + cleanCallback + "'](" + json + ");}",
                            null
                        );
                    }
                });
            });
        }
    }

    private JSONObject sendToService(String message) throws Exception {

        JSONObject payload = new JSONObject();
        payload.put("message", message);
        payload.put("userId", installationId);

        HttpURLConnection connection =
            (HttpURLConnection) new URL(API_URL).openConnection();

        connection.setRequestMethod("POST");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(75000);
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(true);

        connection.setRequestProperty(
            "Content-Type", "application/json; charset=UTF-8"
        );
        connection.setRequestProperty("Accept", "application/json");

        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);

        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
            output.flush();
        }

        int status = connection.getResponseCode();

        InputStream stream =
            (status >= 200 && status < 400)
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (stream == null) {
            connection.disconnect();
            throw new Exception("الخادم لم يرجع بيانات.");
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int count;

        while ((count = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, count);
        }

        stream.close();
        connection.disconnect();

        String body = new String(
            buffer.toByteArray(),
            StandardCharsets.UTF_8
        );

        if (status < 200 || status >= 400) {
            throw new Exception("استجابة الخادم: " + status);
        }

        return new JSONObject(body);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("FaridaAndroid");
            webView.destroy();
            webView = null;
        }

        executor.shutdownNow();
        super.onDestroy();
    }
}

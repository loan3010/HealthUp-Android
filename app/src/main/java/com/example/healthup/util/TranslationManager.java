package com.example.healthup.util;

import android.content.Context;
import android.os.AsyncTask;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class TranslationManager {
    
    public interface TranslationCallback {
        void onTranslationDone(String translatedText);
    }

    // Sử dụng Google Script làm proxy miễn phí để dịch thuật (Không cần API Key phức tạp)
    public static void translate(String text, String targetLang, TranslationCallback callback) {
        if (targetLang.equals("vi")) {
            callback.onTranslationDone(text);
            return;
        }

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=vi&tl=" + targetLang + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");
                    URL url = new URL(urlStr);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestProperty("User-Agent", "Mozilla/5.0");
                    
                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    
                    // Parse kết quả từ JSON mảng của Google
                    String result = response.toString();
                    result = result.substring(4, result.indexOf("\"", 4));
                    return result;
                } catch (Exception e) {
                    return text; // Trả về text gốc nếu lỗi
                }
            }

            @Override
            protected void onPostExecute(String s) {
                callback.onTranslationDone(s);
            }
        }.execute();
    }
}

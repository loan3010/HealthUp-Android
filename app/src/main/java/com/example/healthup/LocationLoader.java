package com.example.healthup;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationLoader {

    private static List<String> provinces = new ArrayList<>();
    private static Map<String, List<String>> provinceWardsMap = new HashMap<>();

    public static void load(Context context) {
        if (!provinces.isEmpty()) return;

        try {
            InputStream is = context.getAssets().open("vn_locations.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);

            JSONArray array = new JSONArray(json);
            provinces.add("Chọn Tỉnh/Thành phố");
            
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String provinceName = obj.getString("province");
                provinces.add(provinceName);

                JSONArray wardsArray = obj.getJSONArray("wards");
                List<String> wards = new ArrayList<>();
                wards.add("Chọn Quận/Huyện/Phường/Xã");
                for (int j = 0; j < wardsArray.length(); j++) {
                    wards.add(wardsArray.getString(j));
                }
                provinceWardsMap.put(provinceName, wards);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback nếu lỗi load JSON
            provinces.add("Bình Định");
            provinces.add("TP. Hồ Chí Minh");
            provinces.add("Hà Nội");
        }
    }

    public static List<String> getProvinceNames() {
        return provinces;
    }

    public static List<String> getWards(String province) {
        if (province == null || !provinceWardsMap.containsKey(province)) {
            return Collections.singletonList("Chọn Quận/Huyện/Phường/Xã");
        }
        return provinceWardsMap.get(province);
    }
}


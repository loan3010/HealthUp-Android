package com.example.healthup;

import android.content.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationLoader {

    private static List<String> provinces = new ArrayList<>();
    private static Map<String, List<String>> districtsMap = new HashMap<>();
    private static Map<String, List<String>> wardsMap = new HashMap<>();

    public static void load(Context context) {
        if (!provinces.isEmpty()) return;

        provinces.add("Chọn Tỉnh/Thành phố");
        provinces.add("Bình Định");
        provinces.add("TP. Hồ Chí Minh");
        provinces.add("Hà Nội");
        provinces.add("Đà Nẵng");
        provinces.add("Cần Thơ");
        provinces.add("Hải Phòng");

        // Bình Định
        districtsMap.put("Bình Định", Arrays.asList("Chọn Quận/Huyện", "TX. An Nhơn", "TP. Quy Nhơn", "Huyện Tuy Phước", "Huyện Phù Cát", "Huyện Hoài Nhơn"));
        wardsMap.put("TX. An Nhơn", Arrays.asList("Chọn Phường/Xã", "Nhơn Thành", "Nhơn An", "Đập Đá", "Nhơn Hậu", "Nhơn Hòa"));
        wardsMap.put("TP. Quy Nhơn", Arrays.asList("Chọn Phường/Xã", "Nguyễn Văn Cừ", "Lê Lợi", "Quang Trung", "Ghềnh Ráng", "Nhơn Lý"));

        // TP. Hồ Chí Minh
        districtsMap.put("TP. Hồ Chí Minh", Arrays.asList("Chọn Quận/Huyện", "Quận 1", "Quận 3", "Quận 5", "Quận 7", "Quận 10", "TP. Thủ Đức", "Huyện Bình Chánh", "Huyện Hóc Môn"));
        wardsMap.put("Quận 1", Arrays.asList("Chọn Phường/Xã", "Phường Bến Nghé", "Phường Đa Kao", "Phường Tân Định", "Phường Bến Thành", "Phường Phạm Ngũ Lão"));
        wardsMap.put("TP. Thủ Đức", Arrays.asList("Chọn Phường/Xã", "Phường Linh Trung", "Phường Linh Tây", "Phường Thảo Điền", "Phường Hiệp Phú", "Phường Tăng Nhơn Phú A"));
        wardsMap.put("Quận 7", Arrays.asList("Chọn Phường/Xã", "Phường Tân Phong", "Phường Tân Kiểng", "Phường Phú Mỹ", "Phường Tân Thuận Đông"));

        // Hà Nội
        districtsMap.put("Hà Nội", Arrays.asList("Chọn Quận/Huyện", "Quận Đống Đa", "Quận Hoàn Kiếm", "Quận Ba Đình", "Quận Hai Bà Trưng", "Quận Cầu Giấy", "Quận Tây Hồ"));
        wardsMap.put("Quận Đống Đa", Arrays.asList("Chọn Phường/Xã", "Láng Hạ", "Kim Liên", "Ô Chợ Dừa", "Khâm Thiên", "Quang Trung"));
        wardsMap.put("Quận Hoàn Kiếm", Arrays.asList("Chọn Phường/Xã", "Hàng Bạc", "Hàng Trống", "Tràng Tiền", "Cửa Đông"));

        // Đà Nẵng
        districtsMap.put("Đà Nẵng", Arrays.asList("Chọn Quận/Huyện", "Quận Hải Châu", "Quận Thanh Khê", "Quận Liên Chiểu", "Quận Sơn Trà"));
        wardsMap.put("Quận Hải Châu", Arrays.asList("Chọn Phường/Xã", "Thạch Thang", "Hải Châu I", "Hải Châu II", "Phước Ninh"));
    }

    public static List<String> getProvinceNames() {
        return provinces;
    }

    public static List<String> getDistricts(String province) {
        if (province == null || !districtsMap.containsKey(province)) {
            return Arrays.asList("Chọn Quận/Huyện");
        }
        return districtsMap.get(province);
    }

    public static List<String> getWards(String district) {
        if (district == null || !wardsMap.containsKey(district)) {
            return Arrays.asList("Chọn Phường/Xã");
        }
        return wardsMap.get(district);
    }
}

package com.example.healthup.chat;

import com.example.models.FAQ;

import java.util.ArrayList;
import java.util.List;

/**
 * Static, local FAQ list for the keyword/fuzzy bot (offline, no Firestore reads).
 *
 * <p>Keywords are matched after {@link TextNormalizer} (exact + fuzzy).</p>
 */
public final class FaqProvider {

    private FaqProvider() {
    }

    private static List<FAQ> cached;

    public static List<FAQ> getFaqs() {
        if (cached != null) {
            return cached;
        }
        List<FAQ> list = new ArrayList<>();
        list.add(new FAQ(
                "shipping_time",
                "Thời gian giao hàng mất bao lâu?",
                "HealthUp giao hàng trong 2–4 ngày làm việc với nội thành và 3–7 ngày với các tỉnh khác. "
                        + "Bạn có thể theo dõi trạng thái đơn ngay trong mục \"Kiểm tra đơn hàng\".",
                "giao hang", "van chuyen", "ship", "bao lau", "may ngay", "thoi gian giao",
                "thoi gian", "bao nhieu ngay", "giao mat bao lau", "ship bao lau",
                "khi nao giao", "bao gio co hang"));
        list.add(new FAQ(
                "shipping_fee",
                "Phí vận chuyển tính như thế nào?",
                "Phí vận chuyển từ 15.000đ tùy khu vực. Đơn hàng từ 300.000đ trở lên được MIỄN PHÍ vận chuyển toàn quốc.",
                "phi ship", "phi van chuyen", "phi giao hang", "mien phi ship", "freeship",
                "ship bao nhieu", "cuoc ship", "phi van", "mien phi van chuyen",
                "ship bao nhieu tien", "tinh phi ship"));
        list.add(new FAQ(
                "return_policy",
                "Chính sách đổi trả ra sao?",
                "Bạn được đổi/trả trong vòng 7 ngày kể từ khi nhận hàng nếu sản phẩm còn nguyên tem, chưa mở niêm phong. "
                        + "Xem chi tiết trong mục Chính sách đổi trả của ứng dụng.",
                "doi tra", "hoan tra", "tra hang", "chinh sach doi", "chinh sach", "bao hanh",
                "doi san pham", "tra san pham", "hoan tien", "refund", "return"));
        list.add(new FAQ(
                "payment_methods",
                "HealthUp hỗ trợ thanh toán nào?",
                "HealthUp hỗ trợ thanh toán khi nhận hàng (COD), chuyển khoản ngân hàng và ví điện tử (Momo, ZaloPay).",
                "thanh toan", "tra tien", "cod", "chuyen khoan", "vi dien tu", "momo",
                "zalopay", "vnpay", "cach thanh toan", "thanh toan the", "tra sau"));
        list.add(new FAQ(
                "membership",
                "Hạng thành viên có lợi ích gì?",
                "Thành viên HealthUp tích điểm cho mỗi đơn hàng và nhận ưu đãi theo hạng (Bạc, Vàng, Kim Cương): "
                        + "giảm giá riêng, quà sinh nhật và freeship thường xuyên hơn.",
                "thanh vien", "hang", "tich diem", "membership", "uu dai", "vip",
                "hang thanh vien", "diem thuong", "diem tich luy", "loi ich thanh vien"));
        cached = list;
        return cached;
    }

    /**
     * Best FAQ for a (preferably already-normalized) question.
     * Exact keyword hits score 2; fuzzy token hits score 1.
     */
    public static FAQ match(String text) {
        String normalized = TextNormalizer.normalize(text);
        if (normalized.isEmpty()) {
            return null;
        }
        FAQ best = null;
        int bestScore = 0;
        for (FAQ faq : getFaqs()) {
            int score = scoreFaq(normalized, faq);
            if (score > bestScore) {
                bestScore = score;
                best = faq;
            }
        }
        // Require at least one solid hit to avoid random FAQ on greetings.
        return bestScore >= 2 ? best : null;
    }

    private static int scoreFaq(String normalized, FAQ faq) {
        if (faq.getKeywords() == null) {
            return 0;
        }
        int score = 0;
        for (String keyword : faq.getKeywords()) {
            if (keyword == null || keyword.isEmpty()) {
                continue;
            }
            String key = TextNormalizer.normalize(keyword);
            if (key.isEmpty()) {
                continue;
            }
            if (normalized.contains(key)) {
                score += 2;
                continue;
            }
            if (key.length() >= 4
                    && TextNormalizer.matchAny(normalized, true, key) >= 0) {
                score += 1;
            }
        }
        return score;
    }
}

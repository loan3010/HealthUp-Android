package com.example.healthup.chat;

import com.example.models.FAQ;

import java.util.ArrayList;
import java.util.List;

/**
 * Static, local (free) list of FAQs used by the keyword bot.
 *
 * <p>Kept in code so the feature works fully offline with no Firestore reads and
 * no Cloud Functions. Keywords are stored already-normalized (see
 * {@link TextNormalizer}).</p>
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
                "giao hang", "van chuyen", "ship", "bao lau", "may ngay", "thoi gian giao", "thoi gian"));
        list.add(new FAQ(
                "shipping_fee",
                "Phí vận chuyển tính như thế nào?",
                "Phí vận chuyển từ 15.000đ tùy khu vực. Đơn hàng từ 300.000đ trở lên được MIỄN PHÍ vận chuyển toàn quốc.",
                "phi ship", "phi van chuyen", "phi giao hang", "mien phi ship", "freeship"));
        list.add(new FAQ(
                "return_policy",
                "Chính sách đổi trả ra sao?",
                "Bạn được đổi/trả trong vòng 7 ngày kể từ khi nhận hàng nếu sản phẩm còn nguyên tem, chưa mở niêm phong. "
                        + "Xem chi tiết trong mục Chính sách đổi trả của ứng dụng.",
                "doi tra", "hoan tra", "tra hang", "chinh sach doi", "chinh sach", "bao hanh"));
        list.add(new FAQ(
                "payment_methods",
                "HealthUp hỗ trợ thanh toán nào?",
                "HealthUp hỗ trợ thanh toán khi nhận hàng (COD), chuyển khoản ngân hàng và ví điện tử (Momo, ZaloPay).",
                "thanh toan", "tra tien", "cod", "chuyen khoan", "vi dien tu", "momo"));
        list.add(new FAQ(
                "membership",
                "Hạng thành viên có lợi ích gì?",
                "Thành viên HealthUp tích điểm cho mỗi đơn hàng và nhận ưu đãi theo hạng (Bạc, Vàng, Kim Cương): "
                        + "giảm giá riêng, quà sinh nhật và freeship thường xuyên hơn.",
                "thanh vien", "hang", "tich diem", "membership", "uu dai", "vip"));
        cached = list;
        return cached;
    }

    /** Returns the best-matching FAQ for a normalized question, or null. */
    public static FAQ match(String normalizedText) {
        FAQ best = null;
        int bestScore = 0;
        for (FAQ faq : getFaqs()) {
            int score = 0;
            if (faq.getKeywords() != null) {
                for (String keyword : faq.getKeywords()) {
                    if (!keyword.isEmpty() && normalizedText.contains(keyword)) {
                        score++;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = faq;
            }
        }
        return best;
    }
}

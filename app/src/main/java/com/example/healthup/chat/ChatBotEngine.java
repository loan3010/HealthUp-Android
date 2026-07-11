package com.example.healthup.chat;

import com.example.models.ChatMessage;
import com.example.models.FAQ;

import java.util.ArrayList;
import java.util.List;

/**
 * Local, rule-based (keyword) chatbot. 100% offline and free: no Cloud
 * Functions, no paid AI. It classifies a user message into an {@link Intent}
 * and produces canned bot replies.
 *
 * <p>Matching is diacritic-insensitive (see {@link TextNormalizer}) so that
 * "huy don", "hủy đơn" and "HỦY ĐƠN" all map to the same intent.</p>
 *
 * <p><b>Smarter chatbox (free tier, no Blaze/Cloud Functions):</b></p>
 * <ul>
 *   <li>Expand {@link FaqProvider} and keyword lists here (cheapest, offline).</li>
 *   <li>Add fuzzy/levenshtein matching in {@link TextNormalizer} for typos.</li>
 *   <li>Optional Gemini REST from the app with {@code GEMINI_API_KEY} in
 *       {@code local.properties} — risky (key in APK, quota cost); prefer a
 *       thin backend proxy if budget allows later.</li>
 *   <li>Firebase ML Kit / on-device NLU is not a fit for open-ended Q&amp;A.</li>
 * </ul>
 */
public class ChatBotEngine {

    public enum Intent {
        GREETING,
        ORDER_STATUS,
        CANCEL_ORDER,
        PRODUCT_ADVICE,
        FAQ,
        FALLBACK
    }

    /** Result of processing a single user message. */
    public static class BotResponse {
        public final Intent intent;
        public final List<ChatMessage> messages;
        /** When true the caller should query Firestore orders and render cards. */
        public final boolean needsOrderLookup;
        /** When true the UI should surface the "Chat với người bán" action. */
        public final boolean offerHumanHandoff;
        /** When true the guest must sign in before continuing. */
        public final boolean requiresLogin;

        BotResponse(Intent intent, List<ChatMessage> messages,
                    boolean needsOrderLookup, boolean offerHumanHandoff, boolean requiresLogin) {
            this.intent = intent;
            this.messages = messages;
            this.needsOrderLookup = needsOrderLookup;
            this.offerHumanHandoff = offerHumanHandoff;
            this.requiresLogin = requiresLogin;
        }

        BotResponse(Intent intent, List<ChatMessage> messages,
                    boolean needsOrderLookup, boolean offerHumanHandoff) {
            this(intent, messages, needsOrderLookup, offerHumanHandoff, false);
        }
    }

    private static ChatMessage bot(String text) {
        return ChatMessage.text(ChatMessage.SENDER_BOT, ChatMessage.SENDER_BOT, text);
    }

    public BotResponse process(String rawText) {
        String text = TextNormalizer.normalize(rawText);
        List<ChatMessage> out = new ArrayList<>();

        // 1) Cancel order (before generic "don hang" so "huy don hang" matches here)
        if (TextNormalizer.containsAny(text,
                "huy don", "huy dat hang", "khong muon mua", "huy mua", "muon huy",
                "cancel order", "huy")) {
            out.add(bot("Bạn có thể tự hủy đơn khi đơn vẫn ở trạng thái \"Chờ xác nhận\": "
                    + "vào Đơn hàng của tôi → chọn đơn → Hủy đơn.\n\n"
                    + "Nếu đơn đã được xác nhận hoặc đang giao và bạn vẫn muốn hủy, "
                    + "mình sẽ kết nối bạn với người bán để được hỗ trợ."));
            return new BotResponse(Intent.CANCEL_ORDER, out, false, true);
        }

        // 2) Order status lookup
        if (TextNormalizer.containsAny(text,
                "kiem tra don", "tinh trang don", "trang thai don", "don hang cua toi",
                "theo doi don", "don da giao", "giao chua", "toi don", "van don",
                "dang o dau", "don dang o", "order status", "my order")
                || (TextNormalizer.containsAny(text, "don hang", "ho tro ve don")
                && !TextNormalizer.containsAny(text, "san pham"))) {
            out.add(bot("Để mình kiểm tra giúp bạn nhé. Đây là các đơn hàng gần đây của bạn:"));
            return new BotResponse(Intent.ORDER_STATUS, out, true, false);
        }

        // 3) Product inquiry (from product detail or order detail)
        if (TextNormalizer.containsAny(text,
                "toi muon hoi ve san pham", "hoi ve san pham", "can hoi ve san pham",
                "tu van san pham")) {
            out.add(bot(buildProductInquiryReply(rawText)));
            return new BotResponse(Intent.PRODUCT_ADVICE, out, false, true);
        }

        // 4) Product & nutrition advice
        if (TextNormalizer.containsAny(text,
                "tu van", "san pham", "dinh duong", "vitamin", "protein", "whey", "thuc pham",
                "bo sung", "goi y", "nen mua", "uong gi", "an gi", "suc khoe", "giam can", "tang can",
                "tang co")) {
            out.add(bot("HealthUp có thể gợi ý theo mục tiêu của bạn:\n"
                    + "• Tăng cơ: Whey Protein, BCAA\n"
                    + "• Giảm cân: Thực phẩm ít calo, trà thảo mộc\n"
                    + "• Tăng đề kháng: Vitamin C, Kẽm, Omega-3\n\n"
                    + "Bạn đang quan tâm mục tiêu nào? Bạn cũng có thể xem thêm ở mục Sản phẩm. "
                    + "Nếu cần tư vấn chuyên sâu, mình có thể kết nối bạn với người bán."));
            return new BotResponse(Intent.PRODUCT_ADVICE, out, false, true);
        }

        // 4) FAQ overview (e.g. tapping "Câu hỏi thường gặp")
        if (TextNormalizer.containsAny(text, "cau hoi thuong gap", "thuong gap", "faq")) {
            StringBuilder sb = new StringBuilder("Một số câu hỏi thường gặp tại HealthUp:\n");
            for (FAQ f : FaqProvider.getFaqs()) {
                sb.append("• ").append(f.getQuestion()).append('\n');
            }
            sb.append("\nBạn hãy nhập câu hỏi của mình để mình trả lời chi tiết nhé.");
            out.add(bot(sb.toString().trim()));
            return new BotResponse(Intent.FAQ, out, false, false);
        }

        // 5) Specific FAQ match by keywords
        FAQ faq = FaqProvider.match(text);
        if (faq != null) {
            out.add(bot(faq.getAnswer()));
            return new BotResponse(Intent.FAQ, out, false, false);
        }

        // 6) Greeting
        if (TextNormalizer.containsAny(text, "xin chao", "chao", "hello", "hi", "alo", "hey")) {
            out.add(bot("Xin chào! HealthUp có thể giúp gì cho bạn hôm nay? "
                    + "Bạn có thể hỏi về đơn hàng, sản phẩm, dinh dưỡng hoặc chính sách nhé."));
            return new BotResponse(Intent.GREETING, out, false, false);
        }

        // 7) Fallback -> offer human handoff
        out.add(bot("Xin lỗi, mình chưa hiểu rõ câu hỏi của bạn. "
                + "Bạn có thể chọn một chủ đề gợi ý, hoặc để mình kết nối với nhân viên để được hỗ trợ trực tiếp nhé."));
        return new BotResponse(Intent.FALLBACK, out, false, true, true);
    }

    /** Bot greeting shown when the thread is opened. */
    public ChatMessage greeting() {
        return bot("Xin chào! Mình là trợ lý HealthUp \uD83C\uDF3F\n"
                + "Mình có thể giúp bạn kiểm tra đơn hàng, hủy đơn, tư vấn sản phẩm & dinh dưỡng "
                + "và trả lời các câu hỏi thường gặp.");
    }

    private static String buildProductInquiryReply(String rawText) {
        String productLabel = extractProductLabel(rawText);
        return "Mình đã nhận câu hỏi của bạn về sản phẩm"
                + (productLabel.isEmpty() ? "" : " \"" + productLabel + "\"")
                + " trong đơn hàng.\n\n"
                + "Bạn có thể hỏi cụ thể về:\n"
                + "• Thành phần & hạn sử dụng\n"
                + "• Cách bảo quản & liều dùng\n"
                + "• Đổi/trả nếu sản phẩm có vấn đề\n\n"
                + "Hãy gõ câu hỏi của bạn, hoặc chọn \"Chat với người bán\" để được tư vấn trực tiếp nhé.";
    }

    /** Pulls product name from "…sản phẩm X trong đơn…" when present. */
    private static String extractProductLabel(String rawText) {
        if (rawText == null) {
            return "";
        }
        String normalized = TextNormalizer.normalize(rawText);
        int start = normalized.indexOf("san pham ");
        if (start < 0) {
            return "";
        }
        start += "san pham ".length();
        int end = normalized.indexOf(" trong don", start);
        if (end < 0) {
            end = normalized.length();
        }
        String label = normalized.substring(start, end).trim();
        return label.isEmpty() ? "" : label;
    }
}

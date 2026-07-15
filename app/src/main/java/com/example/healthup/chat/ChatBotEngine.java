package com.example.healthup.chat;

import com.example.models.ChatMessage;
import com.example.models.FAQ;

import java.util.ArrayList;
import java.util.List;

/**
 * Local, rule-based chatbot with expanded synonym lists + fuzzy matching.
 * Still 100% offline (no Cloud Functions / paid AI).
 *
 * <p>Matching is diacritic-insensitive and typo-tolerant via
 * {@link TextNormalizer} (Levenshtein). See {@link BotIntentLexicon} for
 * paraphrase coverage (e.g. "đơn tôi tới đâu rồi" → order status).</p>
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

    private static boolean hit(String normalized, String... keywords) {
        return TextNormalizer.matchAny(normalized, true, keywords) >= 0;
    }

    public BotResponse process(String rawText) {
        String text = TextNormalizer.normalize(rawText);
        List<ChatMessage> messages = new ArrayList<>();

        // 1) Cancel order (before generic order phrases)
        if (hit(text, BotIntentLexicon.CANCEL_ORDER)) {
            messages.add(bot("Bạn có thể tự hủy đơn khi đơn vẫn ở trạng thái \"Chờ xác nhận\": "
                    + "vào Đơn hàng của tôi → chọn đơn → Hủy đơn.\n\n"
                    + "Nếu đơn đã được xác nhận hoặc đang giao và bạn vẫn muốn hủy, "
                    + "mình sẽ kết nối bạn với người bán để được hỗ trợ."));
            return new BotResponse(Intent.CANCEL_ORDER, messages, false, true);
        }

        // 2) Order status / tracking (paraphrases + fuzzy)
        if (hit(text, BotIntentLexicon.ORDER_STATUS)
                || BotIntentLexicon.looksLikeOrderQuestion(text)) {
            messages.add(bot("Để mình kiểm tra giúp bạn nhé. Đây là các đơn hàng gần đây của bạn:"));
            return new BotResponse(Intent.ORDER_STATUS, messages, true, false);
        }

        // 3) Product inquiry (from product / order detail deep-link phrasing)
        if (hit(text, BotIntentLexicon.PRODUCT_INQUIRY)) {
            messages.add(bot(buildProductInquiryReply(rawText)));
            return new BotResponse(Intent.PRODUCT_ADVICE, messages, false, true);
        }

        // 4) Product & nutrition advice
        if (hit(text, BotIntentLexicon.PRODUCT_ADVICE)) {
            messages.add(bot("HealthUp có thể gợi ý theo mục tiêu của bạn:\n"
                    + "• Tăng cơ: Whey Protein, BCAA\n"
                    + "• Giảm cân: Thực phẩm ít calo, trà thảo mộc\n"
                    + "• Tăng đề kháng: Vitamin C, Kẽm, Omega-3\n\n"
                    + "Bạn đang quan tâm mục tiêu nào? Bạn cũng có thể xem thêm ở mục Sản phẩm. "
                    + "Nếu cần tư vấn chuyên sâu, mình có thể kết nối bạn với người bán."));
            return new BotResponse(Intent.PRODUCT_ADVICE, messages, false, true);
        }

        // 5) FAQ overview
        if (hit(text, BotIntentLexicon.FAQ_MENU)) {
            StringBuilder sb = new StringBuilder("Một số câu hỏi thường gặp tại HealthUp:\n");
            for (FAQ f : FaqProvider.getFaqs()) {
                sb.append("• ").append(f.getQuestion()).append('\n');
            }
            sb.append("\nBạn hãy nhập câu hỏi của mình để mình trả lời chi tiết nhé.");
            messages.add(bot(sb.toString().trim()));
            return new BotResponse(Intent.FAQ, messages, false, false);
        }

        // 6) Specific FAQ (synonyms + fuzzy keyword score)
        FAQ faq = FaqProvider.match(text);
        if (faq != null) {
            messages.add(bot(faq.getAnswer()));
            return new BotResponse(Intent.FAQ, messages, false, false);
        }

        // 7) Greeting
        if (hit(text, BotIntentLexicon.GREETING)) {
            messages.add(bot("Xin chào! HealthUp có thể giúp gì cho bạn hôm nay? "
                    + "Bạn có thể hỏi về đơn hàng, sản phẩm, dinh dưỡng hoặc chính sách nhé."));
            return new BotResponse(Intent.GREETING, messages, false, false);
        }

        // 8) Fallback -> offer human handoff
        messages.add(bot("Xin lỗi, mình chưa hiểu rõ câu hỏi của bạn. "
                + "Bạn có thể hỏi về trạng thái đơn (\"đơn tôi tới đâu rồi\"), hủy đơn, "
                + "tư vấn sản phẩm, hoặc chọn \"Chat với người bán\" để được hỗ trợ trực tiếp nhé."));
        return new BotResponse(Intent.FALLBACK, messages, false, true);
    }

    /** Bot greeting shown when the thread is opened. */
    public ChatMessage greeting() {
        return bot("Xin chào! Mình là trợ lý HealthUp \uD83C\uDF3F\n"
                + "Mình có thể giúp bạn kiểm tra đơn hàng, hủy đơn, tư vấn sản phẩm & dinh dưỡng "
                + "và trả lời các câu hỏi thường gặp.\n"
                + "Ví dụ: \"đơn tôi tới đâu rồi\", \"phí ship bao nhiêu\", \"gợi ý whey tăng cơ\".");
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

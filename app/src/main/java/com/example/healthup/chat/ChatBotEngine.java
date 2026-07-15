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
        MEMBERSHIP,
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
        /** When true the caller should load membership spend / tier. */
        public final boolean needsMembershipLookup;
        /** When true the caller should show category chips then product suggestions. */
        public final boolean needsCategoryLookup;
        /** When true the UI should surface the "Chat với người bán" action. */
        public final boolean offerHumanHandoff;
        /** When true the guest must sign in before continuing. */
        public final boolean requiresLogin;

        BotResponse(Intent intent, List<ChatMessage> messages,
                    boolean needsOrderLookup, boolean needsMembershipLookup,
                    boolean needsCategoryLookup,
                    boolean offerHumanHandoff, boolean requiresLogin) {
            this.intent = intent;
            this.messages = messages;
            this.needsOrderLookup = needsOrderLookup;
            this.needsMembershipLookup = needsMembershipLookup;
            this.needsCategoryLookup = needsCategoryLookup;
            this.offerHumanHandoff = offerHumanHandoff;
            this.requiresLogin = requiresLogin;
        }

        BotResponse(Intent intent, List<ChatMessage> messages,
                    boolean needsOrderLookup, boolean needsMembershipLookup,
                    boolean offerHumanHandoff, boolean requiresLogin) {
            this(intent, messages, needsOrderLookup, needsMembershipLookup,
                    false, offerHumanHandoff, requiresLogin);
        }

        BotResponse(Intent intent, List<ChatMessage> messages,
                    boolean needsOrderLookup, boolean offerHumanHandoff) {
            this(intent, messages, needsOrderLookup, false, false, offerHumanHandoff, false);
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

        // 1) Cancel order (before generic order phrases) — pick a recent order first
        if (hit(text, BotIntentLexicon.CANCEL_ORDER)) {
            messages.add(bot("Bạn muốn hủy đơn nào ạ? HealthUp lấy vài đơn gần đây để bạn chọn nhé."));
            return new BotResponse(Intent.CANCEL_ORDER, messages, true, false, true, false);
        }

        // 2) Order status BEFORE membership — "Kiểm tra đơn hàng" must never
        // hit loyalty (membership phrase "kiem tra hang" used to fuzzy-match it).
        if (hit(text, BotIntentLexicon.ORDER_STATUS)
                || BotIntentLexicon.looksLikeOrderQuestion(text)) {
            messages.add(bot("Để HealthUp kiểm tra giúp bạn, bạn chọn một trong các đơn gần đây bên dưới nhé 💚"));
            return new BotResponse(Intent.ORDER_STATUS, messages, true, false, false, false);
        }

        // 3) Membership / Khách hàng thân thiết (VIP)
        if (hit(text, BotIntentLexicon.MEMBERSHIP)) {
            messages.add(bot("Để mình xem hạng khách hàng thân thiết HealthUp của bạn nhé 💚"));
            return new BotResponse(Intent.MEMBERSHIP, messages, false, true, false, false);
        }

        // 4) Product inquiry about an order item (not general browse advice)
        if (hit(text, BotIntentLexicon.PRODUCT_INQUIRY)) {
            messages.add(bot(buildProductInquiryReply(rawText)));
            return new BotResponse(Intent.PRODUCT_ADVICE, messages, false, true);
        }

        // 5) Product & nutrition advice → pick a category then suggest products
        if (hit(text, BotIntentLexicon.PRODUCT_ADVICE)) {
            messages.add(bot("Bạn muốn HealthUp gợi ý sản phẩm theo danh mục nào? "
                    + "Chọn một nhóm bên dưới nhé 💚"));
            return new BotResponse(Intent.PRODUCT_ADVICE, messages,
                    false, false, true, false, false);
        }

        // 6) FAQ overview
        if (hit(text, BotIntentLexicon.FAQ_MENU)) {
            StringBuilder sb = new StringBuilder("Một số câu hỏi thường gặp tại HealthUp:\n");
            for (FAQ f : FaqProvider.getFaqs()) {
                sb.append("• ").append(f.getQuestion()).append('\n');
            }
            sb.append("\nBạn hãy nhập câu hỏi của mình để mình trả lời chi tiết nhé.");
            messages.add(bot(sb.toString().trim()));
            return new BotResponse(Intent.FAQ, messages, false, false);
        }

        // 7) Specific FAQ (synonyms + fuzzy keyword score)
        FAQ faq = FaqProvider.match(text);
        if (faq != null) {
            messages.add(bot(faq.getAnswer()));
            return new BotResponse(Intent.FAQ, messages, false, false);
        }

        // 8) Greeting
        if (hit(text, BotIntentLexicon.GREETING)) {
            messages.add(bot("Xin chào! HealthUp có thể giúp gì cho bạn hôm nay? "
                    + "Bạn có thể hỏi về đơn hàng, khách hàng thân thiết / VIP, sản phẩm, "
                    + "dinh dưỡng hoặc chính sách nhé."));
            return new BotResponse(Intent.GREETING, messages, false, false);
        }

        // 9) Fallback -> offer human handoff
        messages.add(bot("Xin lỗi bạn nhé, mình chưa hiểu rõ câu hỏi. "
                + "Bạn có thể hỏi về trạng thái đơn, khách hàng thân thiết / VIP, hủy đơn, "
                + "tư vấn sản phẩm, hoặc chọn \"Chat với người bán\" để HealthUp hỗ trợ trực tiếp ạ."));
        return new BotResponse(Intent.FALLBACK, messages, false, true);
    }

    /** Bot greeting shown when the thread is opened. */
    public ChatMessage greeting() {
        return bot("Xin chào! Mình là trợ lý HealthUp \uD83C\uDF3F\n"
                + "Mình có thể giúp bạn kiểm tra đơn hàng, xem khách hàng thân thiết / VIP, hủy đơn, "
                + "tư vấn sản phẩm & dinh dưỡng và trả lời câu hỏi thường gặp.\n"
                + "Ví dụ: \"đơn tôi tới đâu rồi\", \"khách hàng thân thiết\", \"gợi ý whey tăng cơ\".");
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

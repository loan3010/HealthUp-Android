package com.example.healthup.chat;

import androidx.annotation.NonNull;

/**
 * Synonym / paraphrase lists per bot intent (already conceptually normalized;
 * {@link TextNormalizer} still re-normalizes safely).
 */
public final class BotIntentLexicon {

    private BotIntentLexicon() {
    }

    /** Cancel order — checked before generic order phrases. */
    public static final String[] CANCEL_ORDER = {
            "huy don",
            "huy dat hang",
            "huy mua",
            "huy order",
            "cancel order",
            "cancel",
            "khong muon mua",
            "khong lay hang",
            "muon huy",
            "toi muon huy",
            "giup huy don",
            "dung dat hang",
            "bo don"
    };

    /**
     * Order status / tracking paraphrases — covers
     * "đơn tôi tới đâu rồi", "theo dõi đơn", "order status", etc.
     */
    public static final String[] ORDER_STATUS = {
            "kiem tra don hang",
            "kiem tra don",
            "tinh trang don",
            "trang thai don",
            "don hang cua toi",
            "don cua toi",
            "don cua em",
            "don toi",
            "theo doi don",
            "tracking",
            "track order",
            "order status",
            "my order",
            "don da giao",
            "giao chua",
            "da nhan hang chua",
            "toi don",
            "van don",
            "ma van don",
            "dang o dau",
            "don dang o",
            "don o dau",
            "toi dau roi",
            "toi dau",
            "den dau roi",
            "ship den dau",
            "hang den chua",
            "bao gio nhan hang",
            "khi nao nhan",
            "don hang o dau",
            "cho xem don",
            "xem don hang",
            "ho tro ve don"
    };

    /**
     * Loyalty / VIP — avoid ambiguous phrases like "kiem tra hang" that
     * fuzzy-match "kiem tra don hang" (order status).
     */
    public static final String[] MEMBERSHIP = {
            "khach hang than thiet",
            "the thanh vien",
            "hang thanh vien",
            "hang thanh vien cua toi",
            "thanh vien cua toi",
            "thanh vien healthup",
            // OK after ORDER_STATUS — "don hang" does not contain this phrase.
            "thanh vien",
            "hang vip",
            "len vip",
            "uu dai vip",
            "tich diem",
            "loyalty",
            "membership",
            "member tier",
            "hang cua toi",
            "toi la hang nao",
            "kiem tra hang thanh vien",
            "kiem tra hang vip",
            "xem hang thanh vien",
            "quyen loi thanh vien",
            "quyen loi vip"
    };

    public static final String[] PRODUCT_INQUIRY = {
            "toi muon hoi ve san pham",
            "hoi ve san pham",
            "can hoi ve san pham",
            "hoi san pham trong don"
    };

    public static final String[] PRODUCT_ADVICE = {
            "tu van san pham",
            "tu van dinh duong",
            "goi y san pham",
            "tu van",
            "dinh duong",
            "vitamin",
            "protein",
            "whey",
            "whey protein",
            "thuc pham",
            "bo sung",
            "goi y",
            "nen mua",
            "uong gi",
            "an gi",
            "suc khoe",
            "giam can",
            "tang can",
            "tang co",
            "bcaa",
            "omega",
            "tao mass",
            "eat clean",
            "granola",
            "loi khuyen mua",
            "danh muc san pham"
    };

    public static final String[] FAQ_MENU = {
            "cau hoi thuong gap",
            "thuong gap",
            "faq",
            "hoi dap"
    };

    public static final String[] GREETING = {
            "xin chao",
            "chao ban",
            "chao shop",
            "hello",
            "hi",
            "alo",
            "hey",
            "good morning",
            "chao buoi"
    };

    /** True if text looks like an order question but not product advice. */
    public static boolean looksLikeOrderQuestion(@NonNull String normalized) {
        boolean hasOrderWord = TextNormalizer.matchesAny(normalized, true,
                "don hang", "don cua", "order", "van don");
        boolean hasProductWord = TextNormalizer.matchesAny(normalized, false, "san pham");
        return hasOrderWord && !hasProductWord;
    }
}

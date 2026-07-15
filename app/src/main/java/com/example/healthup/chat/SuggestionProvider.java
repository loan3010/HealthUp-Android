package com.example.healthup.chat;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.example.healthup.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Rotating sets of suggested questions shown in the chat suggestion card.
 * Each set maps to questions the local {@link ChatBotEngine} can answer.
 */
public final class SuggestionProvider {

    public static final class Item {
        public final String text;
        @DrawableRes public final int iconRes;

        public Item(@NonNull String text, @DrawableRes int iconRes) {
            this.text = text;
            this.iconRes = iconRes;
        }
    }

    private static final List<List<Item>> SETS = Arrays.asList(
            Arrays.asList(
                    new Item("Kiểm tra đơn hàng", R.drawable.ic_chat_order),
                    new Item("Hủy đơn hàng", R.drawable.ic_chat_cancel),
                    new Item("Tư vấn sản phẩm & Dinh dưỡng", R.drawable.ic_chat_nutrition),
                    new Item("Câu hỏi thường gặp", R.drawable.ic_chat_help)
            ),
            Arrays.asList(
                    new Item("Thời gian giao hàng mất bao lâu?", R.drawable.ic_chat_shipping),
                    new Item("Phí vận chuyển tính như thế nào?", R.drawable.ic_chat_payment),
                    new Item("Chính sách đổi trả ra sao?", R.drawable.ic_chat_return),
                    new Item("HealthUp hỗ trợ thanh toán nào?", R.drawable.ic_chat_payment)
            ),
            Arrays.asList(
                    new Item("Đơn tôi tới đâu rồi?", R.drawable.ic_chat_order),
                    new Item("Phí ship bao nhiêu?", R.drawable.ic_chat_payment),
                    new Item("Gợi ý whey protein tăng cơ", R.drawable.ic_chat_nutrition),
                    new Item("Tôi muốn hủy đơn đặt hàng", R.drawable.ic_chat_cancel)
            )
    );

    private SuggestionProvider() {
    }

    public static int setCount() {
        return SETS.size();
    }

    @NonNull
    public static List<Item> getSet(int index) {
        int safe = Math.floorMod(index, SETS.size());
        return Collections.unmodifiableList(SETS.get(safe));
    }
}

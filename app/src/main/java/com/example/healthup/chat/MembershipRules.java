package com.example.healthup.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * HealthUp membership ranks — same rule as {@code ProfileFragment} /
 * {@code MemberTierFragment}: VIP when cumulative delivered spend ≥ {@link #VIP_THRESHOLD_VND}.
 */
public final class MembershipRules {

    /** Real app threshold (VND) used across Profile + MemberTier. */
    public static final long VIP_THRESHOLD_VND = 5_000_000L;

    public static final String TIER_MEMBER = "Member";
    public static final String TIER_VIP = "VIP";

    private MembershipRules() {
    }

    public static boolean isVip(long spentVnd) {
        return spentVnd >= VIP_THRESHOLD_VND;
    }

    @NonNull
    public static String tierKey(long spentVnd) {
        return isVip(spentVnd) ? TIER_VIP : TIER_MEMBER;
    }

    /** Display label for chat bubbles (Vietnamese). */
    @NonNull
    public static String tierLabelVi(long spentVnd) {
        return isVip(spentVnd) ? "VIP" : "Thành viên";
    }

    public static long remainingToVip(long spentVnd) {
        return Math.max(0L, VIP_THRESHOLD_VND - spentVnd);
    }

    /**
     * HTML reply for the membership chatbot intent.
     */
    @NonNull
    public static String buildStatusHtml(long spentVnd) {
        String tier = tierLabelVi(spentVnd);
        String spentFormatted = formatVnd(spentVnd);
        if (isVip(spentVnd)) {
            return "Bạn đang là khách hàng <b>" + tier + "</b> của HealthUp 🌿<br/><br/>"
                    + "Tổng chi tiêu đã tích lũy: <b>" + spentFormatted + "</b>. "
                    + "Cảm ơn bạn đã đồng hành cùng HealthUp!<br/><br/>"
                    + "Chạm nút bên dưới để xem quyền lợi và ưu đãi hạng VIP.";
        }
        long left = remainingToVip(spentVnd);
        return "Bạn đang là hạng <b>" + tier + "</b> của HealthUp 🌿<br/><br/>"
                + "Tổng chi tiêu đã tích lũy: <b>" + spentFormatted + "</b>.<br/>"
                + "Chi thêm <b>" + formatVnd(left) + "</b> để lên hạng <b>VIP</b> "
                + "(ngưỡng " + formatVnd(VIP_THRESHOLD_VND) + "). "
                + "VIP được giảm 15% mọi đơn, freeship và ưu tiên sản phẩm mới.<br/><br/>"
                + "Chạm nút bên dưới để xem Khách hàng thân thiết và quyền lợi nhé!";
    }

    @NonNull
    public static String formatVnd(long amount) {
        String raw = String.format(java.util.Locale.US, "%,d", amount);
        return raw.replace(',', '.') + "đ";
    }

    @Nullable
    public static Double numberOrNull(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }
}

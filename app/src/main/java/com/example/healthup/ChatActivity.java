package com.example.healthup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Hosts the single chat screen ({@link ChatBotFragment}). Opened by a buyer
 * (no extras) or by a seller from the inbox (seller mode + conversation id).
 */
public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_SELLER_MODE = "extra_seller_mode";
    public static final String EXTRA_CONVERSATION_ID = "extra_conversation_id";
    public static final String EXTRA_BUYER_ID = "extra_buyer_id";
    public static final String EXTRA_ORDER_ID = "extra_order_id";
    public static final String EXTRA_ORDER_CODE = "extra_order_code";
    public static final String EXTRA_PRODUCT_NAME = "extra_product_name";
    public static final String EXTRA_PRODUCT_VARIANT = "extra_product_variant";
    public static final String EXTRA_PRODUCT_ID = "extra_product_id";
    public static final String EXTRA_AUTO_SEND_INQUIRY = "extra_auto_send_inquiry";

    /** Buyer entry point. */
    public static Intent buyerIntent(Context context) {
        return new Intent(context, ChatActivity.class);
    }

    /** Open chat with order context (support section in order detail). */
    public static Intent buyerIntentForOrder(@NonNull Context context,
                                             @NonNull String orderCode,
                                             @Nullable String orderId) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(EXTRA_ORDER_CODE, orderCode);
        if (orderId != null) {
            intent.putExtra(EXTRA_ORDER_ID, orderId);
        }
        intent.putExtra(EXTRA_AUTO_SEND_INQUIRY, true);
        return intent;
    }

    /** Open chat to ask about a specific product in an order. */
    public static Intent buyerIntentForProduct(@NonNull Context context,
                                               @NonNull String orderCode,
                                               @NonNull String productName,
                                               @Nullable String variant,
                                               @Nullable String orderId) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(EXTRA_ORDER_CODE, orderCode);
        intent.putExtra(EXTRA_PRODUCT_NAME, productName);
        if (variant != null) {
            intent.putExtra(EXTRA_PRODUCT_VARIANT, variant);
        }
        if (orderId != null) {
            intent.putExtra(EXTRA_ORDER_ID, orderId);
        }
        intent.putExtra(EXTRA_AUTO_SEND_INQUIRY, true);
        return intent;
    }

    /** Open chat to ask about a product from the product detail page. */
    public static Intent buyerIntentForProductBrowse(@NonNull Context context,
                                                     @NonNull String productName,
                                                     @Nullable String variant,
                                                     @Nullable String productId) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(EXTRA_PRODUCT_NAME, productName);
        if (variant != null) {
            intent.putExtra(EXTRA_PRODUCT_VARIANT, variant);
        }
        if (productId != null) {
            intent.putExtra(EXTRA_PRODUCT_ID, productId);
        }
        intent.putExtra(EXTRA_AUTO_SEND_INQUIRY, true);
        return intent;
    }

    /** Seller entry point (reply in the same thread). */
    public static Intent sellerIntent(Context context, String conversationId, String buyerId) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(EXTRA_SELLER_MODE, true);
        intent.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        intent.putExtra(EXTRA_BUYER_ID, buyerId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        if (savedInstanceState == null) {
            boolean sellerMode = getIntent().getBooleanExtra(EXTRA_SELLER_MODE, false);
            ChatBotFragment fragment;
            if (sellerMode) {
                fragment = ChatBotFragment.newSellerInstance(
                        getIntent().getStringExtra(EXTRA_CONVERSATION_ID),
                        getIntent().getStringExtra(EXTRA_BUYER_ID));
            } else {
                fragment = ChatBotFragment.newBuyerInstance(copyBuyerExtras(getIntent().getExtras()));
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.chatContainer, fragment)
                    .commit();
        }
    }

    @Nullable
    private static Bundle copyBuyerExtras(@Nullable Bundle intentExtras) {
        if (intentExtras == null) {
            return null;
        }
        Bundle args = new Bundle();
        copyStringExtra(intentExtras, args, EXTRA_ORDER_CODE);
        copyStringExtra(intentExtras, args, EXTRA_ORDER_ID);
        copyStringExtra(intentExtras, args, EXTRA_PRODUCT_NAME);
        copyStringExtra(intentExtras, args, EXTRA_PRODUCT_VARIANT);
        copyStringExtra(intentExtras, args, EXTRA_PRODUCT_ID);
        if (intentExtras.containsKey(EXTRA_AUTO_SEND_INQUIRY)) {
            args.putBoolean(EXTRA_AUTO_SEND_INQUIRY,
                    intentExtras.getBoolean(EXTRA_AUTO_SEND_INQUIRY, false));
        }
        return args;
    }

    private static void copyStringExtra(@NonNull Bundle from, @NonNull Bundle to, @NonNull String key) {
        if (from.containsKey(key)) {
            to.putString(key, from.getString(key));
        }
    }
}

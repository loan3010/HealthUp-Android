package com.example.healthup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

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

    /** Buyer entry point. */
    public static Intent buyerIntent(Context context) {
        return new Intent(context, ChatActivity.class);
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
                fragment = ChatBotFragment.newBuyerInstance();
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.chatContainer, fragment)
                    .commit();
        }
    }
}

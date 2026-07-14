package com.example.healthup;

import android.os.Bundle;

import androidx.annotation.Nullable;

    /** Hosts the seller/admin inbox ({@link ChatStaffFragment}). */
public class SellerChatListActivity extends BaseAppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seller_chat_list);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.sellerChatListContainer, new ChatStaffFragment())
                    .commit();
        }
    }
}

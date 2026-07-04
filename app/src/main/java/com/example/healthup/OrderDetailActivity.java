package com.example.healthup;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class OrderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "extra_order_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        TextView detailText = findViewById(R.id.orderDetailText);
        String orderId = getIntent() != null ? getIntent().getStringExtra(EXTRA_ORDER_ID) : null;
        if (!TextUtils.isEmpty(orderId)) {
            detailText.setText(getString(R.string.order_detail_with_id, orderId));
        }
    }
}

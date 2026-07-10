package com.example.healthup.admin;

import androidx.annotation.Nullable;

public interface AdminNavigator {

    void openOrders(@Nullable String statusFilter);

    void openProducts(@Nullable String productFilter);
}

package com.group.healthup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class OrderPagerAdapter extends FragmentStateAdapter {

    public OrderPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    public OrderPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        String filter;
        switch (position) {
            case 0: filter = "all"; break;
            case 1: filter = "pending"; break;
            case 2: filter = "confirmed"; break;
            case 3: filter = "shipping"; break;
            case 4: filter = "delivered"; break;
            case 5: filter = "returned"; break;
            case 6: filter = "cancelled"; break;
            default: filter = "all"; break;
        }
        // Gọi thẳng newInstance với key STATUS_KEY mới
        return OrderListFragment.newInstance(filter);
    }

    @Override
    public int getItemCount() {
        return 7;
    }
}

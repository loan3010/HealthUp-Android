package com.group.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        initViews(view);
        return view;
    }

    private void initViews(View view) {
        // Bind menu items
        setupMenuItem(view.findViewById(R.id.menu_orders), R.drawable.ic_cart, "Lịch sử mua hàng", () -> {
            loadFragment(new OrderHistoryFragment());
        });

        setupMenuItem(view.findViewById(R.id.menu_wishlist), R.drawable.ic_heart_outline, "Danh sách yêu thích", () -> {
            loadFragment(new WishlistFragment());
        });

        setupMenuItem(view.findViewById(R.id.menu_faqs), R.drawable.ic_notifications, "Trung tâm trợ giúp (FAQs)", () -> {
            loadFragment(new FAQFragment());
        });

        setupMenuItem(view.findViewById(R.id.menu_blog), R.drawable.ic_category, "Blog Sức Khỏe", () -> {
            loadFragment(new BlogFragment());
        });

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            // Logout logic
            if (getActivity() != null) {
                getActivity().finish();
            }
        });
    }

    private void setupMenuItem(View view, int iconRes, String title, Runnable action) {
        ImageView ivIcon = view.findViewById(R.id.iv_menu_icon);
        TextView tvTitle = view.findViewById(R.id.tv_menu_title);
        
        ivIcon.setImageResource(iconRes);
        tvTitle.setText(title);
        
        view.setOnClickListener(v -> action.run());
    }

    private void loadFragment(Fragment fragment) {
        if (getActivity() instanceof MainActivity) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    }
}

package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.healthup.databinding.FragmentOrderHistoryBinding;
import com.google.android.material.tabs.TabLayoutMediator;

public class OrderHistoryFragment extends Fragment {
    private FragmentOrderHistoryBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentOrderHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        OrderPagerAdapter adapter = new OrderPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);
        binding.viewPager.setOffscreenPageLimit(7); // Giữ tất cả tab trong bộ nhớ

        String[] tabs = {"Tất cả", "Chờ xác nhận", "Chờ lấy hàng", "Chờ giao hàng", "Đã giao", "Trả hàng", "Đã hủy"};

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            tab.setText(tabs[position]);
        }).attach();

        // Xử lý chuyển đến tab cụ thể nếu được yêu cầu
        if (getArguments() != null) {
            int initialTab = getArguments().getInt("initial_tab", -1);
            if (initialTab != -1) {
                binding.viewPager.post(() -> binding.viewPager.setCurrentItem(initialTab, false));
            }
        }

        binding.btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        binding.btnSearch.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(getContext(), OrderSearchActivity.class);
            startActivity(intent);
        });

        binding.btnChatBot.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ChatBotFragment())
                    .addToBackStack(null)
                    .commit();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

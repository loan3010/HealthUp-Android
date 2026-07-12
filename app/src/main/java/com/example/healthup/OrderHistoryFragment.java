package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.healthup.databinding.FragmentOrderHistoryBinding;
import com.example.healthup.util.GuestLoginRequiredHelper;
import com.example.healthup.util.GuestRecommendationsHelper;
import com.example.healthup.util.UtilityHeaderHelper;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;

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

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            setupGuestMode(view);
        } else {
            setupLoggedInMode();
        }
    }

    private void setupGuestMode(View view) {
        UtilityHeaderHelper.bind(view, this, "Đơn đã mua");
        binding.scrollGuestOrders.setVisibility(View.VISIBLE);
        binding.layoutLoggedInOrders.setVisibility(View.GONE);
        GuestLoginRequiredHelper.bind(view, this);
        GuestRecommendationsHelper.bind(view, this);
    }

    private void setupLoggedInMode() {
        UtilityHeaderHelper.bind(binding.getRoot(), this, "Đơn đã mua");
        binding.scrollGuestOrders.setVisibility(View.GONE);
        binding.layoutLoggedInOrders.setVisibility(View.VISIBLE);

        OrderPagerAdapter adapter = new OrderPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);
        binding.viewPager.setOffscreenPageLimit(7);

        String[] tabs = {"Tất cả", "Chờ xác nhận", "Chờ lấy hàng", "Chờ giao hàng", "Đã giao", "Trả hàng", "Đã hủy"};

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) ->
                tab.setText(tabs[position])).attach();

        if (getArguments() != null) {
            int initialTab = getArguments().getInt("initial_tab", -1);
            if (initialTab != -1) {
                binding.viewPager.post(() -> binding.viewPager.setCurrentItem(initialTab, false));
            }
        }

        binding.btnSearch.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), OrderSearchActivity.class)));

        binding.btnChatBot.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new ChatBotFragment())
                        .addToBackStack(null)
                        .commit());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

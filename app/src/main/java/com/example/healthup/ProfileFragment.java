package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.healthup.databinding.FragmentProfileBinding;


public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        binding.btnSettings.setOnClickListener(v -> {
            loadFragment(new SettingsFragment());
        });


        binding.cardWishlist.setOnClickListener(v -> {
            loadFragment(new WishlistFragment());
        });


        binding.cardOrderHistory.setOnClickListener(v -> {
            loadFragment(new OrderHistoryFragment());
        });


        binding.cardRefund.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), ReturnRefundActivity.class));
        });


        // FIX: gắn điều hướng cho card "Câu hỏi thường gặp" -> mở FAQFragment
        // (FAQFragment đã code sẵn đầy đủ: fetch Firestore collection "faqs",
        // search, filter theo category — chỉ cần nối vào đây)
        binding.cardFAQ.setOnClickListener(v -> {
            loadFragment(new FAQFragment());
        });
    }


    private void loadFragment(Fragment fragment) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
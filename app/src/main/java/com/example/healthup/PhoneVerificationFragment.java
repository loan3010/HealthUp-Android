package com.example.healthup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.PhoneNormalizer;
import com.example.healthup.util.UserPhoneLookup;

public class PhoneVerificationFragment extends Fragment {

    public static final String ARG_RETURN_TO_PREVIOUS = "return_to_previous";

    private EditText etPhone;
    private TextView tvErrorPhone, tvStatusTitle, tvStatusDesc, tvTitle, tvDescription, tvPrefix, tvBackToShopping;
    private View layoutStatus, layoutBottomHint, tvChangePhone, layoutInputPhone;
    private Button btnContinue;

    private boolean isChecking = false;
    private boolean phoneExists = false;
    private boolean returnToPrevious = false;
    private String normalizedPhone = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            returnToPrevious = args.getBoolean(ARG_RETURN_TO_PREVIOUS, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_phone_verification, container, false);
        bindViews(view);
        setupListeners();
        return view;
    }

    private void bindViews(View view) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> navigateBack());

        tvTitle = view.findViewById(R.id.tvTitle);
        tvDescription = view.findViewById(R.id.tvDescription);
        etPhone = view.findViewById(R.id.etPhone);
        tvErrorPhone = view.findViewById(R.id.tvErrorPhone);
        tvPrefix = view.findViewById(R.id.tvPrefix);

        layoutInputPhone = view.findViewById(R.id.layoutInputPhone);
        layoutStatus = view.findViewById(R.id.layoutStatus);
        tvStatusTitle = view.findViewById(R.id.tvStatusTitle);
        tvStatusDesc = view.findViewById(R.id.tvStatusDesc);

        btnContinue = view.findViewById(R.id.btnContinue);
        tvChangePhone = view.findViewById(R.id.tvChangePhone);
        layoutBottomHint = view.findViewById(R.id.layoutBottomHint);
        tvBackToShopping = view.findViewById(R.id.tvBackToShopping);

        boolean hasPendingCheckout = CheckoutIntentHelper.hasPendingCheckout(requireContext());
        if (returnToPrevious) {
            tvBackToShopping.setText(R.string.phone_verification_back_to_chat);
        } else {
            tvBackToShopping.setText(hasPendingCheckout
                    ? R.string.phone_verification_back_to_cart
                    : R.string.phone_verification_continue_shopping);
        }
    }

    private void setupListeners() {
        tvBackToShopping.setOnClickListener(v -> navigateBack());

        etPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isChecking) {
                    resetUI();
                    return;
                }
                resetUI();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        tvChangePhone.setOnClickListener(v -> resetUI());

        btnContinue.setOnClickListener(v -> {
            if (!isChecking) {
                String phone = etPhone.getText().toString().trim();
                if (!PhoneNormalizer.isValidLocalPhone(phone)) {
                    showError();
                    return;
                }
                checkPhoneNumber(PhoneNormalizer.normalize(phone));
                return;
            }

            if (phoneExists) {
                navigateToLogin();
            } else {
                navigateToRegister();
            }
        });
    }

    private void navigateToLogin() {
        boolean returnToCheckout = CheckoutIntentHelper.hasPendingCheckout(requireContext());
        String phone = PhoneNormalizer.normalize(normalizedPhone);
        startActivity(CheckoutIntentHelper.buildLoginIntent(requireContext(), phone, returnToCheckout));
    }

    private void navigateToRegister() {
        boolean returnToCheckout = CheckoutIntentHelper.hasPendingCheckout(requireContext());
        String phone = PhoneNormalizer.normalize(normalizedPhone);
        startActivity(CheckoutIntentHelper.buildRegisterIntent(requireContext(), phone, returnToCheckout));
    }

    private void navigateBack() {
        if (returnToPrevious) {
            requireActivity().finish();
            return;
        }

        FragmentManager fm = requireActivity().getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
            return;
        }

        if (requireActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) requireActivity();
            if (CheckoutIntentHelper.hasPendingCheckout(requireContext())) {
                mainActivity.showCartTab();
            } else {
                mainActivity.showHomeTab();
            }
            return;
        }

        requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }

    private void resetUI() {
        layoutInputPhone.setBackgroundResource(R.drawable.bg_input_normal);
        tvErrorPhone.setVisibility(View.GONE);
        layoutStatus.setVisibility(View.GONE);
        tvChangePhone.setVisibility(View.GONE);
        layoutBottomHint.setVisibility(View.VISIBLE);
        btnContinue.setText("Tiếp tục");
        isChecking = false;
        phoneExists = false;
        normalizedPhone = "";
        tvPrefix.setVisibility(View.GONE);
        etPhone.setEnabled(true);
        tvTitle.setText("Xác minh số điện thoại");
        tvDescription.setVisibility(View.VISIBLE);
    }

    private void showError() {
        layoutInputPhone.setBackgroundResource(R.drawable.bg_input_error);
        tvErrorPhone.setVisibility(View.VISIBLE);
    }

    private void checkPhoneNumber(String phone) {
        normalizedPhone = phone;
        btnContinue.setEnabled(false);
        btnContinue.setText("Đang kiểm tra...");

        UserPhoneLookup.checkExists(phone, new UserPhoneLookup.Callback() {
            @Override
            public void onResult(boolean exists) {
                if (!isAdded()) {
                    return;
                }
                btnContinue.setEnabled(true);
                isChecking = true;
                phoneExists = exists;
                showVerificationResult(phone, exists);
            }

            @Override
            public void onError(Exception error) {
                if (!isAdded()) {
                    return;
                }
                btnContinue.setEnabled(true);
                btnContinue.setText("Tiếp tục");
                Toast.makeText(getContext(), "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showVerificationResult(String phone, boolean exists) {
        tvTitle.setText("Xác minh");
        tvDescription.setVisibility(View.GONE);
        tvPrefix.setVisibility(View.VISIBLE);
        tvPrefix.setText("+84");
        etPhone.setText(phone.startsWith("0") ? phone.substring(1) : phone);
        etPhone.setEnabled(true);
        etPhone.requestFocus();

        layoutStatus.setVisibility(View.VISIBLE);
        layoutBottomHint.setVisibility(View.GONE);
        tvChangePhone.setVisibility(View.VISIBLE);

        if (exists) {
            tvStatusTitle.setText("Tài khoản đã tồn tại.");
            tvStatusTitle.setTextColor(getResources().getColor(R.color.green_primary));
            tvStatusDesc.setText("Số điện thoại đã được đăng ký. Vui lòng đăng nhập để tiếp tục mua hàng với các ưu đãi dành riêng cho thành viên.");
            btnContinue.setText(R.string.phone_verification_login);
        } else {
            tvStatusTitle.setText("Số điện thoại này chưa được đăng ký.");
            tvStatusTitle.setTextColor(getResources().getColor(R.color.green_primary));
            tvStatusDesc.setText("Vui lòng đăng ký tài khoản để tiếp tục mua hàng và nhận ưu đãi dành riêng cho bạn.");
            btnContinue.setText(R.string.phone_verification_register);
        }
    }
}

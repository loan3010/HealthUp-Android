package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapters.VoucherAdapter;
import com.example.models.Voucher;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PromoCouponFragment extends Fragment {

    private ImageButton btnBack;
    private EditText etVoucherCode;
    private Button btnApply, btnConfirm;
    private RecyclerView rvVouchers, rvVouchersDiscount;
    private TextView tvTotalDiscount;

    private VoucherAdapter adapterShipping, adapterDiscount;
    private List<Voucher> shippingList, discountList;
    
    private List<Voucher> previouslySelected = new ArrayList<>();
    private boolean hasVisited = false;

    public PromoCouponFragment() {
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_promo_coupon, container, false);

        if (getArguments() != null) {
            Serializable data = getArguments().getSerializable("selected_vouchers");
            if (data instanceof List) {
                previouslySelected = (List<Voucher>) data;
            }
            hasVisited = getArguments().getBoolean("has_visited", false);
        }

        initViews(view);
        setupRecyclerViews();
        loadVoucherData();

        btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        btnApply.setOnClickListener(v -> {
            String code = etVoucherCode.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(getContext(), "Vui lòng nhập mã voucher", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Mã " + code + " không hợp lệ hoặc đã hết hạn", Toast.LENGTH_SHORT).show();
            }
        });

        btnConfirm.setOnClickListener(v -> confirmSelection());

        return view;
    }

    private void initViews(View view) {
        btnBack = view.findViewById(R.id.btnBack);
        etVoucherCode = view.findViewById(R.id.etVoucherCode);
        btnApply = view.findViewById(R.id.btnApply);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        rvVouchers = view.findViewById(R.id.rvVouchers);
        rvVouchersDiscount = view.findViewById(R.id.rvVouchersDiscount);
        tvTotalDiscount = view.findViewById(R.id.tvTotalDiscount);
    }

    private void setupRecyclerViews() {
        shippingList = new ArrayList<>();
        adapterShipping = new VoucherAdapter(requireContext(), shippingList, voucher -> {
            if (voucher.isSelected()) {
                for (Voucher v : shippingList) if (v != voucher) v.setSelected(false);
                adapterShipping.notifyDataSetChanged();
            }
            updateTotalDiscount();
        });
        rvVouchers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVouchers.setAdapter(adapterShipping);

        discountList = new ArrayList<>();
        adapterDiscount = new VoucherAdapter(requireContext(), discountList, voucher -> {
            if (voucher.isSelected()) {
                for (Voucher v : discountList) if (v != voucher) v.setSelected(false);
                adapterDiscount.notifyDataSetChanged();
            }
            updateTotalDiscount();
        });
        rvVouchersDiscount.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVouchersDiscount.setAdapter(adapterDiscount);
    }

    private void loadVoucherData() {
        // Dữ liệu mẫu
        shippingList.add(new Voucher("v1", "Miễn phí vận chuyển", "Đơn tối thiểu 100k", 21000, "30.06.2024", Voucher.Type.SHIPPING));
        
        discountList.add(new Voucher("v2", "Giảm trực tiếp 30k", "Đơn tối thiểu 500k", 30000, "15.07.2024", Voucher.Type.CASHBACK));
        discountList.add(new Voucher("v3", "Giảm 10% đơn hàng", "Tối đa 50k cho đơn từ 300k", 21000, "20.06.2024", Voucher.Type.DISCOUNT));

        // Logic đồng bộ:
        if (hasVisited) {
            // Nếu đã từng vào, chỉ chọn những gì truyền sang (giữ nguyên cả trạng thái rỗng nếu người dùng bỏ hết)
            for (Voucher prev : previouslySelected) {
                for (Voucher cur : shippingList) if (cur.getId().equals(prev.getId())) cur.setSelected(true);
                for (Voucher cur : discountList) if (cur.getId().equals(prev.getId())) cur.setSelected(true);
            }
        } else {
            // Nếu là lần đầu tiên hoàn toàn, tự động chọn hộ
            if (!shippingList.isEmpty()) shippingList.get(0).setSelected(true);
            if (!discountList.isEmpty()) discountList.get(0).setSelected(true);
        }

        adapterShipping.notifyDataSetChanged();
        adapterDiscount.notifyDataSetChanged();
        updateTotalDiscount();
    }

    private void updateTotalDiscount() {
        double total = 0;
        for (Voucher v : shippingList) if (v.isSelected()) total += v.getDiscountAmount();
        for (Voucher v : discountList) if (v.isSelected()) total += v.getDiscountAmount();
        tvTotalDiscount.setText(String.format(Locale.getDefault(), "Tiết kiệm %,.0fđ", total).replace(",", "."));
    }

    private void confirmSelection() {
        ArrayList<Voucher> resultList = new ArrayList<>();
        for (Voucher v : shippingList) if (v.isSelected()) resultList.add(v);
        for (Voucher v : discountList) if (v.isSelected()) resultList.add(v);

        Bundle result = new Bundle();
        result.putSerializable("selected_vouchers", resultList);
        getParentFragmentManager().setFragmentResult("voucher_result", result);

        requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }
}

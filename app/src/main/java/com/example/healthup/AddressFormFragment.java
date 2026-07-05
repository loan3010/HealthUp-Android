package com.example.healthup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.models.Address;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class AddressFormFragment extends Fragment {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^(0|\\+84)\\d{9,10}$");

    private EditText etFullName, etPhone, etDetailAddress;
    private TextView tvErrorName, tvErrorPhone, tvErrorProvince, tvErrorDistrict, tvErrorWard, tvErrorDetailAddress;
    private Spinner spinnerProvince, spinnerDistrict, spinnerWard;
    private TextView btnTypeHome, btnTypeOffice, tvTitle;
    private SwitchCompat switchDefault;
    private Button btnSubmit;

    private Address editingAddress;
    private String selectedType = Address.TYPE_HOME;
    private boolean submitAttempted = false;

    private FirebaseFirestore db;
    private String userId;

    public static AddressFormFragment newInstance(@Nullable Address addressToEdit) {
        AddressFormFragment fragment = new AddressFormFragment();
        Bundle args = new Bundle();
        if (addressToEdit != null) {
            args.putSerializable("edit_address", addressToEdit);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_address_form, container, false);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();

        if (getArguments() != null) {
            editingAddress = (Address) getArguments().getSerializable("edit_address");
        }

        LocationLoader.load(requireContext());

        bindViews(view);
        initSpinnerAdapters();
        setupSpinnerListeners();
        setupListeners();

        if (editingAddress != null) {
            tvTitle.setText("Chỉnh sửa thông tin");
            btnSubmit.setText("Lưu những thay đổi");
            prefillFromAddress(editingAddress);
        } else {
            tvTitle.setText("Thêm địa chỉ mới");
            btnSubmit.setText("Hoàn thành");
            selectType(Address.TYPE_HOME);
        }

        updateSubmitButtonState();
        return view;
    }

    private void bindViews(View view) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        tvTitle = view.findViewById(R.id.tvTitle);
        etFullName = view.findViewById(R.id.etFullName);
        etPhone = view.findViewById(R.id.etPhone);
        etDetailAddress = view.findViewById(R.id.etDetailAddress);
        
        tvErrorName = view.findViewById(R.id.tvErrorName);
        tvErrorPhone = view.findViewById(R.id.tvErrorPhone);
        tvErrorProvince = view.findViewById(R.id.tvErrorProvince);
        tvErrorDistrict = view.findViewById(R.id.tvErrorDistrict);
        tvErrorWard = view.findViewById(R.id.tvErrorWard);
        tvErrorDetailAddress = view.findViewById(R.id.tvErrorDetailAddress);
        
        spinnerProvince = view.findViewById(R.id.spinnerProvince);
        spinnerDistrict = view.findViewById(R.id.spinnerDistrict);
        spinnerWard = view.findViewById(R.id.spinnerWard);
        
        btnTypeHome = view.findViewById(R.id.btnTypeHome);
        btnTypeOffice = view.findViewById(R.id.btnTypeOffice);
        switchDefault = view.findViewById(R.id.switchDefault);
        btnSubmit = view.findViewById(R.id.btnSubmit);
    }

    private void initSpinnerAdapters() {
        // Province
        ArrayAdapter<String> provinceAdapter = new ArrayAdapter<>(requireContext(),
                R.layout.spinner_item, LocationLoader.getProvinceNames());
        provinceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerProvince.setAdapter(provinceAdapter);

        // Initial empty adapters for District and Ward
        updateDistrictSpinner(null);
        updateWardSpinner(null);
    }

    private void setupSpinnerListeners() {
        spinnerProvince.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                String province = position > 0 ? (String) parent.getItemAtPosition(position) : null;
                updateDistrictSpinner(province);
                updateSubmitButtonState();
                if (position > 0) tvErrorProvince.setVisibility(View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerDistrict.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                String district = position > 0 ? (String) parent.getItemAtPosition(position) : null;
                updateWardSpinner(district);
                updateSubmitButtonState();
                if (position > 0) tvErrorDistrict.setVisibility(View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerWard.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                updateSubmitButtonState();
                if (position > 0) tvErrorWard.setVisibility(View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateDistrictSpinner(@Nullable String province) {
        List<String> districts = LocationLoader.getDistricts(province);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.spinner_item, districts);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerDistrict.setAdapter(adapter);
        updateWardSpinner(null);
    }

    private void updateWardSpinner(@Nullable String district) {
        List<String> wards = LocationLoader.getWards(district);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.spinner_item, wards);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerWard.setAdapter(adapter);
    }

    private void setupListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Gọi ngay khi đang gõ
                updateSubmitButtonState();
            }
            @Override public void afterTextChanged(Editable s) {
                if (etFullName.hasFocus()) {
                    tvErrorName.setVisibility(View.GONE);
                    etFullName.setBackgroundResource(R.drawable.bg_input_normal);
                }
                if (etPhone.hasFocus()) {
                    tvErrorPhone.setVisibility(View.GONE);
                    etPhone.setBackgroundResource(R.drawable.bg_input_normal);
                }
                if (etDetailAddress.hasFocus()) {
                    tvErrorDetailAddress.setVisibility(View.GONE);
                    etDetailAddress.setBackgroundResource(R.drawable.bg_input_normal);
                }
            }
        };
        etFullName.addTextChangedListener(watcher);
        etPhone.addTextChangedListener(watcher);
        etDetailAddress.addTextChangedListener(watcher);

        btnTypeHome.setOnClickListener(v -> selectType(Address.TYPE_HOME));
        btnTypeOffice.setOnClickListener(v -> selectType(Address.TYPE_OFFICE));

        btnSubmit.setOnClickListener(v -> {
            submitAttempted = true;
            if (validate()) {
                saveAndReturn();
            }
        });
    }

    private void selectType(String type) {
        selectedType = type;
        boolean isHome = Address.TYPE_HOME.equals(type);
        btnTypeHome.setBackgroundResource(isHome ? R.drawable.bg_type_option_selected : R.drawable.bg_type_option_unselected);
        btnTypeHome.setTextColor(ContextCompat.getColor(requireContext(), isHome ? R.color.white : R.color.text_dark));
        btnTypeOffice.setBackgroundResource(!isHome ? R.drawable.bg_type_option_selected : R.drawable.bg_type_option_unselected);
        btnTypeOffice.setTextColor(ContextCompat.getColor(requireContext(), !isHome ? R.color.white : R.color.text_dark));
    }

    private void prefillFromAddress(Address address) {
        etFullName.setText(address.getRecipientName());
        etPhone.setText(address.getPhone());
        etDetailAddress.setText(address.getDetailAddress());
        switchDefault.setChecked(address.isDefault());
        selectType(address.getType());

        // Gỡ listener để tránh reset tự động khi setSelection
        spinnerProvince.setOnItemSelectedListener(null);
        spinnerDistrict.setOnItemSelectedListener(null);
        spinnerWard.setOnItemSelectedListener(null);

        // 1. Set Province
        List<String> provinces = LocationLoader.getProvinceNames();
        int pIndex = provinces.indexOf(address.getProvince());
        if (pIndex >= 0) {
            spinnerProvince.setSelection(pIndex);

            // 2. Load và Set District (làm ngay lập tức thay vì dùng post để đồng bộ)
            updateDistrictSpinner(address.getProvince());
            List<String> districts = LocationLoader.getDistricts(address.getProvince());
            int dIndex = districts.indexOf(address.getDistrict());
            if (dIndex >= 0) {
                spinnerDistrict.setSelection(dIndex);

                // 3. Load và Set Ward
                updateWardSpinner(address.getDistrict());
                List<String> wards = LocationLoader.getWards(address.getDistrict());
                int wIndex = wards.indexOf(address.getWard());
                if (wIndex >= 0) {
                    spinnerWard.setSelection(wIndex);
                }
            }
        }

        // Cập nhật trạng thái nút bấm ngay sau khi prefill
        updateSubmitButtonState();

        // Cần dùng post để đảm bảo các Spinner đã dựng UI xong trước khi gán lại Listener
        // Điều này tránh việc sự kiện "auto selection" của Android làm loạn dữ liệu
        if (getView() != null) {
            getView().post(() -> {
                if (isAdded()) {
                    setupSpinnerListeners();
                }
            });
        }
    }

    private void updateSubmitButtonState() {
        String name = etFullName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String detail = etDetailAddress.getText().toString().trim();
        
        boolean nameOk = !name.isEmpty();
        boolean phoneOk = PHONE_PATTERN.matcher(phone).matches();
        boolean provinceOk = spinnerProvince.getSelectedItemPosition() > 0;
        boolean districtOk = spinnerDistrict.getSelectedItemPosition() > 0;
        boolean wardOk = spinnerWard.getSelectedItemPosition() > 0;
        boolean detailOk = !detail.isEmpty();
        
        boolean isValid = nameOk && phoneOk && provinceOk && districtOk && wardOk && detailOk;
                
        btnSubmit.setEnabled(isValid);
        
        // Log để debug (có thể xem trong Logcat)
        android.util.Log.d("AddressForm", "nameOk: " + nameOk + 
            ", phoneOk: " + phoneOk + ", provinceOk: " + provinceOk + 
            ", districtOk: " + districtOk + ", wardOk: " + wardOk + 
            ", detailOk: " + detailOk);
    }

    private boolean validate() {
        boolean valid = true;
        if (etFullName.getText().toString().trim().isEmpty()) {
            showError(etFullName, tvErrorName, "Vui lòng nhập họ và tên");
            valid = false;
        } else clearError(etFullName, tvErrorName);

        String phone = etPhone.getText().toString().trim();
        if (phone.isEmpty()) {
            showError(etPhone, tvErrorPhone, "Vui lòng nhập số điện thoại");
            valid = false;
        } else if (!PHONE_PATTERN.matcher(phone).matches()) {
            showError(etPhone, tvErrorPhone, "Số điện thoại không hợp lệ");
            valid = false;
        } else clearError(etPhone, tvErrorPhone);

        if (spinnerProvince.getSelectedItemPosition() <= 0) {
            tvErrorProvince.setVisibility(View.VISIBLE);
            valid = false;
        } else tvErrorProvince.setVisibility(View.GONE);

        if (spinnerDistrict.getSelectedItemPosition() <= 0) {
            tvErrorDistrict.setVisibility(View.VISIBLE);
            valid = false;
        } else tvErrorDistrict.setVisibility(View.GONE);

        if (spinnerWard.getSelectedItemPosition() <= 0) {
            tvErrorWard.setVisibility(View.VISIBLE);
            valid = false;
        } else tvErrorWard.setVisibility(View.GONE);

        if (etDetailAddress.getText().toString().trim().isEmpty()) {
            showError(etDetailAddress, tvErrorDetailAddress, "Vui lòng nhập địa chỉ cụ thể");
            valid = false;
        } else clearError(etDetailAddress, tvErrorDetailAddress);

        return valid;
    }

    private void showError(EditText et, TextView tv, String msg) {
        et.setBackgroundResource(R.drawable.bg_input_error);
        tv.setText(msg);
        tv.setVisibility(View.VISIBLE);
    }

    private void clearError(EditText et, TextView tv) {
        et.setBackgroundResource(R.drawable.bg_input_normal);
        tv.setVisibility(View.GONE);
    }

    private void saveAndReturn() {
        // Nếu không có userId (chưa đăng nhập), dùng ID tạm là "guest_user" để vẫn cho phép lưu và chọn địa chỉ
        final String effectiveUserId = (userId != null) ? userId : "guest_user";

        String addressId = editingAddress != null ? editingAddress.getId() : db.collection("users").document(effectiveUserId).collection("addresses").document().getId();

        Address address = new Address(
                addressId,
                etFullName.getText().toString().trim(),
                etPhone.getText().toString().trim(),
                (String) spinnerProvince.getSelectedItem(),
                (String) spinnerDistrict.getSelectedItem(),
                (String) spinnerWard.getSelectedItem(),
                etDetailAddress.getText().toString().trim(),
                selectedType,
                switchDefault.isChecked()
        );

        btnSubmit.setEnabled(false); // Ngăn bấm nhiều lần
        btnSubmit.setText("Đang lưu...");

        WriteBatch batch = db.batch();

        // Nếu đặt làm mặc định, phải bỏ mặc định của tất cả địa chỉ khác
        if (address.isDefault()) {
            db.collection("users").document(effectiveUserId).collection("addresses")
                    .whereEqualTo("default", true)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                            if (!doc.getId().equals(addressId)) {
                                batch.update(doc.getReference(), "default", false);
                            }
                        }
                        performSave(batch, address, effectiveUserId);
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("AddressForm", "Error checking default: ", e);
                        performSave(batch, address, effectiveUserId);
                    }); 
        } else {
            performSave(batch, address, effectiveUserId);
        }
    }

    private void performSave(WriteBatch batch, Address address, String effectiveUserId) {
        batch.set(db.collection("users").document(effectiveUserId).collection("addresses").document(address.getId()), address);
        batch.commit().addOnSuccessListener(aVoid -> {
            if (isAdded()) {
                Toast.makeText(getContext(), "Đã lưu địa chỉ thành công", Toast.LENGTH_SHORT).show();
                Bundle result = new Bundle();
                result.putSerializable("saved_address", address);
                getParentFragmentManager().setFragmentResult("address_form_result", result);
                getParentFragmentManager().popBackStack();
            }
        }).addOnFailureListener(e -> {
            if (isAdded()) {
                btnSubmit.setEnabled(true);
                btnSubmit.setText("Hoàn thành");
                Toast.makeText(getContext(), "Lỗi khi lưu lên Firebase: " + e.getMessage(), Toast.LENGTH_LONG).show();
                android.util.Log.e("AddressForm", "Firestore save error", e);
            }
        });
    }
}

package com.example.healthup;


import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
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


import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


public class AddressFormFragment extends Fragment {


    private static final Pattern PHONE_PATTERN = Pattern.compile("^(0|\\+84)\\d{9,10}$");


    private EditText etFullName, etPhone, etDetailAddress;
    private TextView tvErrorName, tvErrorPhone, tvErrorProvince, tvErrorWard, tvErrorDetailAddress;
    private AutoCompleteTextView autoProvince, autoWard;
    private TextView btnTypeHome, btnTypeOffice, tvTitle;
    private SwitchCompat switchDefault;
    private Button btnSubmit, btnDelete;


    private Address editingAddress;
    private String selectedType = Address.TYPE_HOME;


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
        setupLocationAdapters();
        setupListeners(view);


        if (editingAddress != null) {
            tvTitle.setText("Chỉnh sửa thông tin");
            btnSubmit.setText("Lưu những thay đổi");
            btnDelete.setVisibility(View.VISIBLE);
            prefillFromAddress(editingAddress);
        } else {
            tvTitle.setText("Thêm địa chỉ mới");
            btnSubmit.setText("Thêm địa chỉ mới");
            btnDelete.setVisibility(View.GONE);
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
        tvErrorWard = view.findViewById(R.id.tvErrorWard);
        tvErrorDetailAddress = view.findViewById(R.id.tvErrorDetailAddress);

        autoProvince = view.findViewById(R.id.spinnerProvince);
        autoWard = view.findViewById(R.id.spinnerWard);

        btnTypeHome = view.findViewById(R.id.btnTypeHome);
        btnTypeOffice = view.findViewById(R.id.btnTypeOffice);
        switchDefault = view.findViewById(R.id.switchDefault);
        btnSubmit = view.findViewById(R.id.btnSubmit);
        btnDelete = view.findViewById(R.id.btnDelete);


        view.findViewById(R.id.scrollView).setOnTouchListener((v, event) -> {
            UIUtils.hideKeyboard(getActivity());
            return false;
        });
    }


    private void setupLocationAdapters() {
        List<String> provinceNames = new ArrayList<>(LocationLoader.getProvinceNames());
        if (!provinceNames.isEmpty() && provinceNames.get(0).startsWith("Chọn")) {
            provinceNames.remove(0);
        }

        ArrayAdapter<String> provinceAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, provinceNames);
        autoProvince.setAdapter(provinceAdapter);


        autoProvince.setOnItemClickListener((parent, view, position, id) -> {
            String province = (String) parent.getItemAtPosition(position);
            updateWardAdapter(province);
            autoWard.setText("");
            updateSubmitButtonState();
            tvErrorProvince.setVisibility(View.GONE);
        });


        autoProvince.setOnClickListener(v -> autoProvince.showDropDown());
        autoProvince.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && autoProvince.getAdapter() != null) {
                autoProvince.showDropDown();
            }
        });


        autoWard.setOnClickListener(v -> autoWard.showDropDown());
        autoWard.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && autoWard.getAdapter() != null) {
                autoWard.showDropDown();
            }
        });
    }


    private void updateWardAdapter(@Nullable String province) {
        List<String> wards = new ArrayList<>(LocationLoader.getWards(province));
        if (!wards.isEmpty() && wards.get(0).startsWith("Chọn")) {
            wards.remove(0);
        }

        ArrayAdapter<String> wardAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, wards);
        autoWard.setAdapter(wardAdapter);

        autoWard.setOnItemClickListener((parent, view, position, id) -> {
            updateSubmitButtonState();
            tvErrorWard.setVisibility(View.GONE);
        });
    }


    private void setupListeners(View view) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> getParentFragmentManager().popBackStack());


        view.findViewById(R.id.scrollView).setOnTouchListener((v, event) -> {
            Activity activity = getActivity();
            if (activity != null) {
                UIUtils.hideKeyboard(activity);
            }
            return false;
        });


        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSubmitButtonState();
            }
            @Override public void afterTextChanged(Editable s) {
                if (etFullName.hasFocus()) clearError(etFullName, tvErrorName);
                if (etPhone.hasFocus()) clearError(etPhone, tvErrorPhone);
                if (etDetailAddress.hasFocus()) clearError(etDetailAddress, tvErrorDetailAddress);
            }
        };
        etFullName.addTextChangedListener(watcher);
        etPhone.addTextChangedListener(watcher);
        etDetailAddress.addTextChangedListener(watcher);
        autoProvince.addTextChangedListener(watcher);
        autoWard.addTextChangedListener(watcher);


        btnTypeHome.setOnClickListener(v -> selectType(Address.TYPE_HOME));
        btnTypeOffice.setOnClickListener(v -> selectType(Address.TYPE_OFFICE));


        btnDelete.setOnClickListener(v -> deleteAddress());


        btnSubmit.setOnClickListener(v -> {
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


        autoProvince.setText(address.getProvince(), false);
        updateWardAdapter(address.getProvince());
        autoWard.setText(address.getWard(), false);


        updateSubmitButtonState();
    }


    private void updateSubmitButtonState() {
        String name = etFullName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String province = autoProvince.getText().toString().trim();
        String ward = autoWard.getText().toString().trim();
        String detail = etDetailAddress.getText().toString().trim();

        boolean isValid = !name.isEmpty()
                && PHONE_PATTERN.matcher(phone).matches()
                && !province.isEmpty()
                && !ward.isEmpty()
                && !detail.isEmpty();

        btnSubmit.setEnabled(isValid);
    }


    private boolean validate() {
        boolean valid = true;
        if (etFullName.getText().toString().trim().isEmpty()) {
            showError(etFullName, tvErrorName, "Vui lòng nhập họ và tên");
            valid = false;
        }

        String phone = etPhone.getText().toString().trim();
        if (phone.isEmpty() || !PHONE_PATTERN.matcher(phone).matches()) {
            showError(etPhone, tvErrorPhone, "Số điện thoại không hợp lệ");
            valid = false;
        }


        if (autoProvince.getText().toString().trim().isEmpty()) {
            tvErrorProvince.setVisibility(View.VISIBLE);
            valid = false;
        }


        if (autoWard.getText().toString().trim().isEmpty()) {
            tvErrorWard.setVisibility(View.VISIBLE);
            valid = false;
        }


        if (etDetailAddress.getText().toString().trim().isEmpty()) {
            showError(etDetailAddress, tvErrorDetailAddress, "Vui lòng nhập địa chỉ cụ thể");
            valid = false;
        }


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


    // FIX (bug: "Địa chỉ trong thanh toán không lưu vào Sổ địa chỉ"):
    // Trước đây nếu FirebaseAuth.getInstance().getUid() null tại đúng lúc bấm Lưu (ví dụ do
    // Firebase Auth session chưa kịp restore ngay sau khi vừa đăng nhập/xác thực OTP), code
    // sẽ âm thầm lưu địa chỉ vào document ảo "guest_user" thay vì đúng UID thật của người
    // dùng. Hậu quả: mở lại Sổ địa chỉ (luôn đọc theo UID thật) sẽ KHÔNG thấy địa chỉ vừa
    // thêm — y hệt hiện tượng "không lưu". Sửa: luôn lấy UID mới nhất tại thời điểm Lưu, và
    // CHẶN việc lưu (báo lỗi rõ ràng) nếu chưa đăng nhập, thay vì ghi vào nơi vô hình.
    private void saveAndReturn() {
        String currentUserId = FirebaseAuth.getInstance().getUid();
        if (currentUserId == null) {
            Toast.makeText(getContext(), "Vui lòng đăng nhập để lưu địa chỉ", Toast.LENGTH_SHORT).show();
            return;
        }
        userId = currentUserId;
        final String effectiveUserId = userId;


        String addressId = editingAddress != null ? editingAddress.getId() : db.collection("users").document(effectiveUserId).collection("addresses").document().getId();


        Address address = new Address(
                addressId,
                etFullName.getText().toString().trim(),
                etPhone.getText().toString().trim(),
                autoProvince.getText().toString().trim(),
                "", // District gộp chung
                autoWard.getText().toString().trim(),
                etDetailAddress.getText().toString().trim(),
                selectedType,
                switchDefault.isChecked()
        );


        btnSubmit.setEnabled(false);
        btnSubmit.setText("Đang lưu...");


        WriteBatch batch = db.batch();
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
                    .addOnFailureListener(e -> performSave(batch, address, effectiveUserId));
        } else {
            performSave(batch, address, effectiveUserId);
        }
    }


    private void performSave(WriteBatch batch, Address address, String effectiveUserId) {
        batch.set(db.collection("users").document(effectiveUserId).collection("addresses").document(address.getId()), address);
        batch.commit().addOnSuccessListener(aVoid -> {
            if (!isAdded()) return;


            Toast.makeText(getContext(), "Đã lưu địa chỉ thành công", Toast.LENGTH_SHORT).show();

            Bundle result = new Bundle();
            result.putSerializable("saved_address", address);
            getParentFragmentManager().setFragmentResult("address_form_result", result);

            if (isAdded() && getActivity() != null) {
                getActivity().getOnBackPressedDispatcher().onBackPressed();
            }


        }).addOnFailureListener(e -> {
            if (isAdded()) {
                btnSubmit.setEnabled(true);
                btnSubmit.setText("Hoàn thành");
                Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }


    // FIX: tương tự saveAndReturn(), luôn dùng UID thật tại thời điểm xóa, không fallback
    // "guest_user" — tránh xóa nhầm/không tìm thấy do lệch document.
    private void deleteAddress() {
        if (editingAddress == null) return;


        String currentUserId = FirebaseAuth.getInstance().getUid();
        if (currentUserId == null) {
            Toast.makeText(getContext(), "Vui lòng đăng nhập để xóa địa chỉ", Toast.LENGTH_SHORT).show();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa địa chỉ này?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    db.collection("users").document(currentUserId)
                            .collection("addresses").document(editingAddress.getId())
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(), "Đã xóa địa chỉ", Toast.LENGTH_SHORT).show();
                                getParentFragmentManager().setFragmentResult("address_form_result", new Bundle());
                                getParentFragmentManager().popBackStack();
                            })
                            .addOnFailureListener(e -> {
                                if (isAdded()) {
                                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}
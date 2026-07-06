package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapters.AddressAdapter;
import com.example.models.Address;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class AddressBookFragment extends Fragment implements AddressAdapter.Listener {

    private RecyclerView rvAddresses;
    private View layoutEmpty;
    private View btnAddNewAddress;
    private Button btnConfirm;

    private AddressAdapter adapter;
    private final List<Address> addressList = new ArrayList<>();
    private Address selectedAddress;

    private FirebaseFirestore db;
    private String userId;

    public AddressBookFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_address_book, container, false);

        db = FirebaseFirestore.getInstance();
        String currentAuthId = FirebaseAuth.getInstance().getUid();
        userId = (currentAuthId != null) ? currentAuthId : "guest_user";

        bindViews(view);
        setupListeners();
        loadAddressesFromFirestore();

        // Lắng nghe kết quả từ form nhập địa chỉ (khi thêm/sửa xong)
        getParentFragmentManager().setFragmentResultListener("address_form_result", getViewLifecycleOwner(), (requestKey, result) -> {
            Address savedAddress = (Address) result.getSerializable("saved_address");
            if (savedAddress != null) {
                // Sau khi lưu thành công từ form, ta load lại từ Firestore để đảm bảo đồng bộ
                loadAddressesFromFirestore();
            }
        });

        return view;
    }

    private void bindViews(View view) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        rvAddresses = view.findViewById(R.id.rvAddresses);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        btnAddNewAddress = view.findViewById(R.id.btnAddNewAddress);
        btnConfirm = view.findViewById(R.id.btnConfirm);

        rvAddresses.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void setupListeners() {
        btnAddNewAddress.setOnClickListener(v -> openAddressForm(null));

        btnConfirm.setOnClickListener(v -> {
            if (selectedAddress != null) {
                Bundle result = new Bundle();
                result.putSerializable("selected_address", selectedAddress);
                getParentFragmentManager().setFragmentResult("address_result", result);
                
                // Trả về CheckoutFragment (nếu đi từ đó)
                requireActivity().getSupportFragmentManager().popBackStack();
            } else {
                Toast.makeText(getContext(), "Vui lòng chọn một địa chỉ nhận hàng", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAddressesFromFirestore() {
        if (userId == null) {
            renderList();
            return;
        }

        db.collection("users").document(userId).collection("addresses")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) return;
                    addressList.clear();
                    if (!snapshot.isEmpty()) {
                        for (QueryDocumentSnapshot doc : snapshot) {
                            Address a = doc.toObject(Address.class);
                            if (a != null) {
                                a.setId(doc.getId());
                                addressList.add(a);
                            }
                        }
                    }
                    renderList();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi tải địa chỉ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    renderList();
                });
    }

    private void renderList() {
        if (addressList.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvAddresses.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvAddresses.setVisibility(View.VISIBLE);
            adapter = new AddressAdapter(addressList, true, this);
            rvAddresses.setAdapter(adapter);

            // Tìm địa chỉ được chọn (ưu tiên mặc định)
            for (Address a : addressList) {
                if (a.isDefault()) {
                    selectedAddress = a;
                    break;
                }
            }
        }
    }

    private void openAddressForm(@Nullable Address address) {
        AddressFormFragment fragment = AddressFormFragment.newInstance(address);
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onSelect(Address address) {
        this.selectedAddress = address;
    }

    @Override
    public void onEdit(Address address) {
        openAddressForm(address);
    }

    private void updateOrAddAddress(Address address) {
        int foundIndex = -1;
        for (int i = 0; i < addressList.size(); i++) {
            if (addressList.get(i).getId().equals(address.getId())) {
                foundIndex = i;
                break;
            }
        }

        if (foundIndex != -1) {
            addressList.set(foundIndex, address);
        } else {
            addressList.add(address);
        }

        // Nếu địa chỉ mới là mặc định, bỏ mặc định của các địa chỉ cũ
        if (address.isDefault()) {
            for (Address a : addressList) {
                if (!a.getId().equals(address.getId())) a.setDefault(false);
            }
        }

        renderList();
    }
}

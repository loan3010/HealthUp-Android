package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

public class AddressManagementFragment extends Fragment implements AddressAdapter.Listener {

    private RecyclerView rvAddresses;
    private View layoutEmpty;
    private View btnAddNewAddress;

    private AddressAdapter adapter;
    private final List<Address> addressList = new ArrayList<>();

    private FirebaseFirestore db;
    private String userId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_address_management, container, false);

        db = FirebaseFirestore.getInstance();
        String currentAuthId = FirebaseAuth.getInstance().getUid();
        userId = (currentAuthId != null) ? currentAuthId : "guest_user";

        bindViews(view);
        setupListeners();
        loadAddressesFromFirestore();

        getParentFragmentManager().setFragmentResultListener("address_form_result", getViewLifecycleOwner(), (requestKey, result) -> {
            loadAddressesFromFirestore();
        });

        return view;
    }

    private void bindViews(View view) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        rvAddresses = view.findViewById(R.id.rvAddresses);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        btnAddNewAddress = view.findViewById(R.id.btnAddNewAddress);

        rvAddresses.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void setupListeners() {
        btnAddNewAddress.setOnClickListener(v -> openAddressForm(null));
    }

    private void loadAddressesFromFirestore() {
        if (userId == null) return;

        db.collection("users").document(userId).collection("addresses")
                .get()
                .addOnSuccessListener(snapshot -> {
                    addressList.clear();
                    if (!snapshot.isEmpty()) {
                        for (QueryDocumentSnapshot doc : snapshot) {
                            Address a = doc.toObject(Address.class);
                            a.setId(doc.getId());
                            addressList.add(a);
                        }
                    }
                    renderList();
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Lỗi tải địa chỉ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void renderList() {
        if (addressList.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvAddresses.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvAddresses.setVisibility(View.VISIBLE);
            adapter = new AddressAdapter(addressList, false, this);
            rvAddresses.setAdapter(adapter);
        }
    }

    private void openAddressForm(@Nullable Address address) {
        AddressFormFragment fragment = AddressFormFragment.newInstance(address);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onSelect(Address address) {
        // Không dùng trong Management mode
    }

    @Override
    public void onEdit(Address address) {
        openAddressForm(address);
    }
}

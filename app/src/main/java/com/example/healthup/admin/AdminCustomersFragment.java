package com.example.healthup.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class AdminCustomersFragment extends Fragment implements AdminCustomerAdapter.Listener {

    private final AdminRepository repository = new AdminRepository();
    private final List<AdminRepository.AdminCustomer> allCustomers = new ArrayList<>();
    private final List<AdminRepository.AdminCustomer> filteredCustomers = new ArrayList<>();
    private AdminCustomerAdapter adapter;
    private TextView tvEmpty;
    private TextInputEditText etSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_customers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvEmpty = view.findViewById(R.id.tvEmptyCustomers);
        etSearch = view.findViewById(R.id.etSearchCustomers);
        RecyclerView recyclerView = view.findViewById(R.id.rvAdminCustomers);

        adapter = new AdminCustomerAdapter(filteredCustomers, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCustomers();
    }

    private void loadCustomers() {
        repository.loadCustomers(new AdminRepository.CustomersCallback() {
            @Override
            public void onSuccess(@NonNull List<AdminRepository.AdminCustomer> customers) {
                if (!isAdded()) return;
                allCustomers.clear();
                allCustomers.addAll(customers);
                filter(etSearch != null && etSearch.getText() != null ? etSearch.getText().toString() : "");
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filter(String query) {
        filteredCustomers.clear();
        String q = query == null ? "" : query.trim().toLowerCase();
        for (AdminRepository.AdminCustomer customer : allCustomers) {
            String name = customer.fullName != null ? customer.fullName.toLowerCase() : "";
            String phone = customer.phone != null ? customer.phone : "";
            if (q.isEmpty() || name.contains(q) || phone.contains(q)) {
                filteredCustomers.add(customer);
            }
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(filteredCustomers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onToggleDisabled(AdminRepository.AdminCustomer customer, boolean disabled) {
        repository.setCustomerDisabled(customer.uid, disabled, new AdminRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                customer.disabled = disabled;
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                adapter.notifyDataSetChanged();
            }
        });
    }
}

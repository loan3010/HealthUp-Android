package com.example.healthup.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AdminCustomersFragment extends Fragment implements AdminCustomerAdapter.Listener {

    private static final List<String> SORT_KEYS = Arrays.asList(
            "name_asc", "name_desc", "spent_desc", "spent_asc"
    );

    private final AdminRepository repository = new AdminRepository();
    private final List<AdminRepository.AdminCustomer> allCustomers = new ArrayList<>();
    private final List<AdminRepository.AdminCustomer> filteredCustomers = new ArrayList<>();
    private AdminCustomerAdapter adapter;
    private TextView tvEmpty;
    private TextInputEditText etSearch;
    private Spinner spinnerSort;
    private String statusFilter = "all";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_customers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvEmpty = view.findViewById(R.id.tvEmptyCustomers);
        etSearch = view.findViewById(R.id.etSearchCustomers);
        spinnerSort = view.findViewById(R.id.spinnerCustomerSort);
        RecyclerView recyclerView = view.findViewById(R.id.rvAdminCustomers);
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupCustomerFilter);

        adapter = new AdminCustomerAdapter(filteredCustomers, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        List<String> sortLabels = Arrays.asList(
                getString(R.string.admin_sort_name_asc),
                getString(R.string.admin_sort_name_desc),
                getString(R.string.admin_sort_spent_desc),
                getString(R.string.admin_sort_spent_asc)
        );
        spinnerSort.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortLabels));
        spinnerSort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view1, int position, long id) {
                applyFilters();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) return;
            if (checkedId == R.id.chipActiveCustomers) {
                statusFilter = "active";
            } else if (checkedId == R.id.chipLockedCustomers) {
                statusFilter = "locked";
            } else {
                statusFilter = "all";
            }
            applyFilters();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilters(); }
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
                applyFilters();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilters() {
        filteredCustomers.clear();
        String q = etSearch != null && etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";

        for (AdminRepository.AdminCustomer customer : allCustomers) {
            if (!matchesStatusFilter(customer)) continue;
            if (!q.isEmpty() && !matchesCustomer(customer, q)) continue;
            filteredCustomers.add(customer);
        }
        sortCustomers();
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(filteredCustomers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesStatusFilter(AdminRepository.AdminCustomer customer) {
        if ("active".equals(statusFilter)) return !customer.disabled;
        if ("locked".equals(statusFilter)) return customer.disabled;
        return true;
    }

    private boolean matchesCustomer(AdminRepository.AdminCustomer customer, String query) {
        if (contains(customer.fullName, query)) return true;
        if (contains(customer.phone, query)) return true;
        if (contains(normalizePhone(customer.phone), normalizePhone(query))) return true;
        if (contains(customer.email, query)) return true;
        return contains(customer.username, query);
    }

    private void sortCustomers() {
        int sortIndex = spinnerSort != null ? spinnerSort.getSelectedItemPosition() : 0;
        if (sortIndex < 0 || sortIndex >= SORT_KEYS.size()) sortIndex = 0;
        String sortKey = SORT_KEYS.get(sortIndex);
        Comparator<AdminRepository.AdminCustomer> comparator;
        switch (sortKey) {
            case "name_desc":
                comparator = (a, b) -> safeName(b).compareToIgnoreCase(safeName(a));
                break;
            case "spent_desc":
                comparator = (a, b) -> Long.compare(b.spentAmount, a.spentAmount);
                break;
            case "spent_asc":
                comparator = Comparator.comparingLong(a -> a.spentAmount);
                break;
            case "name_asc":
            default:
                comparator = (a, b) -> safeName(a).compareToIgnoreCase(safeName(b));
                break;
        }
        filteredCustomers.sort(comparator);
    }

    private String safeName(AdminRepository.AdminCustomer customer) {
        return customer.fullName != null ? customer.fullName : "";
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String normalizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9]", "");
    }

    @Override
    public void onToggleDisabled(AdminRepository.AdminCustomer customer, boolean disabled) {
        if (disabled) {
            AdminDisableDialogHelper.showLockReasonDialog(requireContext(),
                    reason -> lockCustomer(customer, reason),
                    () -> adapter.notifyDataSetChanged());
        } else {
            AdminDisableDialogHelper.showUnlockReasonDialog(requireContext(),
                    reason -> unlockCustomer(customer, reason),
                    () -> adapter.notifyDataSetChanged());
        }
    }

    private void lockCustomer(AdminRepository.AdminCustomer customer, String reason) {
        repository.setCustomerDisabled(customer.uid, true, reason, new AdminRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                customer.disabled = true;
                customer.disabledReason = reason;
                adapter.notifyDataSetChanged();
                Toast.makeText(requireContext(), "Đã khóa và gửi thông báo", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull String message) {
                adapter.notifyDataSetChanged();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void unlockCustomer(AdminRepository.AdminCustomer customer, String reason) {
        repository.setCustomerDisabled(customer.uid, false, reason, new AdminRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                customer.disabled = false;
                customer.disabledReason = null;
                adapter.notifyDataSetChanged();
                Toast.makeText(requireContext(), "Đã mở khóa và gửi thông báo", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull String message) {
                adapter.notifyDataSetChanged();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onCustomerClick(AdminRepository.AdminCustomer customer) {
        Intent intent = new Intent(requireContext(), AdminCustomerDetailActivity.class);
        intent.putExtra(AdminCustomerDetailActivity.EXTRA_CUSTOMER_UID, customer.uid);
        startActivity(intent);
    }
}

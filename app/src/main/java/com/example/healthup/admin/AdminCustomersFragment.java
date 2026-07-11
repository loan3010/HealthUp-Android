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
import androidx.viewpager2.widget.ViewPager2;

import com.example.healthup.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminCustomersFragment extends Fragment implements AdminCustomerAdapter.Listener {

    public static final String FILTER_ALL = "all";
    public static final String FILTER_ACTIVE = "active";
    public static final String FILTER_LOCKED = "locked";

    private static final List<String> FILTERS = Arrays.asList(
            FILTER_ALL, FILTER_ACTIVE, FILTER_LOCKED
    );

    private static final List<String> SORT_KEYS = Arrays.asList(
            "name_asc", "name_desc", "spent_desc", "spent_asc"
    );

    private final AdminRepository repository = new AdminRepository();
    private final List<AdminRepository.AdminCustomer> allCustomers = new ArrayList<>();

    private TextInputEditText etSearch;
    private Spinner spinnerSort;
    private ViewPager2 viewPager;
    private TabLayout tabFilters;
    private TabLayoutMediator tabMediator;
    private CustomerPagerAdapter pagerAdapter;
    private String statusFilter = FILTER_ALL;

    private final ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            statusFilter = FILTERS.get(position);
            updateTabLabels();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_customers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        etSearch = view.findViewById(R.id.etSearchCustomers);
        spinnerSort = view.findViewById(R.id.spinnerCustomerSort);
        viewPager = view.findViewById(R.id.vpAdminCustomers);
        tabFilters = view.findViewById(R.id.tabCustomerFilters);

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
                refreshPages();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        pagerAdapter = new CustomerPagerAdapter();
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(FILTERS.size());
        viewPager.registerOnPageChangeCallback(pageChangeCallback);

        tabMediator = new TabLayoutMediator(tabFilters, viewPager, (tab, position) ->
                tab.setText(baseLabelForFilter(FILTERS.get(position))));
        tabMediator.attach();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshPages();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        viewPager.setCurrentItem(indexForFilter(statusFilter), false);
    }

    @Override
    public void onDestroyView() {
        if (viewPager != null) {
            viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
        if (tabMediator != null) {
            tabMediator.detach();
            tabMediator = null;
        }
        super.onDestroyView();
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
                refreshPages();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshPages() {
        if (pagerAdapter != null) {
            pagerAdapter.notifyDataSetChanged();
        }
        updateTabLabels();
    }

    private void updateTabLabels() {
        if (tabFilters == null) return;
        Map<String, Integer> counts = countByFilter();
        for (int i = 0; i < FILTERS.size(); i++) {
            TabLayout.Tab tab = tabFilters.getTabAt(i);
            if (tab == null) continue;
            String filter = FILTERS.get(i);
            tab.setText(AdminOrderListHelper.formatChipLabel(
                    baseLabelForFilter(filter), counts.getOrDefault(filter, 0)));
        }
    }

    private Map<String, Integer> countByFilter() {
        Map<String, Integer> counts = new HashMap<>();
        String q = currentQuery();
        for (String filter : FILTERS) {
            int count = 0;
            for (AdminRepository.AdminCustomer customer : allCustomers) {
                if (!matchesStatusFilter(customer, filter)) continue;
                if (!q.isEmpty() && !matchesCustomer(customer, q)) continue;
                count++;
            }
            counts.put(filter, count);
        }
        return counts;
    }

    private List<AdminRepository.AdminCustomer> buildPageCustomers(@NonNull String filter) {
        List<AdminRepository.AdminCustomer> page = new ArrayList<>();
        String q = currentQuery();
        for (AdminRepository.AdminCustomer customer : allCustomers) {
            if (!matchesStatusFilter(customer, filter)) continue;
            if (!q.isEmpty() && !matchesCustomer(customer, q)) continue;
            page.add(customer);
        }
        sortCustomers(page);
        return page;
    }

    private String currentQuery() {
        return etSearch != null && etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
    }

    private boolean matchesStatusFilter(AdminRepository.AdminCustomer customer, String filter) {
        if (FILTER_ACTIVE.equals(filter)) return !customer.disabled;
        if (FILTER_LOCKED.equals(filter)) return customer.disabled;
        return true;
    }

    private boolean matchesCustomer(AdminRepository.AdminCustomer customer, String query) {
        if (contains(customer.fullName, query)) return true;
        if (contains(customer.phone, query)) return true;
        if (contains(normalizePhone(customer.phone), normalizePhone(query))) return true;
        if (contains(customer.email, query)) return true;
        return contains(customer.username, query);
    }

    private void sortCustomers(List<AdminRepository.AdminCustomer> customers) {
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
        customers.sort(comparator);
    }

    private String safeName(AdminRepository.AdminCustomer customer) {
        return customer.fullName != null ? customer.fullName : "";
    }

    private String baseLabelForFilter(String filter) {
        if (FILTER_ACTIVE.equals(filter)) return getString(R.string.admin_customer_active);
        if (FILTER_LOCKED.equals(filter)) return getString(R.string.admin_customer_locked);
        return getString(R.string.admin_filter_all);
    }

    private int indexForFilter(@NonNull String filter) {
        int index = FILTERS.indexOf(filter);
        return Math.max(0, index);
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
                    this::refreshPages);
        } else {
            AdminDisableDialogHelper.showUnlockReasonDialog(requireContext(),
                    reason -> unlockCustomer(customer, reason),
                    this::refreshPages);
        }
    }

    private void lockCustomer(AdminRepository.AdminCustomer customer, String reason) {
        repository.setCustomerDisabled(customer.uid, true, reason, new AdminRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                customer.disabled = true;
                customer.disabledReason = reason;
                refreshPages();
                Toast.makeText(requireContext(), "Đã khóa và gửi thông báo", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull String message) {
                refreshPages();
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
                refreshPages();
                Toast.makeText(requireContext(), "Đã mở khóa và gửi thông báo", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull String message) {
                refreshPages();
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

    private class CustomerPagerAdapter extends RecyclerView.Adapter<CustomerPagerAdapter.PageHolder> {

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View page = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_filter_page, parent, false);
            return new PageHolder(page);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            String filter = FILTERS.get(position);
            holder.bind(buildPageCustomers(filter), getString(R.string.admin_empty_customers));
        }

        @Override
        public int getItemCount() {
            return FILTERS.size();
        }

        class PageHolder extends RecyclerView.ViewHolder {
            private final List<AdminRepository.AdminCustomer> pageItems = new ArrayList<>();
            private final AdminCustomerAdapter adapter;
            private final TextView tvEmpty;

            PageHolder(@NonNull View itemView) {
                super(itemView);
                RecyclerView rv = itemView.findViewById(R.id.rvFilterPage);
                tvEmpty = itemView.findViewById(R.id.tvFilterPageEmpty);
                adapter = new AdminCustomerAdapter(pageItems, AdminCustomersFragment.this);
                rv.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
                rv.setAdapter(adapter);
            }

            void bind(@NonNull List<AdminRepository.AdminCustomer> customers, @NonNull String emptyText) {
                pageItems.clear();
                pageItems.addAll(customers);
                adapter.notifyDataSetChanged();
                tvEmpty.setText(emptyText);
                tvEmpty.setVisibility(pageItems.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }
    }
}

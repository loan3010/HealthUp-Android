package com.example.healthup;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.adapters.FAQCategoryAdapter;
import com.example.models.FAQCategory;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.healthup.R;
import com.example.adapters.FAQAdapter;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.FAQ;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class FAQFragment extends Fragment {


    private RecyclerView rvFaqs, rvCategories, rvSuggestions;
    private FAQAdapter faqAdapter, suggestionAdapter;
    private List<FAQ> fullFaqList = new ArrayList<>();
    private List<FAQCategory> categoriesList = new ArrayList<>();
    private TextView tvFaqListTitle;
    private View layoutCategories, layoutSuggestions;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private String selectedCategoryKey = "";
    private boolean returnToPreviousActivity = false;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            returnToPreviousActivity = getArguments().getBoolean("return_to_previous", false);
        }
        View view = inflater.inflate(R.layout.fragment_faqs, container, false);
        initViews(view);
        setupCategories();
        setupFaqList();
        setupSearch();
        fetchFaqs();
        return view;
    }


    private void initViews(View view) {
        rvFaqs = view.findViewById(R.id.rv_faqs);
        rvCategories = view.findViewById(R.id.rv_faq_categories);
        rvSuggestions = view.findViewById(R.id.rv_faq_suggestions);
        tvFaqListTitle = view.findViewById(R.id.tv_faq_list_title);
        layoutCategories = view.findViewById(R.id.layout_categories);
        layoutSuggestions = view.findViewById(R.id.layout_suggestions);
        etSearch = view.findViewById(R.id.et_faq_search);
        btnClearSearch = view.findViewById(R.id.btn_clear_search);


        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (returnToPreviousActivity && getActivity() != null) {
                getActivity().finish();
            } else if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });


        view.findViewById(R.id.btn_contact_support).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:0769845728"));
            startActivity(intent);
        });


        setupSocialButtons(view);
    }


    private void setupSocialButtons(View view) {
        View btnFb = view.findViewById(R.id.btn_fb);
        View btnIg = view.findViewById(R.id.btn_ig);
        View btnThreads = view.findViewById(R.id.btn_threads);
        View btnTiktok = view.findViewById(R.id.btn_tiktok);


        if (btnFb != null) btnFb.setOnClickListener(v -> openUrl("https://facebook.com/healthup"));
        if (btnIg != null) btnIg.setOnClickListener(v -> openUrl("https://instagram.com/healthup"));
        if (btnThreads != null) btnThreads.setOnClickListener(v -> openUrl("https://www.threads.net/@healthup"));
        if (btnTiktok != null) btnTiktok.setOnClickListener(v -> openUrl("https://tiktok.com/@healthup"));
    }


    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Không thể mở liên kết", Toast.LENGTH_SHORT).show();
        }
    }


    private void setupCategories() {
        categoriesList.clear();
        categoriesList.add(new FAQCategory(getString(R.string.order_category), R.drawable.ic_box, "order"));
        categoriesList.add(new FAQCategory(getString(R.string.payment_category), R.drawable.ic_payment_card, "payment"));
        categoriesList.add(new FAQCategory(getString(R.string.shipping_category), R.drawable.ic_policy_truck, "shipping"));
        categoriesList.add(new FAQCategory(getString(R.string.return_category), R.drawable.ic_order_return, "return"));
        categoriesList.add(new FAQCategory(getString(R.string.account_category), R.drawable.ic_profile, "account"));
        categoriesList.add(new FAQCategory(getString(R.string.product_category), R.drawable.ic_leaf, "product"));


        FAQCategoryAdapter categoryAdapter = new FAQCategoryAdapter(categoriesList, (category, position, isSelected) -> {
            if (isSelected && category != null) {
                selectedCategoryKey = category.getKey();
            } else {
                selectedCategoryKey = ""; // Deselected
            }
            filterFaqs();
        });


        rvCategories.setLayoutManager(new GridLayoutManager(getContext(), 3));
        rvCategories.setAdapter(categoryAdapter);
        rvCategories.setNestedScrollingEnabled(false);
    }


    private void setupFaqList() {
        faqAdapter = new FAQAdapter(new ArrayList<>());
        rvFaqs.setLayoutManager(new LinearLayoutManager(getContext()));
        rvFaqs.setAdapter(faqAdapter);
        rvFaqs.setNestedScrollingEnabled(false);

        suggestionAdapter = new FAQAdapter(new ArrayList<>());
        rvSuggestions.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSuggestions.setAdapter(suggestionAdapter);
        rvSuggestions.setNestedScrollingEnabled(false);
    }


    private void setupSearch() {
        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                filterFaqs();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }


    private void fetchFaqs() {
        FirestoreManager.getInstance().getFirestore().collection("faqs")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) return;
                    fullFaqList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        FAQ faq = doc.toObject(FAQ.class);
                        if (faq != null) {
                            faq.setId(doc.getId());
                            fullFaqList.add(faq);
                        }
                    }
                    filterFaqs();
                });
    }

    private void filterFaqs() {
        String query = etSearch.getText().toString().trim().toLowerCase(Locale.getDefault());
        List<FAQ> filteredList = new ArrayList<>();
        List<FAQ> suggestionsList = new ArrayList<>();

        if (!query.isEmpty()) {
            // Searching mode
            layoutCategories.setVisibility(View.GONE);
            tvFaqListTitle.setText(R.string.faq_search_result);
            
            for (FAQ faq : fullFaqList) {
                if (faq.getQuestion().toLowerCase(Locale.getDefault()).contains(query) ||
                    (faq.getAnswer() != null && faq.getAnswer().toLowerCase(Locale.getDefault()).contains(query))) {
                    filteredList.add(faq);
                } else {
                    suggestionsList.add(faq);
                }
            }
            
            layoutSuggestions.setVisibility(filteredList.isEmpty() ? View.GONE : View.VISIBLE);
            if (!filteredList.isEmpty()) {
                suggestionAdapter.updateList(suggestionsList.subList(0, Math.min(3, suggestionsList.size())));
            }
        } else {
            // Category or general mode
            layoutCategories.setVisibility(View.VISIBLE);
            layoutSuggestions.setVisibility(View.GONE);

            if (selectedCategoryKey.isEmpty()) {
                tvFaqListTitle.setText(R.string.faq_frequent);
                filteredList.addAll(fullFaqList);
            } else {
                // Find category display name
                String categoryDisplayName = "";
                for (FAQCategory cat : categoriesList) {
                    if (cat.getKey().equals(selectedCategoryKey)) {
                        categoryDisplayName = cat.getName();
                        break;
                    }
                }
                
                tvFaqListTitle.setText(getString(R.string.faq_category_prefix, categoryDisplayName));
                for (FAQ faq : fullFaqList) {
                    if (selectedCategoryKey.equals(faq.getCategory())) {
                        filteredList.add(faq);
                    }
                }
            }
        }

        faqAdapter.updateList(filteredList);
    }
}
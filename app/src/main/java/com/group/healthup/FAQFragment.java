package com.group.healthup;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.group.healthup.R;
import com.group.adapters.FAQAdapter;
import com.group.healthup.firebase.FirestoreManager;
import com.group.models.FAQ;
import com.group.models.FAQCategory;
import java.util.ArrayList;
import java.util.List;

public class FAQFragment extends Fragment {

    private RecyclerView rvCategories, rvFaqs;
    private FAQAdapter faqAdapter;
    private List<FAQ> fullFaqList = new ArrayList<>();
    private List<FAQ> filteredFaqList = new ArrayList<>();
    private List<FAQCategory> categories = new ArrayList<>();
    private EditText etSearch;
    private TextView tvFaqListTitle;
    private String selectedCategory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        selectedCategory = getString(R.string.all_categories);
        View view = inflater.inflate(R.layout.fragment_faqs, container, false);
        initViews(view);
        setupCategories();
        setupFaqList();
        fetchFaqs();
        return view;
    }

    private void initViews(View view) {
        rvCategories = view.findViewById(R.id.rv_faq_categories);
        rvFaqs = view.findViewById(R.id.rv_faqs);
        etSearch = view.findViewById(R.id.et_search_faq);
        tvFaqListTitle = view.findViewById(R.id.tv_faq_list_title);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        view.findViewById(R.id.btn_contact_support).setOnClickListener(v -> {
            // Placeholder for contact action
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:19001234"));
            startActivity(intent);
        });

        setupSocialButtons(view);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filterFaqs(s.toString());
            }
        });
    }

    private void setupSocialButtons(View view) {
        view.findViewById(R.id.btn_fb).setOnClickListener(v -> openUrl("https://facebook.com/healthup"));
        view.findViewById(R.id.btn_zalo).setOnClickListener(v -> openUrl("https://zalo.me/healthup"));
        view.findViewById(R.id.btn_ig).setOnClickListener(v -> openUrl("https://instagram.com/healthup"));
        view.findViewById(R.id.btn_messenger).setOnClickListener(v -> openUrl("https://m.me/healthup"));
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
        categories.add(new FAQCategory(getString(R.string.order_category), R.drawable.ic_cart));
        categories.add(new FAQCategory(getString(R.string.payment_category), R.drawable.ic_notifications)); 
        categories.add(new FAQCategory(getString(R.string.shipping_category), R.drawable.ic_notifications));
        categories.add(new FAQCategory(getString(R.string.return_category), R.drawable.ic_notifications));
        categories.add(new FAQCategory(getString(R.string.account_category), R.drawable.ic_profile));
        categories.add(new FAQCategory(getString(R.string.product_category), R.drawable.ic_category));

        FAQCategoryAdapter categoryAdapter = new FAQCategoryAdapter(categories, category -> {
            selectedCategory = category.getName();
            tvFaqListTitle.setText(getString(R.string.faq_category_prefix, selectedCategory));
            filterFaqs(etSearch.getText().toString());
        });

        rvCategories.setLayoutManager(new GridLayoutManager(getContext(), 4));
        rvCategories.setAdapter(categoryAdapter);
    }

    private void setupFaqList() {
        faqAdapter = new FAQAdapter(filteredFaqList);
        rvFaqs.setLayoutManager(new LinearLayoutManager(getContext()));
        rvFaqs.setAdapter(faqAdapter);
        rvFaqs.setNestedScrollingEnabled(false);
    }

    private void fetchFaqs() {
        FirestoreManager.getInstance().getFirestore().collection("faqs")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    fullFaqList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        FAQ faq = doc.toObject(FAQ.class);
                        if (faq != null) {
                            faq.setId(doc.getId());
                            fullFaqList.add(faq);
                        }
                    }
                    filterFaqs(etSearch.getText().toString());
                });
    }

    private void filterFaqs(String query) {
        filteredFaqList.clear();
        String lowerQuery = query.toLowerCase().trim();
        String allCats = getString(R.string.all_categories);
        
        for (FAQ faq : fullFaqList) {
            boolean matchesCategory = selectedCategory.equals(allCats) || faq.getCategory().equals(selectedCategory);
            boolean matchesSearch = faq.getQuestion().toLowerCase().contains(lowerQuery) || 
                                    faq.getAnswer().toLowerCase().contains(lowerQuery);
            
            if (matchesCategory && matchesSearch) {
                filteredFaqList.add(faq);
            }
        }
        
        if (!lowerQuery.isEmpty()) {
            tvFaqListTitle.setText(R.string.faq_search_result);
        } else if (selectedCategory.equals(allCats)) {
            tvFaqListTitle.setText(R.string.faq_title);
        }
        
        faqAdapter.notifyDataSetChanged();
    }

    // Inner Adapter for Categories
    private class FAQCategoryAdapter extends RecyclerView.Adapter<FAQCategoryAdapter.ViewHolder> {
        private List<FAQCategory> list;
        private OnCategoryClickListener listener;
        private int selectedPos = -1;

        public FAQCategoryAdapter(List<FAQCategory> list, OnCategoryClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_faq_category, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FAQCategory cat = list.get(position);
            holder.tvName.setText(cat.getName());
            holder.ivIcon.setImageResource(cat.getIconResId());
            
            if (selectedPos == position) {
                holder.card.setStrokeWidth(2);
                holder.card.setStrokeColor(getResources().getColor(R.color.primary_green));
                holder.card.setCardBackgroundColor(getResources().getColor(R.color.bg_light_green));
            } else {
                holder.card.setStrokeWidth(0);
                holder.card.setCardBackgroundColor(getResources().getColor(R.color.white));
            }

            holder.itemView.setOnClickListener(v -> {
                int old = selectedPos;
                selectedPos = holder.getAdapterPosition();
                notifyItemChanged(old);
                notifyItemChanged(selectedPos);
                listener.onCategoryClick(cat);
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            ImageView ivIcon;
            com.google.android.material.card.MaterialCardView card;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_faq_category_name);
                ivIcon = v.findViewById(R.id.iv_faq_category_icon);
                card = v.findViewById(R.id.card_faq_category);
            }
        }
    }

    interface OnCategoryClickListener {
        void onCategoryClick(FAQCategory category);
    }
}

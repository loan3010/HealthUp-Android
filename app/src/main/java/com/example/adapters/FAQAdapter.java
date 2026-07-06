package com.example.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;
import com.example.healthup.R;
import com.example.models.FAQ;
import java.util.List;

public class FAQAdapter extends RecyclerView.Adapter<FAQAdapter.ViewHolder> {
    private List<FAQ> faqList;
    private RecyclerView recyclerView;

    public FAQAdapter(List<FAQ> faqList) {
        this.faqList = faqList;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    public void updateList(List<FAQ> newList) {
        this.faqList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_faq, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FAQ faq = faqList.get(position);
        holder.tvQuestion.setText(faq.getQuestion());
        holder.tvAnswer.setText(faq.getAnswer());
        
        holder.tvAnswer.setVisibility(faq.isExpanded() ? View.VISIBLE : View.GONE);
        holder.ivArrow.setRotation(faq.isExpanded() ? 90f : -90f);

        holder.layoutHeader.setOnClickListener(v -> {
            boolean expanded = faq.isExpanded();
            faq.setExpanded(!expanded);
            
            if (recyclerView != null) {
                TransitionManager.beginDelayedTransition(recyclerView);
            }
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return faqList != null ? faqList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvQuestion, tvAnswer;
        ImageView ivArrow;
        View layoutHeader;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuestion = itemView.findViewById(R.id.tv_faq_question);
            tvAnswer = itemView.findViewById(R.id.tv_faq_answer);
            ivArrow = itemView.findViewById(R.id.iv_faq_arrow);
            layoutHeader = itemView.findViewById(R.id.layout_faq_header);
        }
    }
}

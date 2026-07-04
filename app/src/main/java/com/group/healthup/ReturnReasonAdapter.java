package com.group.healthup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.group.healthup.databinding.ItemReasonBinding;
import com.group.models.ReturnReason;
import java.util.List;

public class ReturnReasonAdapter extends RecyclerView.Adapter<ReturnReasonAdapter.ViewHolder> {
    private List<ReturnReason> reasons;
    private int selectedPosition = -1;

    public ReturnReasonAdapter(List<ReturnReason> reasons, String initialReason) {
        this.reasons = reasons;
        if (initialReason != null) {
            for (int i = 0; i < reasons.size(); i++) {
                if (reasons.get(i).getTitle().equals(initialReason)) {
                    selectedPosition = i;
                    break;
                }
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReasonBinding binding = ItemReasonBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReturnReason reason = reasons.get(position);
        holder.bind(reason, position == selectedPosition);
    }

    @Override
    public int getItemCount() {
        return reasons.size();
    }

    public ReturnReason getSelectedReason() {
        if (selectedPosition != -1) {
            return reasons.get(selectedPosition);
        }
        return null;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ItemReasonBinding binding;

        public ViewHolder(ItemReasonBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ReturnReason reason, boolean isSelected) {
            binding.tvTitle.setText(reason.getTitle());
            
            if (reason.getDescription() == null || reason.getDescription().isEmpty()) {
                binding.tvDescription.setVisibility(View.GONE);
            } else {
                binding.tvDescription.setVisibility(View.VISIBLE);
                binding.tvDescription.setText(reason.getDescription());
            }

            binding.rbSelect.setChecked(isSelected);
            
            if (isSelected) {
                binding.lnContainer.setBackgroundResource(R.drawable.bg_reason_item_selected);
            } else {
                binding.lnContainer.setBackgroundResource(R.drawable.bg_reason_item_unselected);
            }

            itemView.setOnClickListener(v -> {
                int oldPos = selectedPosition;
                selectedPosition = getAdapterPosition();
                notifyItemChanged(oldPos);
                notifyItemChanged(selectedPosition);
            });
        }
    }
}

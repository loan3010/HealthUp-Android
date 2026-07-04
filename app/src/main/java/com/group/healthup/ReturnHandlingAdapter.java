package com.group.healthup;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.group.healthup.databinding.ItemReasonBinding;
import com.group.models.ReturnHandling;
import java.util.List;

public class ReturnHandlingAdapter extends RecyclerView.Adapter<ReturnHandlingAdapter.ViewHolder> {
    private List<ReturnHandling> methods;
    private int selectedPosition = 0;

    public ReturnHandlingAdapter(List<ReturnHandling> methods, String initialMethod) {
        this.methods = methods;
        if (initialMethod != null) {
            for (int i = 0; i < methods.size(); i++) {
                if (methods.get(i).getTitle().equals(initialMethod)) {
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
        ReturnHandling method = methods.get(position);
        holder.bind(method, position == selectedPosition);
    }

    @Override
    public int getItemCount() {
        return methods.size();
    }

    public ReturnHandling getSelectedMethod() {
        if (selectedPosition != -1) {
            return methods.get(selectedPosition);
        }
        return null;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ItemReasonBinding binding;

        public ViewHolder(ItemReasonBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ReturnHandling method, boolean isSelected) {
            binding.tvTitle.setText(method.getTitle());
            binding.tvDescription.setText(method.getDescription());
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

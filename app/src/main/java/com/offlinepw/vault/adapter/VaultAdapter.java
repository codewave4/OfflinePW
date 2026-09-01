package com.offlinepw.vault.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.offlinepw.vault.R;
import com.offlinepw.vault.model.VaultItem;
import java.util.ArrayList;
import java.util.List;

public class VaultAdapter extends RecyclerView.Adapter<VaultAdapter.ViewHolder> {
    private final List<VaultItem> originalList = new ArrayList<>();
    private final List<VaultItem> filteredList = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(VaultItem item);
    }

    public VaultAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<VaultItem> items) {
        this.originalList.clear();
        this.originalList.addAll(items);
        filter("");
    }

    public void filter(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String lower = query.toLowerCase().trim();
            for (VaultItem item : originalList) {
                if (item.getTitle().toLowerCase().contains(lower) ||
                    (item.getUsername() != null && item.getUsername().toLowerCase().contains(lower)) ||
                    (item.getCategory() != null && item.getCategory().toLowerCase().contains(lower))) {
                    filteredList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vault_nordic, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VaultItem item = filteredList.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvCategory.setText(item.getCategory() != null ? item.getCategory().toUpperCase() : "GENERAL");

        // مخفی‌سازی امن اطلاعات حساس در کارت اصلی
        String val = item.getUsername();
        if (val != null && !val.trim().isEmpty()) {
            if (val.length() > 4) {
                // نمایش ۴ کاراکتر آخر برای شناسایی و مخفی کردن بقیه
                String last4 = val.substring(val.length() - 4);
                holder.tvUsername.setText("•••• •••• •••• " + last4);
            } else {
                holder.tvUsername.setText("••••••••");
            }
        } else {
            holder.tvUsername.setText("••••••••••••");
        }

        holder.cardRoot.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot;
        TextView tvTitle, tvUsername, tvCategory;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardRoot);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvCategory = itemView.findViewById(R.id.tvCategory);
        }
    }
}

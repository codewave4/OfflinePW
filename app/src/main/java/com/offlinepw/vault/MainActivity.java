private void updateThemeUI() {
        if (isDarkMode) {
            mainRootLayout.setBackgroundColor(Color.parseColor("#09090B"));
            appBarLayout.setBackgroundColor(Color.parseColor("#09090B"));
            tvAppTitle.setTextColor(Color.parseColor("#F4F4F5"));
            etSearch.setTextColor(Color.parseColor("#F4F4F5"));
            etSearch.setHintTextColor(Color.parseColor("#71717A"));
            
            btnLanguage.setBackgroundColor(Color.parseColor("#18181B"));
            btnLanguage.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#27272A")));
            btnLanguage.setTextColor(Color.parseColor("#F4F4F5"));
            
            btnThemeToggle.setBackgroundColor(Color.parseColor("#18181B"));
            btnThemeToggle.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#27272A")));
            btnThemeToggle.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#F4F4F5")));
        } else {
            mainRootLayout.setBackgroundColor(Color.parseColor("#FAFAFA"));
            appBarLayout.setBackgroundColor(Color.parseColor("#FAFAFA"));
            tvAppTitle.setTextColor(Color.parseColor("#09090B"));
            etSearch.setTextColor(Color.parseColor("#09090B"));
            etSearch.setHintTextColor(Color.parseColor("#A1A1AA"));
            
            btnLanguage.setBackgroundColor(Color.parseColor("#F4F4F5"));
            btnLanguage.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E4E4E7")));
            btnLanguage.setTextColor(Color.parseColor("#09090B"));
            
            btnThemeToggle.setBackgroundColor(Color.parseColor("#F4F4F5"));
            btnThemeToggle.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E4E4E7")));
            btnThemeToggle.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#09090B")));
        }
    }

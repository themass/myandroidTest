package com.qq.common.ui.view;

public interface IconPagerAdapter {
    /**
     * Get icon representing the page at {@code index} in the adapter.
     */
    int getIconResId(int index);

    /**
     * tab文本的布局（一个TextView）
     */
    int getTabViewStyleId();


    // From PagerAdapter
    int getCount();
}

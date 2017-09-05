package com.sspacee.common.ui.view;

import android.content.Context;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.sspacee.common.util.StringUtils;
import com.timeline.vpn.R;

public class SiteAutoCompleteTextView extends AppCompatAutoCompleteTextView {
    private String[] siteSufixs = new String[]{".com", ".com.cn", ".net", ".org", ".edu", "int", ".cn"};

    public SiteAutoCompleteTextView(Context context) {
        super(context);
        init(context);
    }

    public SiteAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }


    public SiteAutoCompleteTextView(Context context, AttributeSet attrs,
                                    int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }


    public void setAdapterString(String[] es) {
        if (es != null && es.length > 0)
            this.siteSufixs = es;
    }


    private void init(final Context context) {
        //adapter中使用默认的emailSufixs中的数据，可以通过setAdapterString来更改  
        this.setAdapter(new SiteAutoCompleteAdapter(context, R.layout.register_auto_complete_item, siteSufixs));
        //使得在输入1个字符之后便开启自动完成  
        this.setThreshold(1);
        this.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    String text = SiteAutoCompleteTextView.this.getText().toString();
                    //当该文本域重新获得焦点后，重启自动完成  
                    if (StringUtils.hasText(text))
                        performFiltering(text, 0);
                }
            }
        });
    }

    @Override
    protected void replaceText(CharSequence text) {
        //当我们在下拉框中选择一项时，android会默认使用AutoCompleteTextView中Adapter里的文本来填充文本域  
        //因为这里Adapter中只是存了常用email的后缀  
        //因此要重新replace逻辑，将用户输入的部分与后缀合并  
        String t = this.getText().toString();
        int index = t.lastIndexOf(".");
        if (index != -1)
            t = t.substring(0, index);
        super.replaceText(t + text);
    }


    @Override
    protected void performFiltering(CharSequence text, int keyCode) {
        //该方法会在用户输入文本之后调用，将已输入的文本与adapter中的数据对比，若它匹配  
        //adapter中数据的前半部分，那么adapter中的这条数据将会在下拉框中出现  
        String t = text.toString();
        //因为用户输入邮箱时，都是以字母，数字开始，而我们的adapter中只会提供以类似于"@163.com"  
        //的邮箱后缀，因此在调用super.performFiltering时，传入的一定是以"@"开头的字符串  
        int index = t.lastIndexOf(".");
        if (index == -1) {
            if (t.matches("^[a-zA-Z0-9_]+$")) {
                super.performFiltering(".", keyCode);
            } else
                this.dismissDropDown();//当用户中途输入非法字符时，关闭下拉提示框  
        } else {
            super.performFiltering(t.substring(index), keyCode);
        }
    }


    private class SiteAutoCompleteAdapter extends ArrayAdapter<String> {
        public SiteAutoCompleteAdapter(Context context, int textViewResourceId, String[] email_s) {
            super(context, textViewResourceId, email_s);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null)
                v = LayoutInflater.from(getContext()).inflate(
                        R.layout.register_auto_complete_item, null);
            TextView tv = (TextView) v.findViewById(R.id.tv);
            String t = SiteAutoCompleteTextView.this.getText().toString();
            int index = t.lastIndexOf(".");
            if (index != -1)
                t = t.substring(0, index);
            //将用户输入的文本与adapter中的email后缀拼接后，在下拉框中显示  
            tv.setText(t + getItem(position));
            return v;
        }

    }

}  
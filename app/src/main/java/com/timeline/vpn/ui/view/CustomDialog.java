//package com.timeline.vpn.ui.view;
//
//import android.app.Dialog;
//import android.content.Context;
//import android.os.Bundle;
//import android.view.ContextThemeWrapper;
//import android.view.Gravity;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.view.Window;
//import android.widget.AdapterView;
//import android.widget.BaseAdapter;
//import android.widget.Button;
//import android.widget.ListView;
//import android.widget.RadioButton;
//import android.widget.TextView;
//
//import com.netease.money.i.R;
//import com.timeline.vpn.R;
//
//public class CustomDialog extends Dialog {
//    private static int CHOICE_TYPE_SELECT = 1;
//    private static int CHOICE_TYPE_SINGLE = 2;
//    private static int CHOICE_TYPE_MULTI = 3;
//    private TextView titleView;
//    private TextView contentView;
//    private Button btnYes;
//    private Button btnNo;
//
//    private boolean titleShow;
//    private boolean contentShow;
//    private boolean yesShow;
//    private boolean noShow;
//
//    private String titleText;
//    private String contentText;
//    private int yesResId;
//    private int noResId;
//
//    private OnClickListener yesListener;
//    private OnClickListener noListener;
//
//    private int choiceType;
//    private String[] items;
//    private int singleCheckedItem;
//    private OnClickListener choiceListener;
//
//    private boolean contentCenter;
//
//    private boolean autoDismiss = true;
//
//    public CustomDialog(Context context) {
//        super(new ContextThemeWrapper(context, android.R.style.Theme_Holo_Light_Dialog_MinWidth));
//    }
//
//    public CustomDialog(Context context, int theme) {
//        super(context, theme);
//    }
//
//    protected CustomDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
//        super(context, cancelable, cancelListener);
//    }
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//        setContentView(R.layout.custom_dialog);
//        titleView = (TextView) findViewById(R.id.title);
//        contentView = (TextView) findViewById(R.id.content);
//        btnYes = (Button) findViewById(R.id.button_yes);
//        btnNo = (Button) findViewById(R.id.button_no);
//        final ListView listView = (ListView) findViewById(R.id.list);
//        if (titleShow) {
//            titleView.setVisibility(View.VISIBLE);
//            titleView.setText(titleText);
//        }
//        if (contentShow) {
//            contentView.setVisibility(View.VISIBLE);
//            contentView.setText(contentText);
//            if (contentCenter) {
//                contentView.setGravity(Gravity.CENTER);
//            }
//        }
//        if (yesShow) {
//            btnYes.setVisibility(View.VISIBLE);
//            btnYes.setText(yesResId);
//            btnYes.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    if (yesListener != null) {
//                        yesListener.onClick(CustomDialog.this, btnYes.getId());
//                    }
//                    if (autoDismiss) {
//                        dismiss();
//                    }
//                }
//            });
//        }
//        if (noShow) {
//            btnNo.setVisibility(View.VISIBLE);
//            btnNo.setText(noResId);
//            btnNo.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    if (noListener != null) {
//                        noListener.onClick(CustomDialog.this, btnNo.getId());
//                    }
//                    if (autoDismiss) {
//                        dismiss();
//                    }
//                }
//            });
//        }
//        if (choiceType == CHOICE_TYPE_SINGLE) {
//            listView.setVisibility(View.VISIBLE);
//            final SingleAdapter adapter = new SingleAdapter(items, singleCheckedItem, getLayoutInflater());
//            listView.setAdapter(adapter);
//            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//                @Override
//                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                    singleCheckedItem = position;
//                    adapter.notifyDataSetChanged();
//                    if (choiceListener != null) {
//                        choiceListener.onClick(CustomDialog.this, position);
//                    }
//                    if (autoDismiss) {
//                        dismiss();
//                    }
//                }
//            });
//        }
//    }
//
//    public CustomDialog setDialogTitle(int resId) {
//        this.titleShow = true;
//        this.titleText = getContext().getResources().getString(resId);
//        return this;
//    }
//
//    public CustomDialog setDialogTitle(String title) {
//        this.titleShow = true;
//        this.titleText = title;
//        return this;
//    }
//
//    public CustomDialog setContent(int resId) {
//        this.contentShow = true;
//        this.contentText = getContext().getResources().getString(resId);
//        return this;
//    }
//
//    public CustomDialog setContent(String content) {
//        this.contentShow = true;
//        this.contentText = content;
//        return this;
//    }
//
//    public CustomDialog setContentCenter() {
//        this.contentCenter = true;
//        return this;
//    }
//
//    public CustomDialog setPositiveButton(int resId, final OnClickListener listener) {
//        this.yesShow = true;
//        this.yesResId = resId;
//        this.yesListener = listener;
//        return this;
//    }
//
//    public CustomDialog setNegativeButton(int resId, final OnClickListener listener) {
//        this.noShow = true;
//        this.noResId = resId;
//        this.noListener = listener;
//        return this;
//    }
//
//    public CustomDialog setAutoDismiss(boolean autoDismiss) {
//        this.autoDismiss = autoDismiss;
//        return this;
//    }
//
//    public CustomDialog setSingleChoiceItems(String[] items, int checkedItem, final OnClickListener listener) {
//        this.choiceType = CHOICE_TYPE_SINGLE;
//        this.items = items;
//        this.singleCheckedItem = checkedItem;
//        this.choiceListener = listener;
//        return this;
//    }
//
//    static class SingleAdapter extends BaseAdapter {
//        private int checkedItem;
//        private String[] items;
//        private LayoutInflater inflater;
//
//        SingleAdapter(String[] items, int checkedItem, LayoutInflater inflater) {
//            this.items = items;
//            this.inflater = inflater;
//            this.checkedItem = checkedItem;
//        }
//
//        @Override
//        public int getCount() {
//            return items.length;
//        }
//
//        @Override
//        public Object getItem(int position) {
//            return items[position];
//        }
//
//        @Override
//        public long getItemId(int position) {
//            return position;
//        }
//
//        @Override
//        public View getView(int position, View convertView, ViewGroup parent) {
//            if (convertView == null) {
//                convertView = inflater.inflate(R.layout.custom_dialog_single_choice, null);
//            }
//            TextView itemText = (TextView) convertView.findViewById(R.id.text);
//            RadioButton radioButton = (RadioButton) convertView.findViewById(R.id.radio);
//            itemText.setText(items[position]);
//            if (position == checkedItem) {
//                radioButton.setChecked(true);
//            } else {
//                radioButton.setChecked(false);
//            }
//            return convertView;
//        }
//    }
//}

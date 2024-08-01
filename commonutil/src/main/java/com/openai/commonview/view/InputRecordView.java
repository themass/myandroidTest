package com.openai.commonview.view;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.openai.commonutil.R;
import com.openai.commonutil.ToastUtil;
import com.openai.commonview.view.widget.RecordButton;
import com.openai.commonview.view.widget.StateButton;
import com.openai.commonview.view.widget.util.ChatUiHelper;

/**
 * Created by openapi on 2016/9/6.
 */
public class InputRecordView extends LinearLayout {

    public static final int REQUEST_CODE_IMAGE = 0000;
    public static final int REQUEST_CODE_VEDIO = 1111;
    public static final int REQUEST_CODE_FILE = 2222;
    EditText mEtContent;
    RelativeLayout mRlBottomLayout;//表情,添加底部布局
    ImageView mIvAdd;
    ImageView mIvEmo;
    StateButton mBtnSend;//发送按钮
    ImageView mIvAudio;//录音图片
    RecordButton mBtnAudio;//录音按钮
    LinearLayout mLlEmotion;//表情布局
    LinearLayout mLlAdd;//添加布局
    LinearLayout mLlContent;
    ChatUiHelper mUiHelper;
    PicSelectListener picSelectListener;

    public InputRecordView(Context context) {
        super(context);
        setupView();
    }

    public InputRecordView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupView();
    }

    public InputRecordView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupView();
    }

    private void setupView() {
        LayoutInflater mInflater = LayoutInflater.from(getContext());
        View myView = mInflater.inflate(R.layout.myinput_record_view, null);
        addView(myView);
        mEtContent = findViewById(R.id.et_content);
        mRlBottomLayout = findViewById(R.id.bottom_layout);
        mIvAdd = findViewById(R.id.ivAdd);
        mIvEmo = findViewById(R.id.ivEmo);
        mBtnSend = findViewById(R.id.btn_send);
        mIvAudio = findViewById(R.id.ivAudio);
        mBtnAudio = findViewById(R.id.btnAudio);
        mLlEmotion = findViewById(R.id.rlEmotion);
        mLlAdd = findViewById(R.id.llAdd);
        mLlContent = findViewById(R.id.llContent);
        mUiHelper = ChatUiHelper.with((Activity) getContext());
        mUiHelper.bindContentLayout(mLlContent)
                .bindttToSendButton(mBtnSend)
                .bindEditText(mEtContent)
                .bindBottomLayout(mRlBottomLayout)
                .bindEmojiLayout(mLlEmotion)
                .bindAddLayout(mLlAdd)
                .bindToAddButton(mIvAdd)
                .bindToEmojiButton(mIvEmo)
                .bindAudioBtn(mBtnAudio)
                .bindAudioIv(mIvAudio)
                .bindEmojiData(this);
    }

    public void onViewClicked(View view) {
        int id = view.getId();
        if (id == R.id.btn_send) {
            picSelectListener.sendMessage(mEtContent.getText().toString());
            mEtContent.setText("");
        } else if (id == R.id.rlPhoto) {
            ToastUtil.showShort(R.string.coming_soon_please);
//                PictureFileUtil.openGalleryPic(picSelectListener.picSelect(), REQUEST_CODE_IMAGE);
        } else if (id == R.id.rlVideo) {
            ToastUtil.showShort(R.string.coming_soon_please);
//                PictureFileUtil.openGalleryAudio(picSelectListener.picSelect(), REQUEST_CODE_VEDIO);
        } else if (id == R.id.rlFile) {
            ToastUtil.showShort(R.string.coming_soon_please);
//                PictureFileUtil.openFile(ChatActivity.this,REQUEST_CODE_FILE);
        } else if (id == R.id.rlLocation) {
            ToastUtil.showShort(R.string.coming_soon_please);
        }
    }

    public void setAudioFinishListener(RecordButton.OnFinishedRecordListener listener) {
        ((RecordButton) mBtnAudio).setOnFinishedRecordListener(listener);
    }

    public void setPicSelectListener(PicSelectListener listener) {
        this.picSelectListener = listener;
    }

    public void closeKeypad() {
        mUiHelper.hideBottomLayout(false);
        mUiHelper.hideSoftInput();
        mEtContent.clearFocus();
        mIvEmo.setImageResource(R.drawable.ic_emoji);
    }

    public static interface PicSelectListener {
        public void sendMessage(String content);

        public Activity picSelect();
    }

}

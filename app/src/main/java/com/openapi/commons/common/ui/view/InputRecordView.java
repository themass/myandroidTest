package com.openapi.commons.common.ui.view;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.hrl.chaui.util.ChatUiHelper;
import com.hrl.chaui.widget.RecordButton;
import com.hrl.chaui.widget.StateButton;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.ks.chatfree.R;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by openapi on 2016/9/6.
 */
public class InputRecordView extends LinearLayout {

    public static final int REQUEST_CODE_IMAGE = 0000;
    public static final int REQUEST_CODE_VEDIO = 1111;
    public static final int REQUEST_CODE_FILE = 2222;
    @BindView(R.id.et_content)
    EditText mEtContent;
    @BindView(R.id.bottom_layout)
    RelativeLayout mRlBottomLayout;//表情,添加底部布局
    @BindView(R.id.ivAdd)
    ImageView mIvAdd;
    @BindView(R.id.ivEmo)
    ImageView mIvEmo;
    @BindView(R.id.btn_send)
    StateButton mBtnSend;//发送按钮
    @BindView(R.id.ivAudio)
    ImageView mIvAudio;//录音图片
    @BindView(R.id.btnAudio)
    RecordButton mBtnAudio;//录音按钮
    @BindView(R.id.rlEmotion)
    LinearLayout mLlEmotion;//表情布局
    @BindView(R.id.llAdd)
    LinearLayout mLlAdd;//添加布局
    @BindView(R.id.llContent)
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
        ButterKnife.bind(this);
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

    @OnClick({R.id.btn_send, R.id.rlPhoto, R.id.rlVideo, R.id.rlLocation, R.id.rlFile})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btn_send:
                picSelectListener.sendMessage(mEtContent.getText().toString());
                mEtContent.setText("");
                break;
            case R.id.rlPhoto:
                ToastUtil.showShort(R.string.coming_soon_please);
//                PictureFileUtil.openGalleryPic(picSelectListener.picSelect(), REQUEST_CODE_IMAGE);
                break;
            case R.id.rlVideo:
                ToastUtil.showShort(R.string.coming_soon_please);
//                PictureFileUtil.openGalleryAudio(picSelectListener.picSelect(), REQUEST_CODE_VEDIO);
                break;
            case R.id.rlFile:
                ToastUtil.showShort(R.string.coming_soon_please);
//                PictureFileUtil.openFile(ChatActivity.this,REQUEST_CODE_FILE);
                break;
            case R.id.rlLocation:
                ToastUtil.showShort(R.string.coming_soon_please);
                break;
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

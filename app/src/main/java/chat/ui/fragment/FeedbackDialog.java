package chat.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import com.openapi.ks.chat.R;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialog;

import com.donkingliang.labels.LabelsView;
import com.openai.commonutil.ToastUtil;
import com.openai.net.BaseService;
import com.openai.net.request.CommonResponse;
import com.openapi.ks.myapp.bean.form.FeedbackContentForm;
import com.openapi.ks.myapp.bean.vo.AsrResponseVo;
import com.openapi.ks.myapp.bean.vo.NullReturnVo;
import com.openapi.ks.myapp.constant.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import chat.ui.data.model.Message;

public class FeedbackDialog extends AppCompatDialog {
    private ImageView ivClose;
    private Button send;
    private EditText feedbackEditText;
    private BaseService baseService;
    private Message message;
    private LabelsView labelsView;
    private String  FEEDBACK = "FEEADBACK";
    private OnFeedbackSubmitListener onFeedbackSubmitListener;
    public FeedbackDialog(@NonNull Context context, Message message) {
        super(context, R.style.FeedbackDialogTheme);
        baseService = new BaseService();
        baseService.setup(context);
        this.message = message;
    }
    public void setListener(OnFeedbackSubmitListener  listener){
        this.onFeedbackSubmitListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置自定义布局
        setContentView(R.layout.layout_chat_feedback);
        ivClose = findViewById(R.id.id_close);
        feedbackEditText = findViewById(R.id.edit_text);
        send = findViewById(R.id.bt_submit);
        labelsView = findViewById(R.id.labels);
        List<String> label = Arrays.asList(getContext().getResources().getStringArray(R.array.chat_feed_reson));
        labelsView.setLabels(label); //直接设置一个字符串数组就可以了。
        ivClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 获取输入内容
                if(labelsView.getSelectLabelDatas().size()==0){
                    dismiss();
                }
                String feedback = feedbackEditText.getText().toString();
                FeedbackContentForm feedbackContentForm = new FeedbackContentForm();
                feedbackContentForm.setContent(message.getText());
                feedbackContentForm.setId(message.getId());
                feedbackContentForm.setTag(labelsView.getSelectLabelDatas().toString());
                feedbackContentForm.setContent(feedback);
                baseService.postData(Constants.getUrl(Constants.CHAT_FEEDBACK_URL),feedbackContentForm,null, null, FEEDBACK, NullReturnVo.class);
                dismiss();
                if(onFeedbackSubmitListener!=null){
                    onFeedbackSubmitListener.onSubmit(message);
                }
            }
        });
        // 设置点击布局之外的部分关闭对话框
        setCanceledOnTouchOutside(true);
    }
    @Override
    protected void onStart() {
        super.onStart();
        // 获取窗口
        Window window = getWindow();
        if (window != null) {
            // 设置对话框在屏幕底部出现
            window.setGravity(Gravity.BOTTOM);
            // 设置对话框的动画效果
            window.setWindowAnimations(R.style.FeedbackDialogAnim);
        }
    }
    public static interface OnFeedbackSubmitListener{
        public void onSubmit(Message message);
    }
}
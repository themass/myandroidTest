package chat.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.openapi.ks.moviefree1.R;

import chat.ui.data.fixtures.DialogsFixtures;
import chat.ui.data.model.Dialog;
import tz.co.hosannahighertech.messagekit.dialogs.DialogsList;
import tz.co.hosannahighertech.messagekit.dialogs.DialogsListAdapter;

public class CustomLayoutDialogsActivity extends DemoDialogsActivity {

    public static void open(Context context) {
        context.startActivity(new Intent(context, CustomLayoutDialogsActivity.class));
    }

    private DialogsList dialogsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_layout_dialogs);
    }
    public void setupView() {
        super.setupView();
        dialogsList = (DialogsList) findViewById(R.id.dialogsList);
        initAdapter();
    }

    @Override
    public void onDialogClick(Dialog dialog) {
        CustomLayoutMessagesActivity.open(this);
    }

    private void initAdapter() {
        super.dialogsAdapter = new DialogsListAdapter<>(R.layout.item_custom_dialog, super.imageLoader);
        super.dialogsAdapter.setItems(DialogsFixtures.getDialogs());

        super.dialogsAdapter.setOnDialogClickListener(this);
        super.dialogsAdapter.setOnDialogLongClickListener(this);

        dialogsList.setAdapter(super.dialogsAdapter);
    }
}

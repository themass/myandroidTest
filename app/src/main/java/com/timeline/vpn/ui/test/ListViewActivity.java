package com.timeline.vpn.ui.test;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.sspacee.common.ui.base.SlidingActivity;
import com.timeline.vpn.R;

import java.util.ArrayList;
import java.util.List;

public class ListViewActivity extends SlidingActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_activity_list_view);

        ListView listView = (ListView) findViewById(R.id.list_view);
        List<String> list = new ArrayList<>(20);
        for (int i = 0; i < 20; i++) {
            list.add("第" + i + "条记录");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                startActivity(new Intent(ListViewActivity.this, NormalActivity.class));
            }
        });
    }
    protected boolean enableSliding() {
        return true;
    }
}

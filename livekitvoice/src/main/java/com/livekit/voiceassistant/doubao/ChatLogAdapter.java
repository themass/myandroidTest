package com.livekit.voiceassistant.doubao;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

import livekit.LivekitModels;

// 假设 Transcription 类已经定义

public class ChatLogAdapter extends RecyclerView.Adapter<ChatLogAdapter.ChatLogViewHolder> {

    private List<LivekitModels.Transcription> segments;
    private List<LivekitModels.Transcription> localSegments;

    public ChatLogAdapter(List<LivekitModels.Transcription> segments) {
        this.segments = segments;
        this.localSegments = new ArrayList<>();
    }

    public void setSegments(List<LivekitModels.Transcription> segments, List<LivekitModels.Transcription> localSegments) {
        this.segments = segments;
        this.localSegments = localSegments;
    }

    @NonNull
    @Override
    public ChatLogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ChatLogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatLogViewHolder holder, int position) {
        LivekitModels.Transcription segment = segments.get(position);
        if (localSegments.contains(segment)) {
            // 本地参与者的转录内容
            holder.textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        } else {
            // 其他参与者的转录内容
            holder.textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        }
        holder.textView.setText(segment.getSegmentsList().toString());
    }

    @Override
    public int getItemCount() {
        return segments.size();
    }

    public static class ChatLogViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public ChatLogViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}
package com.timeline.vpn.service;

import com.squareup.leakcanary.AnalysisResult;
import com.squareup.leakcanary.DisplayLeakService;
import com.squareup.leakcanary.HeapDump;
import com.timeline.vpn.common.net.HttpUtils;
import com.timeline.vpn.common.util.LogUtil;

public class LeakUploadService extends DisplayLeakService {
  @Override
  protected void afterDefaultHandling(HeapDump heapDump, AnalysisResult result, String leakInfo) {
    if (!result.leakFound || result.excludedLeak) {
      return;
    }
    LogUtil.i("leakInfo="+leakInfo);
    HttpUtils.upload(this,null,heapDump.heapDumpFile,leakInfo);
  }
}
package com.openapi.ks.dh.duix;

/**
 * The SDK-internal ncnn handle (our package). The JNI binding itself lives in
 * {@code ai.guiji.duix.DuixNcnn} — the exact FQN the stock libgjduix.so exports
 * (Task-6 ruling: vendored Java keeps our packages, the stock .so keeps its symbols).
 *
 * Do NOT add a {@code System.loadLibrary} static block here: the natives are loaded
 * by absolute path from the AssetStore cache via {@code com.openapi.ks.dh.DuixNcnnLoader}
 * before any engine call, and {@link com.openapi.ks.dh.DuixRuntime} gates availability
 * on that load. {@link #bridge()} fails with UnsatisfiedLinkError if anyone calls native
 * work before the loader ran.
 */
public class DuixNcnn
{
    private final ai.guiji.duix.DuixNcnn bridge = new ai.guiji.duix.DuixNcnn();

    public int alloc(int taskid,int mincalc,int width,int height){ return bridge.alloc(taskid,mincalc,width,height); }
    public int free(int taskid){ return bridge.free(taskid); }
    public int initPcmex(int maxsize,int minoff,int minblock,int maxblock,int rgb){ return bridge.initPcmex(maxsize,minoff,minblock,maxblock,rgb); }
    public int initWenet(String fnwenet){ return bridge.initWenet(fnwenet); }
    public int initMunet(String fnparam,String fnbin,String fnmask){ return bridge.initMunet(fnparam,fnbin,fnmask); }
    public int initMunetex(String fnparam,String fnbin,String fnmask, int kind){ return bridge.initMunetex(fnparam,fnbin,fnmask,kind); }

    public long newsession(){ return bridge.newsession(); }
    public int finsession(long sessid){ return bridge.finsession(sessid); }
    public int consession(long sessid){ return bridge.consession(sessid); }
    public int allcnt(long sessid){ return bridge.allcnt(sessid); }
    public int readycnt(long sessid){ return bridge.readycnt(sessid); }
    public int pushpcm(long sessid,byte[] arrbuf,int size, int kind){ return bridge.pushpcm(sessid,arrbuf,size,kind); }

    public int filerst(long sessid,String picfn,String mskfn,
        int[] arrbox,String fgpic,int index, byte[] arrimg,byte[] arrmsk,int imgsize){
        return bridge.filerst(sessid,picfn,mskfn,arrbox,fgpic,index,arrimg,arrmsk,imgsize);
    }

    public int bufrst(long sessid, int[] arrbox,int index, byte[] arrimg,int imgsize){ return bridge.bufrst(sessid,arrbox,index,arrimg,imgsize); }

    public int fileload(String picfn,String mskfn,int width,int height,
         byte[] arrpic,byte[] arrmsk,int imgsize){
        return bridge.fileload(picfn,mskfn,width,height,arrpic,arrmsk,imgsize);
    }

    public int startgpg(String picfn,String gpgfn){ return bridge.startgpg(picfn,gpgfn); }
    public int stopgpg(){ return bridge.stopgpg(); }
    public int processmd5(int kind,String infn,String outfn){ return bridge.processmd5(kind,infn,outfn); }
}

"""直接打 testserverV 拍照解题接口的探针（不经过 App）。

服务端要求 UA 里带 channel/version/签名，裸 curl 一律得到 errno 10000，这不是服务端故障。
响应 data 是 AES-ECB 加密的 hex，密钥是 UA 末 16 位。需要 pycryptodome。
用法: python3 photosolve_probe.py  # 打 health.json
"""
import hashlib,time,sys,urllib.request,uuid,json
BASE="https://api.ok123find.top/vvv/api"
devid="androidimei82892812384933941456"
def ua():
    t=int(time.time()*1000)
    key=hashlib.md5(f"{devid}|{t}".encode()).hexdigest()
    return f"Dalvik/2.1.0 (Linux; U; Android 11; ADY-AL00 Build/HUAWEIADY-AL00) SEX/1.1.0.66,channel=GOOGLEMARKET,cpu=arm64-v8a,Webkit/ ,IE{t}{key}", t, key
def post(path, files=None, fields=None, token=None):
    u,t,key=ua()
    if not files:
        boundary='----b'+uuid.uuid4().hex
        body=b''
        for k,v in (fields or {}).items():
            body+=f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
        body+=f'--{boundary}--\r\n'.encode()
        ctype=f'multipart/form-data; boundary={boundary}'
    else:
        boundary='----b'+uuid.uuid4().hex
        body=b''
        for k,v in (fields or {}).items():
            body+=f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
        for name,fn,ct,data in files:
            body+=f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{fn}"\r\nContent-Type: {ct}\r\n\r\n'.encode()+data+b'\r\n'
        body+=f'--{boundary}--\r\n'.encode()
        ctype=f'multipart/form-data; boundary={boundary}'
    req=urllib.request.Request(BASE+path, data=body, method='POST')
    req.add_header('User-Agent',u); req.add_header('Content-Type',ctype)
    req.add_header('Devid',devid)
    req.add_header('Referer','http://www.ok123find.top/')
    req.add_header('lang','zh')
    if token: req.add_header('1111',token)  # SEX_TEMP -> HTTP_TOKEN_SEX_KEY
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.status, r.read().decode('utf-8','ignore'), key
    except Exception as e:
        return None, str(e), key
if __name__=='__main__':
    s,b,key=post('/photosolve/v1/health.json', fields={})
    print('HTTP',s); print('authKey',key); print(b[:800])

def decrypt(data_hex, key):
    from Crypto.Cipher import AES
    from Crypto.Util.Padding import unpad
    k=key.encode()[:16]
    ct=bytes.fromhex(data_hex)
    c=AES.new(k, AES.MODE_ECB)
    return unpad(c.decrypt(ct),16).decode('utf-8')

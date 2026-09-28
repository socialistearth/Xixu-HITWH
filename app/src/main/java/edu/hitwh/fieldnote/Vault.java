package edu.hitwh.fieldnote;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** App-private AES-GCM file. Never logs or exports credentials. */
final class Vault {
    private static final Object LOCK = new Object();
    private static final String ALIAS = "xixu.local.v1";
    private final Context context;
    Vault(Context context) { this.context = context.getApplicationContext(); }
    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    private AtomicFile file() { return new AtomicFile(new File(context.getFilesDir(),"vault.bin")); }
    JSONObject load() throws Exception { synchronized (LOCK) { return read(); } }
    private JSONObject read() throws Exception {
        AtomicFile f=file(); if(!f.getBaseFile().exists()) return new JSONObject();
        byte[] all=f.readFully(); if(all.length<29||all.length>4_000_000)throw new IOException("本地数据无法读取");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,all,0,12));
        return new JSONObject(new String(c.doFinal(all,12,all.length-12),java.nio.charset.StandardCharsets.UTF_8));
    }
    private void write(JSONObject value) throws Exception {
        byte[] data=value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(data.length>3_500_000)throw new IOException("本地数据过大，请减少导入条目");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key());
        byte[] enc=c.doFinal(data); AtomicFile f=file(); FileOutputStream out=null;
        try {out=f.startWrite();out.write(c.getIV());out.write(enc);f.finishWrite(out);}
        catch(Exception e){if(out!=null)f.failWrite(out);throw e;}
    }
    interface Edit { void apply(JSONObject value) throws Exception; }
    void update(Edit edit) throws Exception { synchronized(LOCK){JSONObject value=read();edit.apply(value);write(value);} }
    void clear() {synchronized(LOCK){file().delete();}}
}

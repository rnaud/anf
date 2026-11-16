package io.anf.games;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.URLConnection;
import java.util.*;

public class PlayerActivity extends Activity {
    WebView web; File root; String host; ValueCallback<Uri[]> fileCallback;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::confirmExit);
        try{String id=PackageStore.validId(getIntent().getStringExtra("id"));root=new PackageStore(new File(getFilesDir(),"games")).current(id);host=id+".games.anf.invalid";}
        catch(Exception e){finish();return;}
        if(root==null){finish();return;}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        Button back=new Button(this);back.setText("‹ Library  ·  "+getIntent().getStringExtra("title"));back.setAllCaps(false);back.setOnClickListener(v->confirmExit());layout.addView(back);
        web=new WebView(this);layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(layout);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        // Packages are served at a stable, isolated HTTPS origin. No native JavaScript bridge.
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){return resource(req);}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){
                if(host.equals(req.getUrl().getHost())&&"https".equals(req.getUrl().getScheme()))return false;
                if(req.isForMainFrame()&&"https".equals(req.getUrl().getScheme()))try{startActivity(new Intent(Intent.ACTION_VIEW,req.getUrl()));}catch(Exception ignored){}
                return true;
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())new AlertDialog.Builder(PlayerActivity.this).setMessage("The game could not start. Return to the library and try downloading it again.").setPositiveButton("Library",(d,w)->finish()).show();}
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){((android.view.ViewGroup)v.getParent()).removeView(v);v.destroy();web=null;new AlertDialog.Builder(PlayerActivity.this).setMessage("The game player stopped. Try again, or lower the game’s graphics settings.").setPositiveButton("Library",(d,w)->finish()).setOnCancelListener(d->finish()).show();return true;}
        });
        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient(){@Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request){return resource(request);}});
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=cb;
                try{startActivityForResult(params.createIntent(),1);return true;}catch(Exception e){fileCallback=null;return false;}
            }
        });
        web.loadUrl("https://"+host+"/index.html");
    }
    WebResourceResponse resource(WebResourceRequest request){
        Uri uri=request.getUrl();
        if("script".equalsIgnoreCase(request.getRequestHeaders().get("Service-Worker")))return missing();
        if(!host.equals(uri.getHost())||!"https".equals(uri.getScheme()))return null;
        try{
            String path=uri.getPath();if(path==null||path.endsWith("/"))path=(path==null?"/":path)+"index.html";
            File file=new File(root,path.substring(1));
            if(!file.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator)||!file.isFile())return missing();
            String mime=URLConnection.guessContentTypeFromName(file.getName());
            if(path.endsWith(".js")||path.endsWith(".mjs"))mime="text/javascript";
            if(path.endsWith(".wasm"))mime="application/wasm";
            if(path.endsWith(".webp"))mime="image/webp";
            if(path.endsWith(".json"))mime="application/json";
            if(path.endsWith(".css"))mime="text/css";
            Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("Cross-Origin-Opener-Policy","same-origin");headers.put("Cross-Origin-Embedder-Policy","credentialless");
            return new WebResourceResponse(mime==null?"application/octet-stream":mime,null,200,"OK",headers,new FileInputStream(file));
        }catch(Exception e){return missing();}
    }
    WebResourceResponse missing(){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream("Missing game file".getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    void confirmExit(){new AlertDialog.Builder(this).setTitle("Return to your library?").setMessage("Unsaved progress in the current session may be lost.").setNegativeButton("Keep playing",null).setPositiveButton("Library",(d,w)->finish()).show();}
    @Override public boolean onKeyDown(int key,android.view.KeyEvent event){if(Build.VERSION.SDK_INT<33&&key==android.view.KeyEvent.KEYCODE_BACK){confirmExit();return true;}return super.onKeyDown(key,event);}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==1&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){if(fileCallback!=null)fileCallback.onReceiveValue(null);ServiceWorkerController.getInstance().setServiceWorkerClient(null);if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}

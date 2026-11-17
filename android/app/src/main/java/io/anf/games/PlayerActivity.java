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
    AlertDialog exitDialog; boolean exiting;
    android.window.OnBackInvokedCallback backCallback;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{String id=PackageStore.validId(getIntent().getStringExtra("id"));root=new PackageStore(new File(getFilesDir(),"games")).current(id);host=id+".games.anf.invalid";}
        catch(Exception e){finish();return;}
        if(root==null){finish();return;}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        float density=getResources().getDisplayMetrics().density;
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding((int)(8*density),0,(int)(12*density),0);bar.setBackgroundColor(0xfff1f0e6);
        Button back=new Button(this);back.setText("‹ Library");back.setTextSize(13);back.setAllCaps(false);
        back.setContentDescription("Exit game and return to library");back.setTextColor(0xff244136);
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(0xffe0e6d8);shape.setCornerRadius(20*density);
        back.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22315d4c),shape,null));
        back.setOnClickListener(v->confirmExit());bar.addView(back,new LinearLayout.LayoutParams((int)(100*density),(int)(40*density)));
        TextView name=new TextView(this);name.setText(getIntent().getStringExtra("title"));name.setTextColor(0xff244136);name.setTextSize(15);name.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(name,new LinearLayout.LayoutParams(0,-1,1));layout.addView(bar,new LinearLayout.LayoutParams(-1,(int)(48*density)));
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
        // Register above WebView's own navigation callbacks so Back always offers an exit.
        if(Build.VERSION.SDK_INT>=33){
            backCallback=this::confirmExit;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY,backCallback);
        }
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
    void confirmExit(){
        if(exiting||isFinishing()||(exitDialog!=null&&exitDialog.isShowing()))return;
        if(web!=null){web.onPause();web.pauseTimers();}
        exitDialog=new AlertDialog.Builder(this)
                .setTitle("Exit "+getIntent().getStringExtra("title")+"?")
                .setMessage("Return to your game shelf. Unsaved progress in this session may be lost.")
                .setNegativeButton("Keep playing",(d,w)->{})
                .setPositiveButton("Exit game",(d,w)->exitGame())
                .create();
        exitDialog.setOnDismissListener(d->{
            exitDialog=null;
            if(!exiting&&!isFinishing()&&web!=null){web.resumeTimers();web.onResume();web.requestFocus();}
        });
        exitDialog.show();
    }
    void exitGame(){
        exiting=true;
        if(web!=null){
            web.stopLoading();web.resumeTimers();
            ((android.view.ViewGroup)web.getParent()).removeView(web);
            web.removeAllViews();web.destroy();web=null;
        }
        finish();
    }
    // Predictive gestures are handled above; this fallback is for physical keys and older OSes.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        // Catch physical Back before the focused WebView can consume it (including handhelds).
        // Modern system gestures use the registered OnBackInvokedCallback instead.
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){
            if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())confirmExit();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==1&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null&&(exitDialog==null||!exitDialog.isShowing())){web.resumeTimers();web.onResume();}}
    @Override protected void onDestroy(){
        exiting=true;
        if(Build.VERSION.SDK_INT>=33&&backCallback!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        if(exitDialog!=null)exitDialog.dismiss();
        if(fileCallback!=null)fileCallback.onReceiveValue(null);ServiceWorkerController.getInstance().setServiceWorkerClient(null);if(web!=null){web.stopLoading();web.resumeTimers();web.destroy();}super.onDestroy();}
}

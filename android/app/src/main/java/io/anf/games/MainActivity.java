package io.anf.games;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
    static final String CATALOG_URL = "https://github.com/rnaud/anf/releases/download/game-library/catalog.json";
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    final List<Game> games = new ArrayList<>();
    PackageStore store; LinearLayout list; TextView status; Button refresh;
    volatile String busy = ""; Future<?> task; Game importing;
    int ink=Color.rgb(36,65,54), muted=Color.rgb(99,110,101);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store=new PackageStore(new File(getFilesDir(),"games"));
        try {
            File cached=new File(getFilesDir(),"catalog.json");
            try { if(cached.exists()) load(Files.readAllBytes(cached.toPath())); } catch(Exception ignored) { games.clear(); }
            if(games.isEmpty()) try(InputStream in=getAssets().open("catalog.json")) { load(read(in)); }
        } catch(Exception e) { message("Cannot read the game library: "+e.getMessage()); }
        render();
    }
    byte[] read(InputStream in) throws IOException { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray(); }
    List<Game> parse(byte[] bytes) throws Exception {
        JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(root.getInt("schema")!=1)throw new IOException("Unsupported catalog version");
        JSONArray a=root.getJSONArray("games"); List<Game> next=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for(int i=0;i<a.length();i++){Game g=new Game(a.getJSONObject(i));if(!ids.add(g.id))throw new IOException("Duplicate game");next.add(g);}
        if(next.isEmpty())throw new IOException("Empty catalog"); return next;
    }
    void load(byte[] bytes) throws Exception { games.clear();games.addAll(parse(bytes)); }
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(6),0,dp(6));return t;}
    Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(245,244,237));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(22),dp(22),dp(22),dp(28));scroll.addView(list);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(scroll);
        TextView brand=text("anf / games",18,ink);brand.setTypeface(null,Typeface.BOLD);list.addView(brand);
        TextView title=text("A little escape.",36,ink);title.setTypeface(Typeface.create("serif",Typeface.NORMAL));list.addView(title);
        list.addView(text("Your games, ready when you are. Download once, then play offline.",16,muted));
        status=text("Browser games play here. Native games open in their own apps.",13,muted);list.addView(status);
        refresh=button("Check for game updates",this::refreshCatalog);refresh.setEnabled(busy.isEmpty());list.addView(refresh);
        for(Game g:games)card(g);
        list.addView(text("Game progress stays on this device. Removing a download keeps its web saves; uninstalling anf games clears them.",12,muted));
    }
    void card(Game g){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(0,dp(26),0,dp(12));list.addView(card);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(g.title+" artwork");
        try(InputStream in=getAssets().open("art/"+g.art)){image.setImageDrawable(Drawable.createFromStream(in,null));}catch(IOException ignored){}
        card.addView(image,new LinearLayout.LayoutParams(-1,dp(190)));
        TextView heading=text(g.title,27,ink);heading.setTypeface(Typeface.create("serif",Typeface.NORMAL));card.addView(heading);
        card.addView(text(g.description,15,muted));
        if(g.kind.equals("native")){
            card.addView(text("Separate Android app",12,muted));
            card.addView(button("Open / install "+g.title,()->openNative(g)));return;
        }
        File current=store.current(g.id);boolean ready=current!=null;
        String label=ready?"Downloaded · ready offline":(g.download.isEmpty()?"Package not published yet":String.format(Locale.US,"Download · %.1f MB",g.bytes/1048576.0));
        card.addView(text(label,12,muted));
        if(g.fileCount>0)card.addView(text(String.format(Locale.US,"Complete game · %d files · %.1f MB installed",g.fileCount,g.expandedBytes/1048576.0),12,muted));
        if(ready){Button play=button("Play",()->startActivity(new Intent(this,PlayerActivity.class).putExtra("id",g.id).putExtra("title",g.title)));play.setEnabled(!busy.equals(g.id));card.addView(play);}
        if(!g.download.isEmpty()&&(!ready||!current.getName().equals(g.hash))){
            Button get=button(ready?"Download update":"Download game",()->download(g));get.setEnabled(busy.isEmpty());card.addView(get);
        }
        if(!g.hash.isEmpty()){Button imported=button("Import game ZIP",()->{importing=g;Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(pick,2);}catch(Exception e){message("No file picker is available.");}});imported.setEnabled(busy.isEmpty());card.addView(imported);}
        if(busy.equals(g.id))card.addView(button("Cancel download",()->{if(task!=null)task.cancel(true);status.setText("Cancelling download…");}));
        if(ready){Button remove=button("Remove download",()->new AlertDialog.Builder(this).setTitle("Remove "+g.title+"?").setMessage("Game files will be removed. Web saves will stay on this device.").setNegativeButton("Keep",null).setPositiveButton("Remove",(d,w)->{try{store.remove(g.id);render();}catch(Exception e){message(e.getMessage());}}).show());remove.setEnabled(busy.isEmpty());card.addView(remove);}
        card.addView(button("Visit game website",()->external(g.website)));
    }
    void openNative(Game g){Intent launch=getPackageManager().getLaunchIntentForPackage(g.packageName);if(launch!=null)startActivity(launch);else external(g.website);}
    void external(String url){try{Uri uri=Uri.parse(url);if(!"https".equals(uri.getScheme()))throw new IllegalArgumentException("HTTPS required");startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){message("Could not open this link.");}}
    void message(String value){new AlertDialog.Builder(this).setTitle("anf games").setMessage(value).setPositiveButton("OK",null).show();}
    void refreshCatalog(){
        if(!busy.isEmpty())return;busy="catalog";render();status.setText("Checking for updates…");
        task=worker.submit(()->{
            try{ByteArrayOutputStream out=new ByteArrayOutputStream();Network.copy(CATALOG_URL,out,1024*1024,n->{});byte[] data=out.toByteArray();List<Game> next=parse(data);
                File temp=new File(getFilesDir(),"catalog.tmp");Files.write(temp.toPath(),data);Files.move(temp.toPath(),new File(getFilesDir(),"catalog.json").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                runOnUiThread(()->{busy="";games.clear();games.addAll(next);render();status.setText("Library is up to date.");});
            }catch(Exception e){runOnUiThread(()->{busy="";render();status.setText("Could not update the catalog. Your existing downloads still work.");});}
        });
    }
    void download(Game g){
        if(!busy.isEmpty())return;busy=g.id;render();status.setText("Downloading "+g.title+"… Keep the app open.");
        task=worker.submit(()->{
            File zip=new File(getCacheDir(),g.id+".zip.part");
            try{
                if(getFilesDir().getUsableSpace()<g.bytes+(g.expandedBytes>0?g.expandedBytes:g.bytes*2)+16*1024*1024)throw new IOException("Not enough free storage for this download");
                long[] last={0};
                try(OutputStream out=new FileOutputStream(zip)) { Network.copy(g.download,out,g.bytes,n->{long now=System.currentTimeMillis();if(now-last[0]>350){last[0]=now;runOnUiThread(()->status.setText("Downloading "+g.title+" · "+(n*100/g.bytes)+"%"));}}); }
                if(zip.length()!=g.bytes)throw new IOException("Incomplete download. Please retry.");
                runOnUiThread(()->status.setText("Verifying and installing "+g.title+"…"));store.install(g.id,zip,g.hash);
                runOnUiThread(()->{busy="";render();status.setText(g.title+" is ready to play offline.");});
            }catch(Exception e){boolean cancelled=Thread.currentThread().isInterrupted();runOnUiThread(()->{busy="";render();status.setText(cancelled?"Download cancelled.":"Download failed: "+e.getMessage());});}
            finally{zip.delete();}
        });
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req!=2||result!=RESULT_OK||data==null||importing==null)return;
        Game g=importing;importing=null;Uri uri=data.getData();if(uri==null||!busy.isEmpty())return;
        busy=g.id;render();status.setText("Importing "+g.title+"…");
        task=worker.submit(()->{
            File zip=new File(getCacheDir(),g.id+".zip.part");
            try{
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(zip)){
                    if(in==null)throw new IOException("Could not read the selected file");
                    byte[] b=new byte[65536];long total=0;int n;
                    while((n=in.read(b))!=-1){PackageStore.checkCancelled();total+=n;if(total>g.bytes)throw new IOException("This ZIP does not match the catalog");out.write(b,0,n);}
                }
                if(zip.length()!=g.bytes)throw new IOException("This ZIP does not match the catalog");
                store.install(g.id,zip,g.hash);
                runOnUiThread(()->{busy="";render();status.setText(g.title+" is ready offline.");});
            }catch(Exception e){runOnUiThread(()->{busy="";render();status.setText("Import failed: "+e.getMessage());});}
            finally{zip.delete();}
        });
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}

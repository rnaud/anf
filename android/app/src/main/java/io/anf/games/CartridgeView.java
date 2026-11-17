package io.anf.games;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.InputStream;

/** A focusable game button drawn as a cartridge, with a real text accessibility label. */
final class CartridgeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint type = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final String title, badge, number;
    private final boolean ready;
    private final int accent;
    private Bitmap art;

    CartridgeView(Context context, Game game, int index, String badge, boolean ready, Runnable action) {
        super(context);
        this.title = game.title; this.badge = badge; this.ready = ready;
        this.number = String.format(java.util.Locale.US, "%02d", index + 1);
        int[] colors = {0xffc9d895, 0xffd6b87b, 0xffdba174, 0xffb5c4bb, 0xffc9b8da};
        accent = colors[index % colors.length];
        try (InputStream stream = context.getAssets().open("art/" + game.art)) {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 2;
            art = BitmapFactory.decodeStream(stream, null, options);
        } catch (java.io.IOException ignored) { }
        setContentDescription(game.title + ", " + badge + ". Open game details.");
        setFocusable(true); setClickable(true);
        setOnClickListener(v -> action.run());
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, Math.round(width * 1.32f));
    }
    private void box(Canvas c, float x, float y, float w, float h, float radius, int color) {
        paint.setColor(color); c.drawRoundRect(x,y,x+w,y+h,radius,radius,paint);
    }
    private void label(Canvas c, String value, float x, float y, float size, int color, boolean bold) {
        paint.setColor(color); paint.setTextSize(size);
        paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(value,x,y,paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Fixed design coordinates scale uniformly to fit phone, tablet and handheld shelves.
        float scale = getWidth()/180f;
        canvas.save(); canvas.scale(scale,scale);
        if (isPressed()) canvas.translate(0,2);
        box(canvas,3,6,174,225,12,0xffd2d3c7);
        box(canvas,2,1,176,225,12,0xff273d36);
        box(canvas,5,4,170,218,10,0xff40594e);
        box(canvas,10,9,160,207,7,0xff344c42);
        box(canvas,48,1,84,9,3,0xff23392f);
        for (int i=0;i<5;i++) {
            box(canvas,12,29+i*7,7,2,1,0xff233a30);
            box(canvas,161,29+i*7,7,2,1,0xff233a30);
        }
        label(canvas,"ANF  /  GAME PAK",25,23,7.5f,0xffc2cbbb,true);
        label(canvas,number,144,23,8,0xffc2cbbb,true);
        box(canvas,23,32,134,163,5,0xfff5f2e3);
        box(canvas,23,32,134,16,4,accent);
        label(canvas,"THE LITTLE ESCAPES COLLECTION",29,42.5f,5.3f,0xff263c31,true);
        if (art != null) {
            float targetRatio=134f/82f, sourceRatio=(float)art.getWidth()/art.getHeight();
            Rect source;
            if(sourceRatio>targetRatio){int width=Math.round(art.getHeight()*targetRatio);int left=(art.getWidth()-width)/2;source=new Rect(left,0,left+width,art.getHeight());}
            else {int height=Math.round(art.getWidth()/targetRatio);int top=(art.getHeight()-height)/2;source=new Rect(0,top,art.getWidth(),top+height);}
            canvas.drawBitmap(art,source,new RectF(23,48,157,130),paint);
        }
        type.setColor(0xff243b30);type.setTextSize(17);type.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        StaticLayout heading=StaticLayout.Builder.obtain(title,0,title.length(),type,120)
                .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build();
        canvas.save();canvas.translate(30,138);heading.draw(canvas);canvas.restore();
        paint.setColor(ready?0xff447254:0xffa47a4a);canvas.drawCircle(32,182,2.5f,paint);
        label(canvas,badge,39,185,9,0xff506254,true);
        for(int i=0;i<9;i++)box(canvas,52+i*9,210,5,9,1,0xffb9a568);
        for(float x:new float[]{15,165}) {paint.setColor(0xff91a090);canvas.drawCircle(x,205,2,paint);}
        if(isFocused()) {
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(0xffb87c38);
            canvas.drawRoundRect(1.5f,1.5f,178.5f,227,12,12,paint);paint.setStyle(Paint.Style.FILL);
        }
        canvas.restore();
    }
    @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);info.setClassName(android.widget.Button.class.getName());
    }
}

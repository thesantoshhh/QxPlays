package com.qxplays.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.LruCache;
import android.view.View;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Async thumbnail loading with memory cache. Draws a clean placeholder until loaded. */
public class Thumbs {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

    public static void request(Context ctx, MediaItem item, ThumbView view) {
        if (item == null || view == null) return;
        String key = item.uri;
        view.bind(item);
        Bitmap b = CACHE.get(key);
        if (b != null) { view.setThumb(b); return; }
        view.setThumb(null);
        POOL.execute(() -> {
            Bitmap th = item.isVideo
                    ? Library.videoFrame(ctx, item.uri, 480)
                    : Library.albumArt(ctx, item.uri);
            if (th != null) CACHE.put(key, th);
            final Bitmap res = th;
            view.post(() -> {
                if (key.equals(view.getBoundUri())) view.setThumb(res);
            });
        });
    }

    public static void evict(String uri) {
        if (uri != null) CACHE.remove(uri);
    }

    public static void clear() { CACHE.evictAll(); }

    /** ImageView-like view showing either the thumbnail or a branded placeholder. */
    public static class ThumbView extends View {
        private MediaItem item;
        private String boundUri;
        private Bitmap thumb;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean rounded = true;

        public ThumbView(Context ctx) {
            super(ctx);
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        public void setRounded(boolean r) { rounded = r; }

        public String getBoundUri() { return boundUri; }

        public void bind(MediaItem it) {
            this.item = it;
            this.boundUri = it == null ? null : it.uri;
        }

        public void setThumb(Bitmap b) {
            thumb = b;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float r = rounded ? Math.min(w, h) * 0.12f : 0;
            canvas.save();
            Path clip = new Path();
            clip.addRoundRect(0, 0, w, h, r, r, Path.Direction.CW);
            canvas.clipPath(clip);

            if (thumb != null) {
                // cover-crop
                float scale = Math.max(w / thumb.getWidth(), h / thumb.getHeight());
                float dw = thumb.getWidth() * scale, dh = thumb.getHeight() * scale;
                canvas.drawBitmap(thumb, null,
                        new android.graphics.RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f),
                        paint);
            } else {
                boolean video = item != null && item.isVideo;
                paint.setShader(new android.graphics.LinearGradient(0, 0, w, h,
                        video ? C.alpha(C.accent(), 60) : C.alpha(C.accent(), 40),
                        video ? C.alpha(C.SURFACE_2, 180) : C.alpha(C.SURFACE_2, 160),
                        android.graphics.Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(null);
                drawGlyph(canvas, w, h, video);
            }
            canvas.restore();
        }

        private void drawGlyph(Canvas canvas, float w, float h, boolean video) {
            glyph.setColor(C.alpha(C.text(), 90));
            glyph.setStyle(Paint.Style.FILL);
            float cx = w / 2f, cy = h / 2f;
            if (video) {
                // film glyph
                glyph.setStrokeWidth(Math.max(2, w * 0.04f));
                glyph.setStyle(Paint.Style.STROKE);
                float fw = w * 0.44f, fh = h * 0.34f;
                canvas.drawRoundRect(cx - fw / 2, cy - fh / 2, cx + fw / 2, cy + fh / 2, w * 0.03f, w * 0.03f, glyph);
                glyph.setStyle(Paint.Style.FILL);
                glyph.setColor(C.accent());
                Path tri = new Path();
                float s = Math.min(w, h) * 0.13f;
                tri.moveTo(cx - s * 0.45f, cy - s * 0.7f);
                tri.lineTo(cx - s * 0.45f, cy + s * 0.7f);
                tri.lineTo(cx + s * 0.75f, cy);
                tri.close();
                canvas.drawPath(tri, glyph);
            } else {
                // music note glyph
                float s = Math.min(w, h) * 0.16f;
                glyph.setColor(C.alpha(C.accent(), 170));
                glyph.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx + s * 0.9f, cy + s * 0.8f, s * 0.42f, glyph);
                canvas.drawCircle(cx - s * 0.9f, cy + s * 0.8f, s * 0.42f, glyph);
                glyph.setStrokeWidth(s * 0.34f);
                glyph.setStyle(Paint.Style.STROKE);
                canvas.drawLine(cx + s * 0.9f, cy + s * 0.8f, cx + s * 0.9f, cy - s * 0.95f, glyph);
                canvas.drawLine(cx - s * 0.9f, cy + s * 0.8f, cx - s * 0.9f, cy - s * 0.95f, glyph);
                canvas.drawLine(cx + s * 0.9f, cy - s * 0.95f, cx - s * 0.9f, cy - s * 0.95f, glyph);
            }
        }
    }
}

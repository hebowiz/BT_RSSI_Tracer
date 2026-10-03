package com.example.bluetoothrssi2.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import com.jjoe64.graphview.GraphView;

/** A uniform plot frame, independent of where zero falls in the Y range. */
public final class FramedGraphView extends GraphView {
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FramedGraphView(Context context) { this(context, null); }
    public FramedGraphView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public FramedGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // GraphView's labels already sit below the plot. Do not emphasize Y=0 at its top.
        getGridLabelRenderer().setHighlightZeroLines(false);
        getViewport().setDrawBorder(false); // The built-in border does not draw all four sides.
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getGraphContentWidth(), height = getGraphContentHeight();
        if (isInEditMode() || width <= 0 || height <= 0) return;
        frame.setColor(getGridLabelRenderer().getHorizontalLabelsColor());
        float left = getGraphContentLeft(), top = getGraphContentTop();
        // Draw after grid/series so boundary values cannot obscure any side of the frame.
        canvas.drawRect(left, top, left + width, top + height, frame);
    }
}

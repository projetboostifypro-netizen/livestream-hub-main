package com.flow.iptv;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

/**
 * A VideoView that always fills its parent container completely,
 * stretching the video to fill the full width and height of the screen.
 */
public class FillVideoView extends VideoView {

    public FillVideoView(Context context) {
        super(context);
    }

    public FillVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FillVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Force the VideoView to fill the entire measured space
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
    }
}

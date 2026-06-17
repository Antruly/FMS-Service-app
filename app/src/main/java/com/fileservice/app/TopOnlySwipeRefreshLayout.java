package com.fileservice.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * SwipeRefreshLayout that only triggers pull-to-refresh when:
 * 1. The initial touch starts within the top {@link #PULL_ZONE_DP} dp of the view
 * 2. The child cannot scroll up any further (built-in SwipeRefreshLayout behavior)
 *
 * This prevents accidental refresh triggers when scrolling mid-page content.
 */
public class TopOnlySwipeRefreshLayout extends SwipeRefreshLayout {

    /** Only a pull that starts within this distance from the top can trigger refresh. */
    private static final int PULL_ZONE_DP = 200;

    private float mStartY = 0f;
    private boolean mInPullZone = false;
    private float mDensity = 1f;
    private float mPullZonePx = 600f; // fallback; recalculated in init

    public TopOnlySwipeRefreshLayout(Context context) {
        super(context);
        init(context);
    }

    public TopOnlySwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mDensity = context.getResources().getDisplayMetrics().density;
        mPullZonePx = PULL_ZONE_DP * mDensity;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mStartY = ev.getY();
                mInPullZone = mStartY <= mPullZonePx;
                break;
            case MotionEvent.ACTION_MOVE:
                // If the swipe started outside the pull zone, do NOT intercept —
                // let the child (WebView) handle the scroll normally.
                if (!mInPullZone) {
                    return false;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mInPullZone = false;
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }
}

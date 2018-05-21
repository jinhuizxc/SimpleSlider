package com.cpacm.library;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import com.cpacm.library.infinite.FixedSpeedScroller;
import com.cpacm.library.infinite.InfinitePagerAdapter;

import java.lang.reflect.Field;

/**
 * <p>
 *
 * @author cpacm 2018/5/18
 */
public class SimpleViewPager extends ViewPager {

    private final static int MIN_CYCLE_COUNT = 3;
    private final static int DEFAULT_SCROLL_DURATION = 500;

    private int count;//item count
    private boolean autoScroll = false;
    private boolean isCycling = true;
    private boolean scrollPositive = true;
    private boolean mIsDataSetChanged;

    private boolean disableDrawChildOrder = false;
    /**
     * the duration between scroll action
     */
    private long sliderDuration = 2000;
    // Flag for invalidate transformer side scroll when use setCurrentItem() method
    private boolean isInitialItem;

    private final Handler autoScrollHandler = new Handler();
    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoScroll) return;
            setCurrentItem(getRealItem() + (scrollPositive ? 1 : -1));
            autoScrollHandler.postDelayed(this, sliderDuration);
        }
    };
    private InfinitePagerAdapter infinitePagerAdapter;

    public SimpleViewPager(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public SimpleViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attributeSet) {

        //resetScroller();
    }

    private void resetScroller() {
        setSliderTransformDuration(DEFAULT_SCROLL_DURATION, new SpringInterpolator());
    }

    /**
     * speed settings for page
     *
     * @param period
     * @param interpolator
     */
    public void setSliderTransformDuration(int period, Interpolator interpolator) {
        try {
            Field mScroller = ViewPager.class.getDeclaredField("mScroller");
            mScroller.setAccessible(true);
            FixedSpeedScroller scroller = new FixedSpeedScroller(getContext(), interpolator, period);
            mScroller.set(this, scroller);
        } catch (Exception e) {

        }
    }

    public void setPageTransformer(PageTransformer transformer) {
        setPageTransformer(false, transformer);
    }

    @Override
    public void setPageTransformer(boolean reverseDrawingOrder, @Nullable PageTransformer transformer) {
        super.setPageTransformer(reverseDrawingOrder, transformer);
    }

    @Override
    public void setAdapter(@Nullable PagerAdapter adapter) {
        if (adapter != null && adapter.getCount() >= MIN_CYCLE_COUNT) {
            count = adapter.getCount();
            infinitePagerAdapter = new InfinitePagerAdapter(adapter);
            super.setAdapter(infinitePagerAdapter);
            resetPager();
        } else {
            infinitePagerAdapter = null;
            count = adapter.getCount();
            super.setAdapter(adapter);
        }
    }

    public void setSliderDuration(long sliderDuration) {
        this.sliderDuration = sliderDuration;
    }

    @Nullable
    public PagerAdapter getRealAdapter() {
        if (infinitePagerAdapter == null) return super.getAdapter();
        return infinitePagerAdapter.getRealAdapter();
    }

    @Nullable
    @Override
    public PagerAdapter getAdapter() {
        if (infinitePagerAdapter == null) return super.getAdapter();
        return infinitePagerAdapter;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE)
            if (autoScroll) {
                autoScrollHandler.removeCallbacks(autoScrollRunnable);
            }
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)
            if (autoScroll) {
                startAutoScroll(scrollPositive);
            }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onWindowFocusChanged(final boolean hasWindowFocus) {
        if (hasWindowFocus) {
            invalidateTransformer();
        }
        super.onWindowFocusChanged(hasWindowFocus);
    }


    @Override
    protected void onDetachedFromWindow() {
        stopAutoScroll();
        super.onDetachedFromWindow();
    }

    @Override
    public void setCurrentItem(final int item) {
        setCurrentItem(item, true);
    }

    @Override
    public void setCurrentItem(final int item, final boolean smoothScroll) {
        // Flag for invalidate transformer side scroll when use setCurrentItem() method
        isInitialItem = true;
        super.setCurrentItem(getVirtualCurrentItem(item), smoothScroll);
    }

    // Set current item where you put original adapter position and this method calculate nearest
    // position to scroll from center if at first initial position or nearest position of old position
    public int getVirtualCurrentItem(final int item) {
        if (getRealAdapter() == null || getRealAdapter().getCount() < MIN_CYCLE_COUNT) return item;

        final int count = getRealAdapter().getCount();
        return getCurrentItem() + Math.min(count, item) - getRealItem();
    }

    public void resetPager() {
        if (infinitePagerAdapter != null) {
            super.setCurrentItem(((infinitePagerAdapter.getCount() / 2) / count) * count);
        } else {
            setCurrentItem(0);
        }
        postInvalidateTransformer();
    }

    /**
     * 返回真实位置
     *
     * @return
     */
    public int getRealItem() {
        if (getRealAdapter() == null || getRealAdapter().getCount() < MIN_CYCLE_COUNT)
            return getCurrentItem();
        return infinitePagerAdapter.getRealPosition(getCurrentItem());
    }

    /**
     * 当数据更新时调用此方法刷新
     */
    public void notifyDataSetChanged() {
        if (infinitePagerAdapter != null) {
            infinitePagerAdapter.notifyDataSetChanged();
            mIsDataSetChanged = true;
        } else {
            if (getRealAdapter() != null) {
                getRealAdapter().notifyDataSetChanged();
            }
        }
        postInvalidateTransformer();
    }

    public void invalidateTransformer() {
        if (infinitePagerAdapter != null && count > 0) {
            if (beginFakeDrag()) {
                fakeDragBy(0f);
                endFakeDrag();
            }
        }
    }

    public void postInvalidateTransformer() {
        post(new Runnable() {
            @Override
            public void run() {
                invalidateTransformer();
            }
        });
    }

    // Start auto scroll
    public void startAutoScroll(final boolean scrollPositive) {
        autoScroll = true;
        this.scrollPositive = scrollPositive;

        autoScrollHandler.removeCallbacks(autoScrollRunnable);
        autoScrollHandler.postDelayed(autoScrollRunnable,sliderDuration);
    }

    // Stop auto scroll
    public void stopAutoScroll() {
        if (!autoScroll) return;
        autoScroll = false;
        autoScrollHandler.removeCallbacks(autoScrollRunnable);
    }

    @Override
    public void setOverScrollMode(final int overScrollMode) {
        super.setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public int getCount() {
        return count;
    }

    @Override
    protected void setChildrenDrawingOrderEnabled(final boolean enabled) {
        if (disableDrawChildOrder) {
            super.setChildrenDrawingOrderEnabled(false);
            return;
        }
        super.setChildrenDrawingOrderEnabled(enabled);

    }

    public void setDisableDrawChildOrder(boolean disableDrawChildOrder) {
        this.disableDrawChildOrder = disableDrawChildOrder;
    }

    public boolean ismIsDataSetChanged() {
        return mIsDataSetChanged;
    }

    public boolean isInitialItem() {
        return isInitialItem;
    }

    public void setInitialItem(boolean initialItem) {
        this.isInitialItem = initialItem;
    }

    // Default spring interpolator
    private final class SpringInterpolator implements Interpolator {

        private final static float FACTOR = 0.5F;

        @Override
        public float getInterpolation(final float input) {
            return (float) (Math.pow(2.0F, -10.0F * input) *
                    Math.sin((input - FACTOR / 4.0F) * (2.0F * Math.PI) / FACTOR) + 1.0F);
        }
    }
}
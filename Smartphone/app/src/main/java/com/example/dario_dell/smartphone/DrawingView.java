package com.example.dario_dell.smartphone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import java.util.ArrayList;

/*Custom View class in which the drawing takes place*/
public class DrawingView extends View {

    ArrayList<ViewWasTouchedListener> listeners = new ArrayList<ViewWasTouchedListener>();

    //drawing path
    private Path drawPath;
    //drawing and canvas paint
    private Paint drawPaint, canvasPaint;

    //default color
    private int defaultPaintColor = Color.BLACK;
    //canvas
    private Canvas drawCanvas;
    //canvas bitmap
    private Bitmap canvasBitmap;


    public DrawingView(Context context, AttributeSet attrs){
        super(context, attrs);
        setupDrawing();
    }

    //method for instantiating some of these variables now to set the class up for drawing
    private void setupDrawing(){
        /*variables to set the class up for drawing*/
        drawPath = new Path();
        drawPaint = new Paint();
        // set the initial color
        drawPaint.setColor(defaultPaintColor);
        //set the initial path properties
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(20);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        //instantiate the canvas Paint object
        canvasPaint = new Paint(Paint.DITHER_FLAG);
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        //instantiate the drawing canvas and bitmap using the width and height values
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        drawCanvas = new Canvas(canvasBitmap);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
        canvas.drawPath(drawPath, drawPaint);
    }


    // Update the ArrayList of ViewWasTouchedListener
    public void setWasTouchedListener(ViewWasTouchedListener listener){
        listeners.add(listener);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        float touchX = event.getX();
        float touchY = event.getY();

        for (ViewWasTouchedListener listener:listeners){
            listener.onViewTouched(touchX, touchY, event);
        }

        switch (action){
            case MotionEvent.ACTION_DOWN:
                startNew();
                drawPath.moveTo(touchX, touchY);
            break;

            case MotionEvent.ACTION_MOVE:
                drawPath.lineTo(touchX, touchY);
            break;

            case MotionEvent.ACTION_UP:
                drawCanvas.drawPath(drawPath, drawPaint);
                drawPath.reset();
            break;

            default:
                return false;
        }

        // Calling invalidate will cause the onDraw method to execute
        invalidate();
        return true;
    }


    // Clears the canvas and updates the display
    public void startNew(){
        drawCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        invalidate();
    }


}

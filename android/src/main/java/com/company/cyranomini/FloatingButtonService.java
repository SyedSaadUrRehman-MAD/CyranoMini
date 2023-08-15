package com.company.cyranomini;

import static android.app.Activity.RESULT_CANCELED;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.RotateDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.company.cyranomini.model.Action;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

public class FloatingButtonService extends Service {
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler;
    private WindowManager windowManager;
    private View floatingView;
    private ImageButton floatingBtn;
    private ImageButton cancelBtn;

    private List<Action> moreActions = Arrays.asList(new Action("notes", R.drawable.notes, null, null, Action.ActionType.OPTION),
            new Action("translate", R.drawable.translate, null, null, Action.ActionType.OPTION),
            new Action("context", R.drawable.context, null, null, Action.ActionType.OPTION),
            new Action("exit", R.drawable.exit, null, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_LAUNCHER);
                        String packageName = getPackageName();
                        intent.setPackage(packageName);
                        List<ResolveInfo> activities = getPackageManager().queryIntentActivities(intent, 0);
                        if (activities != null && !activities.isEmpty()) {
                            String name =  activities.get(0).activityInfo.name;
                            Log.d("cyrano","activity name retrieved "+name);
                            try {
                                Class<?> activityClass = Class.forName(name);
                                Intent activityintent = new Intent(FloatingButtonService.this, activityClass);
                                activityintent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(activityintent);
                                stopSelf();
                            } catch (ClassNotFoundException e) {
                                // Handle the error here - the class was not found
                                e.printStackTrace();
                            }
                        }

                    return null;
                }
            }, Action.ActionType.OPTION));
    private List<Action> actions = Arrays.asList(new Action("more", R.drawable.more, moreActions, null, Action.ActionType.OPTION),
            new Action("dash", R.drawable.dash, null, null, Action.ActionType.OPTION),
            new Action("info", R.drawable.info, null, null, Action.ActionType.OPTION),
            new Action("status", R.drawable.status, null, null, Action.ActionType.OPTION),
            new Action("suggest", R.drawable.context, null, null, Action.ActionType.OPTION));
    private boolean showingOptions = false;
    RotateAnimation rotate = new RotateAnimation(0, 45, // Starting angle and ending angle
            Animation.RELATIVE_TO_SELF, 0.5f, // Pivot X (center of the view)
            Animation.RELATIVE_TO_SELF, 0.5f); // Pivot Y (center of the view)
    private View transparentView;
    private Runnable captureRunnable = new Runnable() {
        @Override
        public void run() {
            captureScreen();
            handler.postDelayed(this, 30000); // Repeat every 30 seconds
        }
    };
    private boolean stopService = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        // Inflate the floating view layout
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_button, null);
        View cancelView = LayoutInflater.from(this).inflate(R.layout.cancel_overlay, null);

        floatingBtn = floatingView.findViewById(R.id.floatingButton);
        cancelBtn = cancelView.findViewById(R.id.cancelButton);
        final RotateDrawable background = (RotateDrawable) floatingBtn.getBackground();

        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            int level = 0;

            @Override
            public void run() {
                level += 100; // Change this value to control the speed of the rotation
                if (level > 10000) {
                    level = 0; // Reset when reaching the full rotation
                }

                background.setLevel(level);
                handler.postDelayed(this, 50); // Schedule the next update
            }
        };

        handler.post(runnable); // Start the updates

        rotate.setDuration(500); // Duration of the animation in milliseconds
        rotate.setFillAfter(true); // Retain the final rotation state when the animation is done

        // Add the view to the window
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // Set initial position
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        windowManager.addView(floatingView, params);
        measureView(floatingView);

        Log.d("cyrano", "window width =" + windowManager.getDefaultDisplay().getWidth());
        Log.d("cyrano", "window height =" + windowManager.getDefaultDisplay().getHeight());
        Log.d("cyrano", "overlay button width =" + floatingView.getMeasuredWidth());
        Log.d("cyrano", "overlay button height =" + floatingView.getMeasuredHeight());

        setHUDStartingPosition(params);
        setOverlayDraggable(params);
        floatingView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!showingOptions) {
                    showingOptions = true;
                    calculateArcPositions();
                    animateActionButtons();
                    // Start the animation
                    floatingBtn.startAnimation(rotate);

                    floatingBtn.setImageResource(R.drawable.close);
                } else {
                    showingOptions = false;
                    animateActionButtons();
                    removeAdditionalButtons();
                    // Start the animation
                    floatingBtn.startAnimation(rotate);

                    floatingBtn.setImageResource(R.drawable.status_add);

                }
            }
        });
    }

    private void setOverlayDraggable(WindowManager.LayoutParams params) {
        // Set the touch listener
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        showCancelView();
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return false;
                    case MotionEvent.ACTION_UP:
                        if (stopService) {
                            stopSelf();
                        } else {
                            setHUDStartingPosition(params);
                            hideCancelView();
                        }
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        // Calculate the X and Y coordinates of the view.
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        // Update the layout with new X & Y coordinate
                        windowManager.updateViewLayout(floatingView, params);

                        stopService = isViewInside(floatingView, cancelBtn, event);

                        return false;
                }
                return false;
            }
        });
    }

    private void hideCancelView() {
        try {
            windowManager.removeView(cancelBtn);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isViewInside(View viewToCheck, View targetView, MotionEvent event) {
        Rect rect = new Rect();
        targetView.getHitRect(rect);

        int[] viewToCheckCoordinates = new int[2];
        viewToCheck.getLocationOnScreen(viewToCheckCoordinates);

        int[] targetViewCoordinates = new int[2];
        targetView.getLocationOnScreen(targetViewCoordinates);

        int x = (int) (event.getRawX() - targetViewCoordinates[0]);
        int y = (int) (event.getRawY() - targetViewCoordinates[1]);

        return rect.contains(x, y);
    }

    private void setHUDStartingPosition(WindowManager.LayoutParams params) {
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = windowManager.getDefaultDisplay().getWidth() - floatingView.getMeasuredWidth() - 10;
        params.y = windowManager.getDefaultDisplay().getHeight() / 2 - floatingView.getMeasuredHeight() / 2;

        windowManager.updateViewLayout(floatingView, params);
//        floatingView.animate()
//                .translationXBy(0)
//                .translationYBy(0)
//                .translationY(params.x)
//                .translationX(params.y)
//                .setDuration(1000)
//                .start();
    }

    private void showCancelView() {
        // Add the view to the window
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                150,
                150,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);

        // Set initial position
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        windowManager.addView(cancelBtn, params);
        measureView(cancelBtn);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = windowManager.getDefaultDisplay().getWidth() / 2 - 75;
        params.y = windowManager.getDefaultDisplay().getHeight() - cancelBtn.getMeasuredHeight() / 2 - 200;

        windowManager.updateViewLayout(cancelBtn, params);
//        cancelBtn.animate()
//                .translationXBy(windowManager.getDefaultDisplay().getWidth() /2)
//                .translationYBy(windowManager.getDefaultDisplay().getHeight())
//                .translationY(params.x)
//                .translationX(params.y)
//                .setDuration(1000)
//                .start();
    }

    private void addTransparentViewToDetectTouchesOnWindow() {
        transparentView = new View(this);
        transparentView.setBackgroundResource(android.R.color.black);
        transparentView.setAlpha(0.2f);
        WindowManager.LayoutParams transparentParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_PHONE :
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSPARENT);
        windowManager.addView(transparentView, transparentParams);

        transparentView.setOnTouchListener((view, motionEvent) -> {
            if (showingOptions) {
                showingOptions = false;
                animateActionButtons();
                removeAdditionalButtons();
                // Start the animation
                floatingBtn.startAnimation(rotate);

                floatingBtn.setImageResource(R.drawable.status_add);
            }
            return false;
        });

    }

    private void removeAdditionalButtons() {
        showingOptions = false;
        int additionalButtonCount = actions.size();
        for (int i = 0; i < additionalButtonCount; i++) {
            View additionalButton = actions.get(i).getView(this);
            if(actions.get(i).isDisplayedOptions())
            {
                if(actions.get(i).getContainer() != null) {
                    actions.get(i).getContainer().removeAllViews();
                    windowManager.removeView(actions.get(i).getContainer());
                }
            }
            actions.get(i).setContainer(null);
            windowManager.removeView(additionalButton);
        }

        if(transparentView != null)
            windowManager.removeView(transparentView);
    }

    private void animateActionButtons() {
        for (int i = 0; i < actions.size(); i++) {
            View child = actions.get(i).getView(this);
            child.animate()
                    .scaleX(showingOptions ? 1.0f : 0.0f)
                    .scaleY(showingOptions ? 1.0f : 0.0f)
                    .setDuration(300)
                    .start();
        }
    }

    private void measureView(View view) {
        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(widthMeasureSpec, heightMeasureSpec);
    }

    private void calculateArcPositions() {

        addTransparentViewToDetectTouchesOnWindow();
//        // Get the main button's position and size
        //Working with right arc but left arc is abnormal
        // Radius of the arc
        int radius = 140; // You can adjust this to your desired value

        // Determine the starting angle and angle increment based on the main button's position
        int startAngle, angleIncrement;
        WindowManager.LayoutParams mainButtonParam = (WindowManager.LayoutParams) floatingView.getLayoutParams();
        if (mainButtonParam.x < windowManager.getDefaultDisplay().getWidth() / 2) {
            // Main button is on the left side
            startAngle = -90;
            angleIncrement = 45; // Increment angle for a clockwise arc
        } else {
            // Main button is on the right side
            startAngle = -90; // Start angle same as the left side
            angleIncrement = -45; // Decrement angle for a counterclockwise arc
        }

        Log.d("cyranomini", "mainButtonParam.x = " + mainButtonParam.x);
        Log.d("cyranomini", "mainButtonParam.y = " + mainButtonParam.y);
        Log.d("cyranomini", "startAngle = " + startAngle);
        Log.d("cyranomini", "angleIncrement = " + angleIncrement);
        // Get the main button's center position
        int mainButtonX = (int) (mainButtonParam.x + floatingView.getWidth() / 2f);
        int mainButtonY = (int) (mainButtonParam.y + floatingView.getHeight() / 2f);
        Log.d("cyranomini", "mainButtonX = " + mainButtonX);
        Log.d("cyranomini", "mainButtonY = " + mainButtonY);

        // Iterate over the additional buttons and position them
        for (int i = 0; i < actions.size(); i++) {
            // Calculate the angle for this option
            int angle = startAngle + angleIncrement * i;

            // Calculate the X and Y position for this option
            int x = (int) (mainButtonX + radius * Math.cos(Math.toRadians(angle)));
            int y = (int) (mainButtonY + radius * Math.sin(Math.toRadians(angle)));


            Log.d("cyranomini", "x for " + i + "th view = " + x);
            Log.d("cyranomini", "y for " + i + "th view = " + x);

//            // Apply the coordinates to the layout parameters
            View additionalButton = actions.get(i).getView(this);
            measureView(additionalButton);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = x - additionalButton.getMeasuredWidth() / 2;
            params.y = y - additionalButton.getMeasuredHeight() / 2;

            Log.d("cyranomini", "params.x for " + i + 1 + "th view = " + params.x);
            Log.d("cyranomini", "params.y for " + i + 1 + "th view = " + params.y);
            windowManager.addView(additionalButton, params);

            if (actions.get(i).getOptions() != null)
                setListenersOnViewWithAction(actions.get(i), additionalButton);

        }
        showingOptions = true;
    }

    private void setListenersOnViewWithAction(Action action, View parentView) {
        action.getView(this).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(action.isDisplayedOptions() && action.getContainer() != null)
                {
                    action.getContainer().removeAllViews();
                    windowManager.removeView(action.getContainer());
                    action.setDisplayedOptions(false);
                    action.setContainer(null);
                }else {
                    LinearLayout layout = new LinearLayout(FloatingButtonService.this);
                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                            PixelFormat.TRANSLUCENT);
                    params.gravity = Gravity.TOP | Gravity.START;
                    for (Action option : action.getOptions()) {
                        View v = option.getView(FloatingButtonService.this);
                        LinearLayout.LayoutParams llparam = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        layout.addView(v, llparam);
                        measureView(v);
                        if(option.getCallable() != null)
                        {
                            v.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    try {
                                        option.getCallable().call();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }
                    }
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setGravity(Gravity.CENTER);
                    windowManager.addView(layout, params);
                    measureView(layout);
                    params.x = ((WindowManager.LayoutParams) parentView.getLayoutParams()).x;
                    Log.d("cyrano","LL height = "+ layout.getMeasuredHeight());
                    params.y = ((WindowManager.LayoutParams) parentView.getLayoutParams()).y - layout.getMeasuredHeight();
                    windowManager.updateViewLayout(layout,params);
                    action.setContainer(layout);
                    action.setDisplayedOptions(true);
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1234, createNotification());
        int resultCode = intent.getIntExtra("resultCode", RESULT_CANCELED);
        Intent data = intent.getParcelableExtra("data");

        Log.d("cyrano", "in service result code is " + resultCode);
        Log.d("cyrano", "in service mp data is " + data.toString());

        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);

        // Start capturing the screenshots
        startCapturing();

        return START_NOT_STICKY;
    }

    private void startCapturing() {
        // Create an ImageReader to capture the screen content
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        imageReader = ImageReader.newInstance(width, height, ImageFormat.RGB_565, 5);

        // Create a VirtualDisplay with the ImageReader's surface
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        captureScreen();
        // Set up a handler to periodically capture the screen content
        handler = new Handler();
        handler.postDelayed(captureRunnable, 30000);
    }

    private void captureScreen() {
        try {
            Image image = imageReader.acquireLatestImage();
            if (image != null) {
                final Image.Plane[] planes = image.getPlanes();
                final ByteBuffer buffer = planes[0].getBuffer();
                int offset = 0;
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * image.getWidth();
                // create bitmap
                Bitmap bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, image.getHeight(), Bitmap.Config.RGB_565);

                bitmap.copyPixelsFromBuffer(buffer);
                // Save the bitmap to a file
                saveBitmapToExternalStorage(bitmap);

                image.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveBitmapToExternalStorage(Bitmap bitmap) {
        String fileName = "screenshot_" + System.currentTimeMillis() + ".png";
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File file = new File(path, fileName);

        try {
            path.mkdirs();
            FileOutputStream outputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            outputStream.close();
            Toast.makeText(this.getBaseContext(), "Screenshot saved to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this.getBaseContext(), "Failed to save screenshot", Toast.LENGTH_LONG).show();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "cyrano-channel"; // Provide your channel name
            String description = "this is cyrano description"; // Provide your channel description
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("Cyrano_id", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        createNotificationChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "Cyrano_id")
                .setSmallIcon(R.drawable.status_add) // Provide your icon
                .setContentTitle("screenshot service") // Provide your notification title
                .setContentText("takes screenshot every 30sec") // Provide your notification text
                ;

        return builder.build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideCancelView();
        try {
            if (floatingView != null) {
                windowManager.removeView(floatingView);
            }
            if(transparentView != null) {
                windowManager.removeView(transparentView);
            }
            removeAdditionalButtons();
            if (mediaProjection != null) {
                mediaProjection.stop();
            }
            handler.removeCallbacksAndMessages(captureRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
